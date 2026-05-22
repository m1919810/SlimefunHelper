package me.matl114.hacks.modules.render;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import me.matl114.accessors.access.ExplosiveProjectileAccess;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.*;
import me.matl114.utils.containers.MetaData;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector2d;

public class ProjectileESP extends BaseModule {
    public final ModulePath detectEntity = makePath(Configs.RENDER_CONFIG, "detect-entity");
    public final ModulePath calculateTrace = detectEntity.add("calculate-trace");

    public ProjectileESP() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(calculateTrace).build();

    public final FlagRef calculateFireball =
            flagBuilder(detectEntity.add("cal-fireball")).build();

    public final FlagRef calculateArrow =
            flagBuilder(detectEntity.add("cal-projectile")).build();

    public final FlagRef renderFireball =
            flagBuilder(detectEntity.add("render-fireball")).build();

    public final FlagRef renderArrow =
            flagBuilder(detectEntity.add("render-projectile")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getEntityClientVelocityUpdate(), this::onVelocityFireball);
        registerListener(Listener.getEntityClientVelocityUpdate(), this::onVelocityArrow);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(Listener.getServerEntitySpawnListener(), this::onEntitySpawn);
    }

    private static final String flagCalculateProjectile = "slimefunhelper:calculate_projectile";

    public void onEntitySpawn(Event<Entity> event) {
        if (enable.get()
                && calculateFireball.get()
                && event.context() instanceof ExplosiveProjectileEntity projectile) {
            onVelocityFireballCal(projectile, projectile.getVelocity());
        }
    }

    public void onVelocityFireball(Event<Vec3d> fireballEvent) {
        if (enable.get() && calculateFireball.get()) {
            Entity entity = fireballEvent.getArgs(0);
            if (entity instanceof ExplosiveProjectileEntity fireball) {
                Vec3d vec = fireballEvent.context();
                onVelocityFireballCal(fireball, vec);
            }
        }
    }

    public void onVelocityFireballCal(ExplosiveProjectileEntity fireball, Vec3d vec) {
        if (vec.lengthSquared() > 1e-10) {
            EntityAccess<ExplosiveProjectileEntity> access = EntityAccess.of(fireball);
            if (access.getMetadata().get(this, flagCalculateProjectile) == null) {
                access.getMetadata().put(this, flagCalculateProjectile, Boolean.TRUE);
                calLineTrace(fireball.getPos(), vec, fireball.getType());
            }
        }
    }

    public void onVelocityArrow(Event<Vec3d> arrowEvent) {
        if (enable.get() && calculateArrow.get()) {
            Entity entity = arrowEvent.getArgs(0);
            if (entity instanceof TridentEntity trident) {

            } else if (entity instanceof PersistentProjectileEntity arrow) {
                Vec3d vec = arrowEvent.context();
                if (vec.lengthSquared() > 1e-10) {
                    EntityAccess<PersistentProjectileEntity> access = EntityAccess.of(arrow);
                    MetaData metaData = access.getMetadata();
                    Integer integer = metaData.get(this, flagCalculateProjectile);
                    if (integer != null) {
                        if (integer >= 3) {
                            arrow.setVelocity(vec);
                            calArrowTrace(arrow);
                        }
                        metaData.put(this, flagCalculateProjectile, integer + 1);
                    } else {
                        metaData.put(this, flagCalculateProjectile, 1);
                    }
                }
            }
        }
    }

    public static void calLineTrace(Vec3d fireballPosition, Vec3d power, EntityType<?> type) {
        // (x - x0)/px = (y - y0)/py = (z - z0)/pz
        if (mc.player != null) {
            // 给行进方向norm
            power = power.normalize();
            var playerPos = mc.player.getEyePos();
            var deltaTo = playerPos.subtract(fireballPosition);
            // 求出玩家位置在行进方向上的投影长度
            var projLen = deltaTo.dotProduct(power);
            if (projLen > 0) {
                // 勾股定理求出最短距离
                var projPoint = fireballPosition.add(power.multiply(projLen));
                var lookAtProjPoint = projPoint.subtract(playerPos);
                var minDist = lookAtProjPoint.length();
                Vector2d planeVec = new Vector2d(lookAtProjPoint.x, lookAtProjPoint.z);
                // cal direction
                Vector2d playerLookat = EntityUtils.getEntityLookXZ(mc.player);
                boolean front = planeVec.dot(playerLookat) > 0;
                Debug.chat(
                        type.getName(),
                        "trace:",
                        Text.literal("%.2f".formatted(minDist)).formatted(Formatting.RED),
                        (front ? Text.literal("in front of") : Text.literal("at back of")).formatted(Formatting.GREEN),
                        "you");
            } else {
                Debug.chat("Fireball trace update: not towards you");
            }
        } else {
            // Debug.info("null player");
        }
    }

    public static void calArrowTrace(PersistentProjectileEntity arrow) {
        if (mc.player != null) {
            if (arrow.getOwner() == mc.player) return;
            if (mc.player.getPos().squaredDistanceTo(arrow.getPos()) < 0.1) {
                // might be shot by player using something
                return;
            }
            // 给行进方向norm
            Vec3d vec3d = arrow.getVelocity();
            Vec3d vecDirection = vec3d.normalize();
            var playerPos = mc.player.getEyePos();
            var deltaTo = playerPos.subtract(arrow.getPos());
            // 求出玩家位置在行进方向上的投影长度
            var projLen = deltaTo.dotProduct(vecDirection);
            if (projLen > 0) {
                // 勾股定理求出最短距离
                List<Vec3d> preciseLine = ArrowPredictor.of(arrow, 0.0F).predictLine(400);
                Vec3d proj = null;
                double lenSquared = 144000000;
                for (var vec : preciseLine) {
                    double len = vec.squaredDistanceTo(playerPos);
                    if (len < lenSquared) {
                        proj = vec;
                        lenSquared = len;
                    }
                }
                if (proj == null) return;
                Vector2d planeVec = new Vector2d(proj.x, proj.z);
                double minDist = Math.sqrt(lenSquared);
                // cal direction
                Vector2d playerLookat = EntityUtils.getEntityLookXZ(mc.player);
                boolean front = planeVec.dot(playerLookat) > 0;
                Debug.chat(
                        "Arrow trace update:",
                        Text.literal("%.2f".formatted(minDist)).formatted(Formatting.RED),
                        (front ? Text.literal("in front of") : Text.literal("at back of")).formatted(Formatting.GREEN),
                        "you");
            } else {
                Debug.chat("Arrow trace update: not towards you");
            }
        } else {
            // Debug.info("null player");
        }
    }

    public void onRender(Event<MatrixStack> stackE) {
        if (mc.world == null || mc.player == null) return;
        // no render arrow
        //        var whitelist = getWhitelisted();
        // remove whitelist whitelist

        // if(!arrowItem)return;
        if (enable.get()) {
            var stack = stackE.context;
            boolean arrowFlag = renderArrow.get();
            boolean fireballFlag = renderFireball.get();
            float tickDelta = (Float) stackE.getArgs(0);
            RenderUtils.startDrawVirtual(stack);
            for (var fireball : mc.world.getEntities()) {
                if (fireball instanceof ExplosiveProjectileEntity explosive) {
                    if (fireballFlag) {
                        RenderUtils.drawStripLineVirtual(stack, predictFireballTrace(explosive), Color.RED);
                    }
                }
                if (arrowFlag
                        && fireball instanceof AbstractSkeletonEntity arrow
                        && !(arrow instanceof WitherSkeletonEntity)) {
                    renderSkeletonProjectile(stack, arrow, tickDelta);
                } else if (arrowFlag && fireball instanceof PlayerEntity player) {
                    renderPlayerProjectile(stack, player, tickDelta);
                } else if (arrowFlag && fireball instanceof CrossbowUser user) {
                    renderCrossbowProjectile(stack, user, tickDelta);
                } else if (arrowFlag && fireball instanceof PersistentProjectileEntity arrow) {
                    renderArrowProjectile(stack, arrow, tickDelta);
                }
            }
            RenderUtils.stopDrawVirtual(stack);
        }
    }

    private static class ArrowPredictor {
        Vec3d pos;
        Vec3d vec;
        Type type;
        Entity owner;
        private static final Random random = net.minecraft.util.math.random.Random.create();
        private static Vec3d lastRand;
        private static int lastRandTime = 0;

        private static Vec3d getArrowRand() {
            if (true) return Vec3d.ZERO;
            if (lastRandTime + 20 < Tasks.getTick()) {
                lastRandTime = Tasks.getTick();
                float uncertainty = 1.0f;
                lastRand = new Vec3d(
                        random.nextTriangular(0.0, 0.0172275 * (double) uncertainty),
                        random.nextTriangular(0.0, 0.0172275 * (double) uncertainty),
                        random.nextTriangular(0.0, 0.0172275 * (double) uncertainty));
            }
            return lastRand;
        }

        private static Vec3d calculateVelocity(double x, double y, double z, float power) {
            return (new Vec3d(x, y, z)).normalize().add(getArrowRand()).multiply((double) power);
        }

        public static ArrowPredictor of(AbstractSkeletonEntity entity, float tickDelta) {
            Vec3d originPos = new Vec3d(entity.getX(), entity.getEyeY() - 0.10000000149011612, entity.getZ())
                    .add(RenderUtils.getLerpedDelta(entity, tickDelta));
            Vec3d facing = entity.getRotationVector();
            double d = facing.getX();
            double f = facing.getZ();
            double g = Math.sqrt(d * d + f * f);

            //            Debug.info(entity.getTarget());
            Vec3d vec3d = calculateVelocity(d, facing.y + g * 0.2, f, 1.6F);
            return new ArrowPredictor(originPos, vec3d, Type.SKELETON, entity);
        }

        private static float getPullProgress(int useTicks) {
            float f = (float) useTicks / 20.0F;
            f = (f * f + f * 2.0F) / 3.0F;
            if (f > 1.0F) {
                f = 1.0F;
            }

            return f;
        }

        private static Vec3d getHandOffset(PlayerEntity player, Hand hand) {
            double yaw = Math.toRadians(player.getYaw());
            Arm mainArm = mc.options.getMainArm().getValue();

            boolean rightSide =
                    mainArm == Arm.RIGHT && hand == Hand.MAIN_HAND || mainArm == Arm.LEFT && hand == Hand.OFF_HAND;

            double sideMultiplier = rightSide ? -1 : 1;
            double handOffsetX = Math.cos(yaw) * 0.16 * sideMultiplier;
            double handOffsetZ = Math.sin(yaw) * 0.16 * sideMultiplier;

            return new Vec3d(handOffsetX, 0, handOffsetZ);
        }

        public static ArrowPredictor of(PlayerEntity player, RangedWeaponItem weaponItem, Hand hand, float tickDelta) {
            Vec3d vec3d;
            final Vec3d offset = getHandOffset(player, hand);
            Vec3d pos = new Vec3d(player.getX(), player.getEyeY() - 0.10000000149011612, player.getZ())
                    .add(offset)
                    .add(RenderUtils.getLerpedDelta(player, tickDelta));
            if (weaponItem instanceof BowItem) {
                int usingTicks =
                        (player.isUsingItem() && player.getActiveHand() == hand) ? player.getItemUseTime() : 1000;
                float progress = getPullProgress(usingTicks);
                float speed = progress * 3.0f;
                Vec3d facing = player.getRotationVector();
                vec3d = calculateVelocity(facing.x, facing.y, facing.z, speed);
                // add player velocity here
                Vec3d infect0 = player.getVelocity();
                vec3d = vec3d.add(infect0.x, player.isOnGround() ? 0.0D : infect0.y, infect0.z);
            } else {
                Vec3d facing = player.getRotationVec(1.0f);
                float speed = 3.15F;
                vec3d = calculateVelocity(facing.x, facing.y, facing.z, speed);
            }
            return new ArrowPredictor(pos, vec3d, Type.PLAYER, player) {
                @Override
                public List<Vec3d> predictLine(int ticks) {
                    List<Vec3d> list = super.predictLine(ticks);
                    int size = list.size();
                    if (size == 0) return list;
                    List<Vec3d> list3d = new ArrayList<>();
                    for (int i = 0; i < size; ++i) {
                        list3d.add(list.get(i).subtract(offset.multiply(((i + 1) / (double) size))));
                    }
                    return list3d;
                }
            };
        }

        public static ArrowPredictor of(PersistentProjectileEntity arrow, float tickDelta) {
            return new ArrowPredictor(
                    RenderUtils.getLerpedPos(arrow, tickDelta), arrow.getVelocity(), Type.ARROW, arrow);
        }

        public static ArrowPredictor of(CrossbowUser user, float tickDelta) {
            Entity player = (Entity) user;
            Vec3d facing = (player).getRotationVec(1.0f);
            float speed = 1.6F;

            Vec3d vec3d = calculateVelocity(facing.x, facing.y, facing.z, speed);
            Vec3d pos = new Vec3d(player.getX(), player.getEyeY() - 0.10000000149011612, player.getZ())
                    .add(RenderUtils.getLerpedDelta((Entity) user, tickDelta));
            return new ArrowPredictor(pos, vec3d, Type.CROSSBOW, player);
        }

        public ArrowPredictor(Vec3d pos, Vec3d vec, Type type, Entity owner) {
            this.pos = pos;
            this.vec = vec;
            this.type = type;
            this.owner = owner;
        }

        public static enum Type {
            SKELETON,
            PLAYER,
            ARROW,
            CROSSBOW;
        }

        public List<Vec3d> predictLine(int ticks) {
            Vec3d arrowPos = pos;
            Vec3d arrowMotion = vec;
            double gravity = EntityUtils.getProjectileGravity(Items.BOW);
            List<Vec3d> path = new ArrayList<>();
            Vec3d lastPos;
            if (this.vec.lengthSquared() < 1e-5) {
                return List.of();
            }
            for (int i = 0; i < ticks; i++) {
                // add to path
                path.add(arrowPos);
                // apply motion
                arrowPos = arrowPos.add(arrowMotion.multiply(0.1));

                // apply air friction
                arrowMotion = arrowMotion.multiply(0.999);

                // apply gravity
                arrowMotion = arrowMotion.add(0, -gravity * 0.1, 0);

                if (path.size() > 2) {
                    lastPos = path.get(path.size() - 2);
                    if (RaycastUtils.raycastAnySolidBlock(owner, lastPos, arrowPos)
                            || RaycastUtils.raycastHitAnyEntityExceptPlayer(owner, lastPos, arrowPos)) {
                        break;
                    }
                }
            }
            return path;
        }

        public Pair<List<Vec3d>, HitResult> predictLineWithHitResult(int ticks) {
            Vec3d arrowPos = pos;
            Vec3d arrowMotion = vec;
            double gravity = EntityUtils.getProjectileGravity(Items.BOW);
            List<Vec3d> path = new ArrayList<>();
            Vec3d lastPos;
            if (this.vec.lengthSquared() < 1e-5) {
                return Pair.of(List.of(), null);
            }
            HitResult result = null;
            for (int i = 0; i < ticks; i++) {
                // add to path
                path.add(arrowPos);
                // apply motion
                arrowPos = arrowPos.add(arrowMotion.multiply(0.1));

                // apply air friction
                arrowMotion = arrowMotion.multiply(0.999);

                // apply gravity
                arrowMotion = arrowMotion.add(0, -gravity * 0.1, 0);

                if (path.size() > 2) {
                    lastPos = path.get(path.size() - 2);
                    result = RaycastUtils.raycastSolidBlockResult(owner, lastPos, arrowPos);
                    if (result != null && result.getType() != HitResult.Type.MISS) {
                        break;
                    }
                    result = RaycastUtils.raycastHitEntityExceptPlayerResult(owner, lastPos, arrowPos);
                    if (result != null && result.getType() != HitResult.Type.MISS) {
                        break;
                    }
                    result = null;
                }
            }
            return Pair.of(path, result);
        }
    }

    private static void renderSkeletonProjectile(MatrixStack stack, AbstractSkeletonEntity entity, float tickDelta) {
        if (entity.isUsingItem() && entity.getActiveItem().getItem() instanceof BowItem) {
            drawClassicArrowTrajectory(
                    stack, ArrowPredictor.of(entity, tickDelta).predictLine(400), Color.YELLOW);
        }
    }

    private static void renderCrossbowProjectile(MatrixStack stack, CrossbowUser pillagerEntity, float tickDelta) {
        if (pillagerEntity instanceof LivingEntity entity
                && entity.isUsingItem()
                && entity.getActiveItem().getItem() instanceof RangedWeaponItem crossbow) {
            drawClassicArrowTrajectory(
                    stack, ArrowPredictor.of(pillagerEntity, tickDelta).predictLine(400), Color.YELLOW);
            return;
        }
    }

    private static void renderPlayerProjectile(MatrixStack stack, PlayerEntity player, float tickDelta) {
        for (var hand : Hand.values()) {
            if (player.getStackInHand(hand).getItem() instanceof RangedWeaponItem item) {
                var data = ArrowPredictor.of(player, item, hand, tickDelta).predictLineWithHitResult(400);
                drawArrowTrajectoryWithHitResult(stack, data.getFirst(), data.getSecond(), tickDelta);
                // drawClassicArrowTrajectory(stack, ArrowPredictor.of(player, item, hand).predictLine(400));
                return;
            }
        }
    }

    private static void renderArrowProjectile(MatrixStack stack, PersistentProjectileEntity arrow, float tickDelta) {
        // filter on ground arrows
        if (!arrow.isOnGround() && arrow.getVelocity().lengthSquared() > 1e-5) {
            // fix? velocity does not change
            drawClassicArrowTrajectory(
                    stack, ArrowPredictor.of(arrow, tickDelta).predictLine(400), Color.RED);
        }
    }

    private static void drawClassicArrowTrajectory(MatrixStack stack, List<Vec3d> vec3ds, Color clr) {
        // escape little traj
        if (vec3ds.size() <= 3) return;
        RenderUtils.drawStripLineVirtual(stack, vec3ds, clr);
        if (!vec3ds.isEmpty()) {
            Vec3d finalPosition = vec3ds.get(vec3ds.size() - 1);
            RenderUtils.drawSolidBox(
                    stack,
                    finalPosition.add(RenderTasks.SMALL_FROM),
                    finalPosition.add(RenderTasks.SMALL_TO),
                    ColorUtils.withAlpha(Color.GREEN, 0.25F));
        }
    }

    private static void drawArrowTrajectoryWithHitResult(
            MatrixStack stack, List<Vec3d> vec3ds, HitResult result, float tickDelta) {
        if (vec3ds.size() <= 3) return;
        RenderUtils.drawStripLineVirtual(stack, vec3ds, Color.RED);
        if (!vec3ds.isEmpty()) {
            if (result == null || result.getType() != HitResult.Type.ENTITY) {
                Vec3d finalPosition = vec3ds.get(vec3ds.size() - 1);
                RenderUtils.drawSolidBox(
                        stack,
                        finalPosition.add(RenderTasks.SMALL_FROM),
                        finalPosition.add(RenderTasks.SMALL_TO),
                        ColorUtils.withAlpha(Color.GREEN, 0.25F));
            } else {
                Entity hitEntity = ((EntityHitResult) result).getEntity();
                Box box = RenderUtils.getLerpedBox(hitEntity, tickDelta);
                RenderUtils.drawSolidBox(
                        stack, box.getMinPos(), box.getMaxPos(), ColorUtils.withAlpha(Color.GREEN, 0.25F));
            }
        }
    }

    public static ArrayList<Vec3d> predictFireballTrace(ExplosiveProjectileEntity fireball) {
        ArrayList<Vec3d> trace = new ArrayList<>();
        Vec3d startpos = fireball.getPos();
        Vec3d lastPos = startpos;
        var access = ExplosiveProjectileAccess.of(fireball);
        float drag = access.getDragCommon();
        Vec3d motion = fireball.getVelocity();
        Vec3d power = motion.normalize().multiply(fireball.accelerationPower);

        trace.add(startpos);

        for (int i = 0; i < 400; ++i) {
            startpos = startpos.add(motion);
            motion = motion.add(power).multiply(drag);
            trace.add(startpos);
            if (trace.size() > 2) {
                lastPos = trace.get(trace.size() - 2);
                if (RaycastUtils.raycastAnySolidBlock(fireball, lastPos, startpos)
                        || RaycastUtils.raycastHitAnyEntityExceptPlayer(fireball, lastPos, startpos)) {
                    break;
                }
            }
        }
        return trace;
    }
}
