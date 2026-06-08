package me.matl114.hacks.modules.combat;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.*;
import me.matl114.utils.*;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

public class SpearAttack extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath spearModule = makePath(Configs.COMBAT_CONFIG, "spear-module");
    private static LegalMovementManager.DelegateMovementModifier INSTANCE;

    public SpearAttack() {
        if (INSTANCE == null) {
            INSTANCE = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> INSTANCE);
        }
        INSTANCE.setDelegate(this::cast);
        bindFlag(enable);
    }

    @Override
    public int priority() {
        return PRIORITY_LOW;
    }

    public final FlagRef enable = builder(spearModule.add("spear-attack-enable"), Boolean.class)
            .defaultValue(true)
            .build();

    public final KeyBindRef keyBind = hotkey(spearModule.add("spear-attack-hotkey"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onSpearAction))
            .build();

    public final DoubleRef spearDistance = builder(spearModule.add("spear-motion-simulation"), DoubleRef.TYPE)
            .defaultValue(50.0D)
            .build();

    public final DoubleRef spearMaxTp = builder(spearModule.add("spear-max-tp"), DoubleRef.TYPE)
            .defaultValue(100.0D)
            .build();

    public final FlagRef spearRender =
            flagBuilder(spearModule.add("render-target")).build();

    public final IntRef delay = intBuilder(spearModule.add("spear-server-tick-delay"))
            .defaultValue(2)
            .validator(Configs.INT_POSITIVE)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRenderLayerTasks(), this::renderPlayerSpearTarget);
        registerListener(Listener.getPacketPoint().getChannel(ClientTickEndC2SPacket.class), this::onClientTickEnd);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }
    //
    public boolean onSpearAction() {
        if (enable.get() && canSpearAttack()) {
            if (currentWaitBackTick > 0) {
                return true;
            }
            currentWaitBackTick = 1;
            // Tasks.scheduleDelayed(this::spearAttack, 0);
            return spearAttack();
        }
        return false;
    }
    // todo: find out which flag determines the spear status
    public boolean canSpearAttack() {
        return CombatTasks.getSpearEnhance().canSpearKineticAttack();
    }

    public boolean spearAttack() {
        if (spearDistance.get() < 0) {
            currentWaitBackTick = 0;
            return false;
        }
        Entity target =
                CombatTasks.getTargetSelector().searchAttackEntity(spearDistance.get(), true, this::isSpearable);
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
        Vec3d targetPos = CombatTasks.getPositionPredict()
                .spearPredictArgument
                .get()
                .predict(target); // .predictAttackPosition(target);
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
        Vec3d horizontalLine = MathUtils.getVerticalWithSameXZ(direction);

        var re = findValidTpPosition(
                playerPos, tpDirection, horizontalLine, spearMaxTp.get(), spearDistance.get(), distance);
        // ensure the back tp is a direct tp
        if (re != null && re.getSecond().size() == 2) {
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
            Vec3d backpos = re.getSecond().get(1);
            toList.add(new MovTasks.MovInfo(backpos, false, false, py));
            // DO NOT CONSIDER NOFALL, it may send extra packets
            MovTasks.scheduleMoveSequence(MovTasks.createPlayerMovContext(), toList, false, true);
            // DO NOT SEND PACKET HERE
            ClientPlayerAccess.of(mc.player).setForceNoFall(false);

            Debug.chat(Text.literal("[Spear] simulate delay %.2f"
                            .formatted(playerPos.subtract(targetTpPos).dotProduct(direction)))
                    .formatted(Formatting.GREEN));
            // Debug.info("target", targetTpPos);
            currentWaitBackTick = delay.get() + 1;
            mc.player.setPitch(playerPy.x);
            mc.player.setYaw(playerPy.y);
            MovTasks.setupAutoResync();
            return true;
            // todo: check if we can do 1tick move
        } else {
            Debug.chat("[Spear] Too far to reach target");
            currentWaitBackTick = 0;
            return true;
        }
    }

    public Pair<List<Vec3d>, List<Vec3d>> isValidTpLocation(
            MovTasks.CollisionContext context, Vec3d playerLocation, Vec3d tpLocation, double maxDistance) {
        Vec3d currentSimulateMovement = playerLocation.subtract(tpLocation);
        List<Vec3d> back;
        if (MovTasks.validMoveTo(context, tpLocation, currentSimulateMovement)) {
            back = List.of(tpLocation, playerLocation);
        } else return null;
        var listTo = context.generateTpSequence(playerLocation, tpLocation, false, 320, true);
        if (listTo.isEmpty()) return null;
        // back movement should be one

        //        var listBack = context.generateTpSequence(tpLocation, playerLocation, false, 320, true);
        return Pair.of(listTo, back);
    }

    //    public void onSpearAttackRender(Event<MatrixStack> event) {
    //        MatrixStack stack = event.context();
    //        {
    //            RenderUtils.startDrawVirtual(stack);
    //            try {
    //                for (var entity : mc.world.getEntities()) {
    //                    if (entity instanceof LivingEntity livingEntity
    //                            && livingEntity.isUsingItem()
    //                            && VItem.getInstance().isSpear(livingEntity.getActiveItem())) {
    //                        drawPlayerUseSpearTick(livingEntity, stack);
    //                    }
    //                }
    //            } finally {
    //                RenderUtils.stopDrawVirtual(stack);
    //            }
    //        }
    //    }
    // > 1 : no TickEnd, no move
    // == 1 : make noFall for next tick, can TickEnd, can not start next Spear
    // == 0 can move, can Start next Spear
    int currentWaitBackTick = 0;

    private void renderPlayerSpearTarget(Event<MatrixStack> event) {
        if (!enable.get()) return;
        //        if (RenderTasks.DEBUG_RENDER_SPEAR) {
        //            onSpearAttackRender(event);
        //        }
        MatrixStack stack = event.context();
        float tickDelta = event.getArgs(0);
        if (spearRender.get()) {
            RenderUtils.startDrawVirtual(stack);
            try {
                if (canSpearAttack()) {
                    Entity spearEntity = CombatTasks.getTargetSelector()
                            .searchAttackEntity(spearDistance.get(), false, this::isSpearable);
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

    //    private void drawPlayerUseSpearTick(LivingEntity entity, MatrixStack stack) {
    //        AttackRangeComponent attackRange = entity.getAttackRange();
    //        Vec3d startEye = entity.getEyePos();
    //        Vec3d direction = entity.getHeadRotationVector();
    //        double minRange = attackRange.getEffectiveMinRange(entity);
    //        double maxRange = attackRange.getEffectiveMaxRange(entity);
    //        double speedBonus = Math.max(0, entity.getMovement().dotProduct(direction));
    //        double finalMaxRange = maxRange + speedBonus;
    //
    //        Vec3d startPoint = startEye.add(direction.multiply(minRange));
    //        Vec3d endPoint = startEye.add(direction.multiply(finalMaxRange));
    //        float hitboxMargin = attackRange.hitboxMargin();
    //        Box box = Box.of(startPoint, (double) hitboxMargin, (double) hitboxMargin, (double) hitboxMargin)
    //                .stretch(endPoint.subtract(startPoint))
    //                .expand(1.0);
    //        stack.push();
    //        RenderUtils.drawOutlinedBox(stack, box.getMinPos(), box.getMaxPos(), Color.MAGENTA);
    //        RenderUtils.drawLineVirtual(stack, startPoint, endPoint, Color.MAGENTA);
    //        float max = Math.max(0, hitboxMargin);
    //        for (var e : mc.world.getOtherEntities(entity, box)) {
    //            if (e instanceof LivingEntity livingEntity) {
    //                if (livingEntity.getBoundingBox().raycast(startPoint, endPoint).isPresent()) {
    //                    RenderUtils.drawSolidBox(
    //                            stack,
    //                            livingEntity.getBoundingBox().getMinPos(),
    //                            livingEntity.getBoundingBox().getMaxPos(),
    //                            ColorUtils.withAlpha(Color.BLUE, 0.25F));
    //                } else if (max > 0) {
    //                    var box2 = livingEntity.getBoundingBox().expand(hitboxMargin);
    //                    var re = box2.raycast(startPoint, endPoint);
    //                    if (re.isPresent()) {
    //                        Vec3d vec3d = re.get();
    //                        Vec3d vec3d2 = box2.getCenter();
    //                        Optional<Vec3d> optional3 =
    //                                livingEntity.getBoundingBox().raycast(vec3d, vec3d2);
    //                        if (optional3.isPresent()) {
    //                            RenderUtils.drawSolidBox(
    //                                    stack,
    //                                    livingEntity.getBoundingBox().getMinPos(),
    //                                    livingEntity.getBoundingBox().getMaxPos(),
    //                                    ColorUtils.withAlpha(Color.BLUE, 0.25F));
    //                        }
    //                    }
    //                }
    //            }
    //        }
    //        stack.pop();
    //    }

    private boolean isSpearable(Entity entity) {
        // Vec3d pos = mc.player.getEyePos();
        if (mc.player.getEyePos().subtract(entity.getEyePos()).lengthSquared() <= MathUtils.s2(2.0D)) {
            return false;
        }
        BlockHitResult blockHitResult = mc.world.raycast(new RaycastContext(
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

    DoubleList searchOrder = new DoubleArrayList();

    {
        searchOrder.add(0);

        for (var i = 1; i < 10; ++i) {
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
        //            new MovTasks.CollisionCache(
        //            mc.player,
        //            currentPlayerPos.add(- maxDistance , -maxDistance, -maxDistance),
        //            currentPlayerPos.add(maxDistance, maxDistance, maxDistance),
        //            true);
        Pair<List<Vec3d>, List<Vec3d>> result = null;

        for (double search = distance; search > minDistance; search -= 2.0D) {
            for (var i : searchOrder) {
                Vec3d searchTpPos =
                        currentPlayerPos.add(tpDirection.multiply(search)).add(expandDirection.multiply(i));
                if (searchTpPos.squaredDistanceTo(currentPlayerPos) > MathUtils.s2(maxDistance)) {
                    break;
                }
                if (!context.checkEnvironmentCollision(mc.player, searchTpPos, true)
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
            if (currentWaitBackTick > delay.get()) {
                currentWaitBackTick = delay.get();
            }
            if (currentWaitBackTick > 0) {
                movementManagerEvent.cancel();
                // movementManagerEvent.context.playerStatus.restorePos();
                movementManagerEvent.context.playerStatus.entity.setOnGround(false);
            }

            if (currentWaitBackTick == 1) {
                if (RenderTasks.DEBUG_RENDER_SPEAR) {
                    Debug.chat("Delay finish");
                }
                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                // trigger pitch yaw resync and pos resync
                ClientPlayerAccess.of(mc.player).resyncPos();
                ClientPlayerAccess.of(mc.player).resyncRot();
            }
        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        this.applyBeforeMovementPacketModify(movementManagerEvent);
    }

    public void onClientTickEnd(Event<ClientTickEndC2SPacket> tickEndPacket) {
        if (currentWaitBackTick > 1) {
            tickEndPacket.cancel();
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context().getValue();
        switch (preset) {
            case HACKING, VANILLA -> {
                if (spearDistance.get() < 0.0D) {
                    spearDistance.set(-spearDistance.get());
                }
            }
            default -> {
                if (spearDistance.get() > 0.0D) {
                    spearDistance.set(-spearDistance.get());
                }
            }
        }
    }
}
