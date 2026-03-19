package me.matl114.hacks.modules.combat;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.*;
import me.matl114.utils.*;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class Spear extends BaseModule implements LegalMovementManager.MovementModifier {
    public static final String[] SPEAR_ATTACK_HOTKEY = new String[] {"spear-module", "spear-attack-hotkey"};
    public static final String[] SPEAR_DISTANCE = new String[] {"spear-module", "spear-motion-simulation"};
    public static final String[] SPEAR_MAX_TP = new String[] {"spear-module", "spear-max-tp"};
    public static final String[] SPEAR_RENDER = new String[] {"spear-module", "render-target"};
    private static LegalMovementManager.DelegateMovementModifier INSTANCE;

    public Spear() {
        if (INSTANCE == null) {
            INSTANCE = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_POS.addMovementModifierFactory(() -> INSTANCE);
        }
        INSTANCE.setDelegate(this::cast);
    }

    @Override
    public int priority() {
        return -1000;
    }

    public final KeyBindRef keyBind = hotkey(Configs.COMBAT_CONFIG, SPEAR_ATTACK_HOTKEY)
            .defaultValue(new MultiKeyBind(KeyCode.MOUSE_BUTTON_1))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onSpearAction))
            .build();

    public final DoubleRef spearDistance = builder(Configs.COMBAT_CONFIG, SPEAR_DISTANCE, DoubleRef.TYPE)
            .defaultValue(50.0D)
            .build();

    public final DoubleRef spearMaxTp = builder(Configs.COMBAT_CONFIG, SPEAR_MAX_TP, DoubleRef.TYPE)
            .defaultValue(100.0D)
            .build();

    public final FlagRef spearRender =
            flagBuilder(Configs.COMBAT_CONFIG, SPEAR_RENDER).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRenderLayerTasks(), this::renderPlayerSpearTarget);
    }
    //
    public boolean onSpearAction() {
        if (canSpearAttack()) {
            if (currentWaitBackTick > 0) {
                return true;
            }
            currentWaitBackTick = 1;
            // Tasks.scheduleDelayed(this::spearAttack, 0);
            return spearAttack();
        }
        return false;
    }

    public boolean canSpearAttack() {
        if (mc.player.isUsingItem()
                && VItem.getInstance().isSpear(mc.player.getActiveItem())
                && mc.player.getItemUseTime() >= 8) {
            ItemStack stack = mc.player.getActiveItem();
            KineticWeaponComponent component = stack.get(DataComponentTypes.KINETIC_WEAPON);
            if (component != null && mc.player.getItemUseTime() < component.delayTicks()) {
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean spearAttack() {
        AttackRangeComponent attackRange = mc.player.getAttackRange();
        Entity target = CombatTasks.getTargetSelector()
                .searchAttackEntity(
                        spearDistance.get() + attackRange.getEffectiveMaxRange(mc.player), true, this::isSpearable);
        if (target == null) {
            currentWaitBackTick = 0;
            return false;
        }
        if (spearMaxTp.get() < spearDistance.get()) {
            Debug.chat("[Spear] 参数错误, MaxTp不能小于Distance");
            currentWaitBackTick = 0;
            return true;
        }
        Vec3d playerPos = mc.player.getPos();
        Vec2f playerPy = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        Vec3d targetPos = CombatTasks.getPositionPredict().predictPosition(target);
        Vec3d direction =
                targetPos.add(0, target.getEyeHeight(target.getPose()), 0).subtract(mc.player.getEyePos());
        if (RenderTasks.DEBUG_RENDER_SPEAR) {
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                    RenderTasks.DEBUG_TICK,
                    new RenderTasks.LineObject(mc.player.getEyePos(), direction).color(Color.MAGENTA)));
        }
        double distance = direction.length();
        direction = direction.normalize();
        Vec3d tpDirection = direction.multiply(-1);
        Vec3d horizontalLine = (direction.y != 0
                        ? new Vec3d(
                                direction.x,
                                -(MathUtils.s2(direction.x) + MathUtils.s2(direction.z)) / direction.y,
                                direction.z)
                        : new Vec3d(0, 1, 0))
                .normalize();

        var re = findValidTpPosition(
                playerPos, tpDirection, horizontalLine, spearMaxTp.get(), spearDistance.get(), distance);
        if (re != null) {
            var to = re.getFirst();
            var from = re.getSecond();
            List<MovTasks.MovInfo> toList = new ArrayList<>();
            Vec2f py = EntityUtils.rotationToPitchYaw(direction);
            for (var i = 0; i < to.size() - 1; i++) {
                toList.add(new MovTasks.MovInfo(to.get(i), false, false, null));
            }
            Vec3d targetTpPos = to.get(to.size() - 1);
            if (RenderTasks.DEBUG_RENDER_SPEAR) {
                RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                        RenderTasks.DEBUG_TICK,
                        new RenderTasks.BoxObject(
                                targetTpPos.add(RenderTasks.FROM), targetTpPos.add(RenderTasks.TO), Color.MAGENTA)));
                RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                        RenderTasks.DEBUG_TICK, new RenderTasks.LineToTargetObject(targetTpPos, Color.MAGENTA)));
            }
            toList.add(new MovTasks.MovInfo(targetTpPos, false, false, py));
            // DO NOT CONSIDER NOFALL, it may send extra packets
            MovTasks.scheduleMoveSequence(MovTasks.createPlayerMovContext(), toList, false, true);
            // DO NOT SEND PACKET HERE
            ClientPlayerAccess.of(mc.player).setForceNoFall(false);
            List<MovTasks.MovInfo> fromList = new ArrayList<>();
            for (var i = 0; i < from.size() - 1; i++) {
                fromList.add(new MovTasks.MovInfo(from.get(i), false, false, null));
            }
            fromList.add(new MovTasks.MovInfo(from.get(from.size() - 1), false, true, py));
            // Debug.chat("move");
            Debug.chat(Text.literal("[Spear] simulate range %.2f"
                            .formatted(playerPos.subtract(targetTpPos).dotProduct(direction)))
                    .formatted(Formatting.GREEN));
            // Debug.info("target", targetTpPos);
            currentWaitBackTick = 4;
            mc.player.setPitch(playerPy.x);
            mc.player.setYaw(playerPy.y);
            MovTasks.setupAutoResync(targetTpPos);
            Tasks.scheduleDelayed(
                    () -> {
                        if (mc.player == null) return;
                        currentWaitBackTick = 1;
                        Vec3d currentPlayerPos = mc.player.getPos();
                        Vec2f currentPlayerPy = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
                        mc.player.setPosition(targetTpPos);
                        // Debug.chat("move back");
                        MovTasks.scheduleMoveSequence(MovTasks.createMovContext(targetTpPos), fromList, false, true);
                        currentPlayerPos = playerPos.subtract(currentPlayerPos).lengthSquared() < 100.0D
                                ? currentPlayerPos
                                : playerPos;
                        mc.player.setPosition(currentPlayerPos);
                        MovTasks.setupAutoResync();
                        mc.player.setPitch(currentPlayerPy.x);
                        mc.player.setYaw(currentPlayerPy.y);
                        ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                    },
                    2);
            return true;
        } else {
            Debug.chat("[Spear] Can not reach target");
            currentWaitBackTick = 0;
            return true;
        }
    }

    public Pair<List<Vec3d>, List<Vec3d>> isValidTpLocation(
            MovTasks.CollisionContext context, Vec3d playerLocation, Vec3d tpLocation, double maxDistance) {
        var listTo = context.generateTpSequence(playerLocation, tpLocation, false, 320, true);
        if (listTo.isEmpty()) return null;
        var listBack = context.generateTpSequence(tpLocation, playerLocation, false, 320, true);
        if (listBack.isEmpty()) return null;
        return Pair.of(listTo, listBack);
    }

    public void onSpearAttackRender(Event<MatrixStack> event) {
        MatrixStack stack = event.context();
        {
            RenderUtils.startDrawVirtual(stack);
            try {
                for (var entity : mc.world.getEntities()) {
                    if (entity instanceof LivingEntity livingEntity
                            && livingEntity.isUsingItem()
                            && VItem.getInstance().isSpear(livingEntity.getActiveItem())) {
                        drawPlayerUseSpearTick(livingEntity, stack);
                    }
                }
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    int currentWaitBackTick = 0;

    private void renderPlayerSpearTarget(Event<MatrixStack> event) {
        if (RenderTasks.DEBUG_RENDER_SPEAR) {
            onSpearAttackRender(event);
        }
        MatrixStack stack = event.context();
        float tickDelta = event.getArgs(0);
        if (spearRender.get()) {
            RenderUtils.startDrawVirtual(stack);
            try {
                if (canSpearAttack()) {
                    AttackRangeComponent attackRange = mc.player.getAttackRange();
                    Entity spearEntity = CombatTasks.getTargetSelector()
                            .searchAttackEntity(
                                    spearDistance.get() + attackRange.getEffectiveMaxRange(mc.player),
                                    true,
                                    this::isSpearable);
                    if (spearEntity != null) {
                        float dist = spearEntity.distanceTo(mc.player);
                        float opacity = Math.min(0.6F, 0.10F + dist * 0.02F);
                        Box box = RenderUtils.getLerpedBox(spearEntity, tickDelta);
                        RenderUtils.drawSolidBox(
                                stack, box.getMinPos(), box.getMaxPos(), ColorUtils.withAlpha(Color.GREEN, opacity));
                    }
                }
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    private void drawPlayerUseSpearTick(LivingEntity entity, MatrixStack stack) {
        AttackRangeComponent attackRange = entity.getAttackRange();
        Vec3d startEye = entity.getEyePos();
        Vec3d direction = entity.getHeadRotationVector();
        double minRange = attackRange.getEffectiveMinRange(entity);
        double maxRange = attackRange.getEffectiveMaxRange(entity);
        double speedBonus = Math.max(0, entity.getMovement().dotProduct(direction));
        double finalMaxRange = maxRange + speedBonus;

        Vec3d startPoint = startEye.add(direction.multiply(minRange));
        Vec3d endPoint = startEye.add(direction.multiply(finalMaxRange));
        float hitboxMargin = attackRange.hitboxMargin();
        Box box = Box.of(startPoint, (double) hitboxMargin, (double) hitboxMargin, (double) hitboxMargin)
                .stretch(endPoint.subtract(startPoint))
                .expand(1.0);
        stack.push();
        Vec3d eyeToCamera = startEye.subtract(RenderUtils.getCameraPos());
        RenderUtils.drawOutlinedBox(stack, box.getMinPos(), box.getMaxPos(), Color.MAGENTA);
        RenderUtils.drawLineVirtual(stack, startPoint, endPoint, Color.MAGENTA);
        float max = Math.max(0, hitboxMargin);
        for (var e : mc.world.getOtherEntities(entity, box)) {
            if (e instanceof LivingEntity livingEntity) {
                if (livingEntity.getBoundingBox().raycast(startPoint, endPoint).isPresent()) {
                    RenderUtils.drawSolidBox(
                            stack,
                            livingEntity.getBoundingBox().getMinPos(),
                            livingEntity.getBoundingBox().getMaxPos(),
                            ColorUtils.withAlpha(Color.BLUE, 0.25F));
                } else if (max > 0) {
                    var box2 = livingEntity.getBoundingBox().expand(hitboxMargin);
                    var re = box2.raycast(startPoint, endPoint);
                    if (re.isPresent()) {
                        Vec3d vec3d = re.get();
                        Vec3d vec3d2 = box2.getCenter();
                        Optional<Vec3d> optional3 =
                                livingEntity.getBoundingBox().raycast(vec3d, vec3d2);
                        if (optional3.isPresent()) {
                            RenderUtils.drawSolidBox(
                                    stack,
                                    livingEntity.getBoundingBox().getMinPos(),
                                    livingEntity.getBoundingBox().getMaxPos(),
                                    ColorUtils.withAlpha(Color.BLUE, 0.25F));
                        }
                    }
                }
            }
        }
        stack.pop();
    }

    private boolean isSpearable(Entity entity) {
        // Vec3d pos = mc.player.getEyePos();
        if (mc.player.getEyePos().subtract(entity.getEyePos()).lengthSquared()
                <= MathUtils.s2(mc.player.getAttackRange().getEffectiveMinRange(mc.player))) {
            return false;
        }
        BlockHitResult blockHitResult = mc.world.getCollisionsIncludingWorldBorder(new RaycastContext(
                mc.player.getEyePos(),
                entity.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            return false;
            //            Vec3d maxReach = blockHitResult.getPos();
            //            if (pos.squaredDistanceTo(maxReach)
            //                    < MathUtils.s2(mc.player.getAttackRange().getEffectiveMinRange(mc.player))) {
            //                return false;
            //            }
        }
        return true;
    }

    IntList searchOrder = new IntArrayList();

    {
        searchOrder.add(0);
        for (var i = 1; i < 30; ++i) {
            searchOrder.add(i);
            searchOrder.add(-i);
        }
    }

    public Pair<List<Vec3d>, List<Vec3d>> findValidTpPosition(
            Vec3d currentPlayerPos,
            Vec3d tpDirection,
            Vec3d expandDirection,
            double maxDistance,
            double distance,
            double minDistance) {
        MovTasks.CollisionContext context = MovTasks.ENGIN;
        Pair<List<Vec3d>, List<Vec3d>> result = null;
        for (double search = distance; search > minDistance; search -= 2.0D) {
            for (var i : searchOrder) {
                Vec3d searchTpPos =
                        currentPlayerPos.add(tpDirection.multiply(search)).add(expandDirection.multiply(i));
                if (searchTpPos.squaredDistanceTo(currentPlayerPos) > MathUtils.s2(maxDistance)) {
                    break;
                }
                if (!context.checkEnvironmentCollision(mc.player, searchTpPos)
                        && (result = isValidTpLocation(context, currentPlayerPos, searchTpPos, maxDistance)) != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public boolean mayModifyRotation() {
        return false;
    }

    @Override
    public boolean mayModifyPos() {
        return false;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (currentWaitBackTick > 0) {
            currentWaitBackTick -= 1;
            movementManagerEvent.cancel();
            // movementManagerEvent.context.playerStatus.restorePos();
            movementManagerEvent.context.playerStatus.entity.setOnGround(false);
        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (currentWaitBackTick > 0) {
            currentWaitBackTick -= 1;
            movementManagerEvent.cancel();
            // movementManagerEvent.context.playerStatus.restorePos();
            movementManagerEvent.context.playerStatus.entity.setOnGround(false);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }
}
