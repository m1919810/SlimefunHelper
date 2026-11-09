package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.render.ProjectileESP;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hacks.utils.RaycastUtils;
import me.matl114.hacks.utils.RenderUtils;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class PearlFly extends BaseModule {
    public PearlFly() {
        super("PearlFly");
    }

    public final ModulePath combatUtils = makePath(Configs.COMBAT_CONFIG, "combat-utils");
    public final ModulePath pearl = combatUtils.add("pearl-fly");

    public final ModulePath pearlPhase = combatUtils.add("pearl-phase");
    public final FlagRef enablePearlPhase = flagBuilder(pearlPhase.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(pearlPhase.addHotkey(), new MultiKeyBind(), pearlPhase.addEnable())
            .build();

    public final FlagRef enableCrawl =
            flagBuilder(pearlPhase.add("enable-crawl")).build();

    public final FlagRef enableStand =
            flagBuilder(pearlPhase.add("enable-stand")).build();

    public final FlagRef autoCrawl = flagBuilder(pearlPhase.add("auto-crawl")).build();

    public final DoubleRef autoPearlActivateRange = doubleBuilder(pearlPhase.add("auto-activate-range"))
            .defaultValue(0.35)
            .validator(Configs.doubleRange(0.0D, 2.0D))
            .build();

    public final FlagRef useWASDControl =
            flagBuilder(pearlPhase.add("use-wasd-control")).build();

    public final FlagRef enableJump = builder(pearlPhase.add("enable-jump-up"), Boolean.class)
            .defaultValue(true)
            .build();

    public final ModulePath pearlThrow = combatUtils.add("pearl-throw");

    public final KeyBindRef executePearlThrow = hotkey(pearlThrow.add("execute-throw"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::doPearlThrow))
            .build();

    public final FlagRef pearlAimRender =
            flagBuilder(pearlThrow.add("pearl-trace-render")).build();

    public final KeyBindRef executePearlHelper = toggleHotkey(
                    pearlThrow.add("pearl-trace-render-hotkey"),
                    new MultiKeyBind(),
                    pearlThrow.add("pearl-trace-render"))
            .build();

    public final NBTRef<WrapColor> color = builder(pearlThrow.add("color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.ORANGE)))
            .build();

    public final FlagRef exactDirection = flagBuilder(pearlThrow.add("exact")).build();

    public final EnumRef<GhostHandMode> ghostHand = builder(pearl.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef offhand = flagBuilder(pearl.add("offhand")).build();

    public final FlagRef swingHand =
            builder(pearl.add("swing-hand"), Boolean.class).defaultValue(true).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    Vec2f pearlThrowRotation = null;

    public void onInputEvent() {
        if (checkNull()) return;
        if (enablePearlPhase.get()) {
            var pose = mc.player.getPose();
            if (pose == EntityPose.SWIMMING) {
                if (!enableCrawl.get()) {
                    return;
                }
            } else {
                if (!enableStand.get()) {
                    return;
                }
            }
            if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
                return;
            }
            if (useWASDControl.get()) {
                Vec3d facingDirection = EntityUtils.pitchYawToRotation(0, mc.player.getYaw());
                var input = PlayerInputUtils.of(mc.options);
                Vec3d adAxis = new Vec3d(facingDirection.z, 0, -facingDirection.x);
                Vec3d inputVec = facingDirection
                        .multiply(input.forwardSpeed())
                        .add(adAxis.multiply(input.sidewaysSpeed()))
                        .normalize();
                BlockPos searchPos = BlockPos.ofFloored(mc.player.getPos().add(inputVec));
                if (enableJump.get() && input.upwardSpeed() > 0) {
                    searchPos = searchPos.offset(Direction.UP, input.upwardSpeed());
                }
                if (!Objects.equals(mc.player.getBlockPos(), searchPos) && doPearlUse(searchPos)) {
                    enablePearlPhase.set(false);
                    return;
                }
            } else {
                Box playerBox = mc.player.getBoundingBox();
                double extra = autoPearlActivateRange.get();
                double minX = playerBox.minX;
                double maxX = playerBox.maxX;
                double minY = playerBox.minY;
                double maxY = playerBox.maxY;
                double minZ = playerBox.minZ;
                double maxZ = playerBox.maxZ;
                List<Box> verticalSearchBoxes;
                if (autoCrawl.get()) {
                    verticalSearchBoxes = new ArrayList<>();
                    for (int y = MathHelper.floor(minY); y < Math.ceil(maxY); y++) {
                        double sliceMinY = Math.max(minY, y);
                        double sliceMaxY = Math.min(maxY, y + 1.0);
                        verticalSearchBoxes.add(new Box(minX, sliceMinY, minZ, maxX, sliceMaxY, maxZ));
                    }
                } else {
                    verticalSearchBoxes = List.of(playerBox);
                }
                List<Box> cornerSearchBoxes = List.of(
                        new Box(maxX, minY, maxZ, maxX + extra, maxY, maxZ + extra),
                        new Box(minX - extra, minY, maxZ, minX, maxY, maxZ + extra),
                        new Box(minX - extra, minY, minZ - extra, minX, maxY, minZ),
                        new Box(maxX, minY, minZ - extra, maxX + extra, maxY, minZ));
                List<Box> sideSearchBoxes = List.of(
                        new Box(maxX, minY, minZ, maxX + extra, maxY, maxZ),
                        new Box(minX - extra, minY, minZ, minX, maxY, maxZ),
                        new Box(minX, minY, maxZ, maxX, maxY, maxZ + extra),
                        new Box(minX, minY, minZ - extra, maxX, maxY, minZ));

                List<List<BlockPos>> collisionGroups = new ArrayList<>();
                for (List<Box> searchBoxes : List.of(cornerSearchBoxes, sideSearchBoxes)) {
                    collisionGroups.add(searchBoxes.stream()
                            .flatMap(searchBox -> verticalSearchBoxes.stream()
                                    .map(verticalBox -> new Box(
                                            searchBox.minX,
                                            verticalBox.minY,
                                            searchBox.minZ,
                                            searchBox.maxX,
                                            verticalBox.maxY,
                                            searchBox.maxZ)))
                            .flatMap(
                                    searchBox -> CollisionUtil.getBoxCollision(mc.world, mc.player, searchBox).stream())
                            .distinct()
                            .toList());
                }

                int blockY = mc.player.getBlockY();
                Vec3d eyePos = mc.player.getEyePos();
                Comparator<BlockPos> horizontalDistance = Comparator.comparingDouble(pos ->
                        MathUtils.squareSum(pos.getX() + 0.5 - mc.player.getX(), pos.getZ() + 0.5 - mc.player.getZ()));
                if (autoCrawl.get() && pose != EntityPose.SWIMMING && pose != EntityPose.GLIDING) {
                    BlockPos crawlPos = collisionGroups.stream()
                            .flatMap(List::stream)
                            .filter(pos -> pos.getY() == blockY + 1)
                            .filter(pos -> {
                                BlockPos crawlSpacePos = pos.down();
                                return mc.world
                                        .getBlockState(crawlSpacePos)
                                        .getCollisionShape(mc.world, crawlSpacePos)
                                        .isEmpty();
                            })
                            .filter(pos -> RaycastUtils.raycastOnlyBlockCollisions(
                                            eyePos, getPearlTargetPoint(pos, blockY + 0.01))
                                    == null)
                            .max(horizontalDistance)
                            .orElse(null);
                    if (crawlPos != null
                            && usePearl(
                                    getPearlTargetPoint(crawlPos, blockY + 0.01).subtract(eyePos))) {
                        enablePearlPhase.set(false);
                        return;
                    }
                }

                for (List<BlockPos> collisionPositions : collisionGroups) {
                    BlockPos targetPos = collisionPositions.stream()
                            .filter(pos -> pos.getY() == blockY)
                            .max(horizontalDistance)
                            .orElse(null);
                    if (targetPos != null && doPearlUse(targetPos)) {
                        enablePearlPhase.set(false);
                        return;
                    }
                }
            }
        }
        if (pearlThrowRotation != null
                && !mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
            usePearl(EntityUtils.pitchYawToRotation(pearlThrowRotation.x, pearlThrowRotation.y));
            pearlThrowRotation = null;
        }
        lineCollector.clear();
        hitBoxCollector.clear();
        if (pearlAimRender.get()) {
            Vec2f direction;
            Vec3d eye = mc.player.getEyePos();
            if (exactDirection.get()) {
                Vec3d rayCastStart = RenderUtils.getCameraPos();
                Vec3d look = RenderUtils.getCameraLookVec(0.0F);
                var hit = RaycastUtils.raycastOnlyBlockCollisions(rayCastStart, rayCastStart.add(look.multiply(128)));
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    Vec3d hitPoint = hit.getPos();
                    var red = CombatTasks.calculatePitchYawPredict(1.48F, Vec3d.ZERO, hitPoint.subtract(eye));
                    if (Float.isNaN(red.x)
                            || Float.isInfinite(red.x)
                            || Float.isNaN(red.y)
                            || Float.isInfinite(red.y)) {
                        return;
                    } else {
                        direction = red;
                    }
                } else {
                    return;
                }
            } else {
                direction = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
            }
            Vec3d rot = EntityUtils.pitchYawToRotation(direction.x, direction.y);
            var predictor = new ProjectileESP.ArrowPredictor(
                    eye.subtract(0, 0.11, 0), rot.normalize().multiply(1.48F), mc.player);
            var result = predictor.predictLineWithHitResult(400);
            if (result != null) {
                lineCollector.submit(result.getFirst(), color.get().withAlpha(255));
                HitResult hitPose = result.getSecond();
                if (hitPose != null) {
                    var hitPos = hitPose.getPos();
                    hitBoxCollector.submit(
                            new Box(hitPos.add(RenderTasks.SMALL_FROM), hitPos.add(RenderTasks.SMALL_TO)),
                            color.get().withAlpha(64));
                }
            }
        }
    }

    RenderCollector<List<Vec3d>> lineCollector = RenderCollectors.createLinesCollector();
    RenderCollector<Box> hitBoxCollector = RenderCollectors.createBoxCollector(true, true, false);

    public void doPearlThrow() {
        if (checkNull()) return;
        if (exactDirection.get()) {
            Vec3d rayCastStart = RenderUtils.getCameraPos();
            Vec3d look = RenderUtils.getCameraLookVec(0.0F);
            var hit = RaycastUtils.raycastOnlyBlockCollisions(rayCastStart, rayCastStart.add(look.multiply(128)));
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                Vec3d hitPoint = hit.getPos();
                var red = CombatTasks.calculatePitchYawPredict(
                        1.48F, Vec3d.ZERO, hitPoint.subtract(mc.player.getEyePos()));
                if (Float.isNaN(red.x) || Float.isInfinite(red.x) || Float.isNaN(red.y) || Float.isInfinite(red.y)) {
                    logI18NSub("Pearl", "message.module.pearl-fly.can-not-reach-target");
                } else {
                    pearlThrowRotation = red;
                }
            } else {
                logI18NSub("Pearl", "message.module.pearl-fly.no-target");
            }
        } else {
            pearlThrowRotation = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        }
    }

    public boolean doPearlUse(BlockPos pos) {
        Vec3d eyePos = mc.player.getEyePos();
        return usePearl(getPearlTargetPoint(pos, mc.player.getBlockY() + 0.5).subtract(eyePos));
    }

    private Vec3d getPearlTargetPoint(BlockPos pos, double y) {
        Vec3d eyePos = mc.player.getEyePos();
        return MathUtils.magnitudePoint(new Box(pos), eyePos).withAxis(Direction.Axis.Y, y);
    }

    public boolean usePearl(Vec3d look) {
        look = look.normalize();
        var re = InventoryUtils.findPlayerItem((ss) -> ss.getItem() == Items.ENDER_PEARL, true, false);
        if (re == null) {
            logI18NSub("Pearl", "message.module.pearl-fly.no-pearl");
            return true;
        }

        boolean offHand = offhand.get() || re.index() == 40;
        Runnable runnable = InvExtra.INSTANCE.swapItemToHand(re.index(), offHand, ghostHand.get());
        if (runnable == null) return false;
        InteractionTasks.interactItem(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND, look, true, swingHand.get());
        runnable.run();
        return true;
    }

    public void onRender3D(Event<Render3D> event) {
        if (checkNull())
            ;
        if (pearlAimRender.get()) {
            RenderUtils.startDrawVirtual(event.context.stack());
            try {
                lineCollector.render3D(event.context.stack(), event.context.partialTicks());
                hitBoxCollector.render3D(event.context.stack(), event.context.partialTicks());
            } finally {
                RenderUtils.stopDrawVirtual(event.context.stack());
            }
        }
    }
}
