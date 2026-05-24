package me.matl114.hacks.modules.combat;

import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Criticals extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath attBot = makePath(Configs.COMBAT_CONFIG, "att-bot");
    public final ModulePath criticals = attBot.add("criticals");

    public final FlagRef enable = flagBuilder(criticals.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    criticals.add("hotkey"), new MultiKeyBind(), criticals.add("enable"), () -> this.mode
                            .get()
                            .getDisplay())
            .build();

    public final EnumRef<Mode> mode =
            builder(criticals.add("mode"), Mode.class).defaultValue(Mode.PACKET).build();

    public final FlagRef groundOnly = flagBuilder(criticals.add("ground-only")).build();

    public final FlagRef targetAround =
            flagBuilder(criticals.add("target-only")).build();

    public final FlagRef movementOk = flagBuilder(criticals.add("movement-ok")).build();

    public final FlagRef autoFakeGround = builder(criticals.add("auto-fake-ground-height"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoWalk = builder(criticals.add("auto-walk-resync"), Boolean.class)
            .defaultValue(true)
            .build();

    public final EnumRef<Configs.SetBackTriggerType> setBackType = builder(
                    criticals.add("set-back-mode"), Configs.SetBackTriggerType.class)
            .defaultValue(Configs.SetBackTriggerType.SIMULATION)
            .build();

    static LegalMovementManager.DelegateMovementModifier instance;

    public Criticals() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    // todo: wall critical

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::onPlayerAttack);
        registerListener(Listener.getPacketPoint().getChannel(HandSwingC2SPacket.class), this::onSwing);
        //        registerListener(
        //                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class),
        //                this::onTeleportConfirm2);
        //        registerListener(
        //            Listener.getPacketPostSendPoint().getChannel(TeleportConfirmC2SPacket.class),
        // this::onTeleportConfirm);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onTeleportConfirmPre);
        ;
    }

    public void onPlayerAttack(Event<PlayerInteractEntityC2SPacket> event) {
        if (checkNull() || event.isCancelled()) return;
        if (enable.get()
                && PlayerInteractEntityC2SPacketAccess.of(event.context).isAttack()
                && mc.world != null
                && mc.world.getEntityById(PlayerInteractEntityC2SPacketAccess.of(event.context)
                                .getEntityId())
                        instanceof LivingEntity lv) {
            handleCritical(event);
        }
    }

    int walkCnt = 0;
    boolean nextAttackIsKillarua = false;

    public void handleCritical(Event<PlayerInteractEntityC2SPacket> event) {
        // todo: handle wall critical, handle in wall critical
        var x = mc.player.getX();
        var y = mc.player.getY();
        var z = mc.player.getZ();
        // todo: handle sprint, handle inWater, handle condition
        switch (mode.get()) {
            case PACKET -> {
                // do not influence tp
                if (!CombatTasks.getAttack().canUseTp()) {
                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y + 5.0E-4, z, false, false));
                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y + 1.0E-4, z, false, false));
                }
            }
                //            case OLD_GRIM_V2 -> {
                //                if (mc.player.isOnGround()
                //                        && !PlayerInputUtils.of(mc.player.input).hasWASDMovement()) {
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y + 0.0625, z,
                // false, false));
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y, z, false,
                // false));
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y + 1.0E-7, z,
                // false, false));
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y, z, false,
                // false));
                //                }
                //            }
                //            case OLD_GRIM_V3 -> {
                //                if (mc.player.isOnGround()
                //                        && !PlayerInputUtils.of(mc.player.input).hasWASDMovement()) {
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y, z, true,
                // false));
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y + 0.0625, z,
                // false, false));
                //                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y + 0.04535,
                // z, false, false));
                //                }
                //            }
            case FREEZE -> {
                if (lastOnGroundT) {
                    mc.getNetworkHandler()
                            .sendPacket(VPacket.newLookAndOnGround(
                                    mc.player.getYaw(), mc.player.getPitch(), false, mc.player.horizontalCollision));
                }
            }
            case GRIM_GROUND_SIMULATION -> {
                if (mc.player.isOnGround()) {
                    if (!shouldApplyGrimGroundSimulationAutoFakeGround()) {
                        mc.getNetworkHandler()
                                .sendPacket(VPacket.newPositionAndOnGround(x, y + 5.0E-5, z, true, false));
                    }
                    // trigger simulation to sync our position from y + 1.0E-5 -> y, critical
                    switch (setBackType.get()) {
                        case CRASH_PACKETS -> {
                            mc.getNetworkHandler()
                                    .sendPacket(PlayerMoveC2SPacketAccess.setCause(
                                            VPacket.newPositionAndOnGround(
                                                    x, Double.POSITIVE_INFINITY, z, false, false),
                                            PlayerMoveC2SPacketAccess.Cause.TRIGGER_SIMULATION));
                        }
                        case SIMULATION -> {
                            mc.getNetworkHandler()
                                    .sendPacket(PlayerMoveC2SPacketAccess.setCause(
                                            VPacket.newPositionAndOnGround(x, y + 1, z, false, false),
                                            PlayerMoveC2SPacketAccess.Cause.TRIGGER_SIMULATION));
                        }
                    }
                    event.cancel();
                    cache = event.context;
                    cachedHandStack = mc.player.getStackInHand(Hand.MAIN_HAND).copy();
                    fakeMovementThisTick = true;
                }
            }
        }
    }

    public void onSwing(Event<HandSwingC2SPacket> eventSwing) {
        if (cache != null) {
            eventSwing.cancel();
        }
    }

    boolean fakeMovementThisTick;
    PlayerInteractEntityC2SPacket cache;
    ItemStack cachedHandStack;
    int setbackFlag = 0;
    //    public void onTeleportConfirm(Event<TeleportConfirmC2SPacket> event) {
    //        setbackFlag = 2;
    //        if (cache != null) {
    //            Listener.sendPacketNoEvents(cache);
    //            cache = null;
    //            mc.player.swingHand(Hand.MAIN_HAND);
    //        }
    //    }

    public void onTeleportConfirmPre(Event<PlayerMoveC2SPacket> event) {
        if (cache != null
                && event.context instanceof PlayerMoveC2SPacketAccess acc
                && acc.getCause() == PlayerMoveC2SPacketAccess.Cause.SET_BACK) {
            setbackFlag = 1;
            Entity entity = mc.world.getEntityById(
                    PlayerInteractEntityC2SPacketAccess.of(cache).getEntityId());
            var pkt0 = event.context;
            if (entity != null) {
                boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                        mc.player,
                        pkt0.getPitch(PlayerStateManager.INSTANCE.lastPitch),
                        pkt0.getYaw(PlayerStateManager.INSTANCE.lastYaw),
                        entity);
                if (canDirectlyHit) {
                    // escape rot
                } else {
                    Vec3d predictedEyePos = mc.player.getEyePos();
                    Vec3d eyePos = entity.getEyePos();
                    Vec3d cacheDirection = eyePos.subtract(predictedEyePos).normalize();
                    var py = EntityUtils.rotationToPitchYaw(cacheDirection);
                    acc.setPitch(py.x);
                    acc.setYaw(EntityUtils.getSafeYaw(mc.player, py.y));
                }
            }
            var pkt = cache;
            var stack = cachedHandStack;
            PacketManager.schedulePostCallback(event.context, () -> {
                var weapon = mc.player.getStackInHand(Hand.MAIN_HAND);
                Runnable callback = null;
                if (stack != null && !stack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(weapon, stack)) {
                    var res = InventoryUtils.findPlayerItem(
                            it -> ItemStack.areItemsAndComponentsEqual(it, stack), true, false);
                    if (res != null) {
                        callback = InvTasks.getInvExtra().swapInventoryIndexToHand(res.index());
                    }
                }
                Listener.sendPacketNoEvents(pkt);
                mc.player.swingHand(Hand.MAIN_HAND);
                if (callback != null) {
                    callback.run();
                }
            });
            cache = null;
            cachedHandStack = null;
        }
    }

    boolean lastFall = false;
    boolean lastOnGroundT = false;
    int fakeTicks = 0;
    // grim ground critical optimize

    public boolean shouldApplyCriticalConditionCheck() {
        return hasNoMovement() && hasTargetNear();
    }

    public boolean shouldApplyGrimGroundSimulationAutoFakeGround() {
        return (autoFakeGround.get() || nextAttackIsKillarua)
                && mc.player.isOnGround()
                && shouldApplyCriticalConditionCheck();
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {

        boolean lastLastOnGround = lastOnGroundT;
        lastOnGroundT = mc.player.isOnGround() && !movementManagerEvent.context.playerStatus.onGround;
        if (enable.get() && mode.get() == Mode.FREEZE && shouldApplyCriticalConditionCheck()) {
            boolean shouldApplyFreeze = false;
            if (groundOnly.get()) {
                shouldApplyFreeze = lastLastOnGround || lastOnGroundT;
                if (shouldApplyFreeze) {
                    lastOnGroundT = true;
                }
            } else {
                shouldApplyFreeze = mc.player.getY() < movementManagerEvent.context.playerStatus.pos.y && lastFall;
            }
            if (shouldApplyFreeze) {
                MovTasks.getFloatingUtils().setGrimFloatingTick(true);
                mc.player.setOnGround(false);
            }
        }
        lastFall = mc.player.getY() < movementManagerEvent.context.playerStatus.pos.y;
        // fake height
        if (setbackFlag > 0) {
            setbackFlag -= 1;
        }
        if (enable.get()
                && mode.get() == Mode.GRIM_GROUND_SIMULATION
                && shouldApplyGrimGroundSimulationAutoFakeGround()) {
            double yLevel = mc.player.getY();

            if (setbackFlag <= 0) {
                double newYLevel = (((int) (yLevel * 1E4)) * 1E-4) + (1.0E-5);
                mc.player.setPosition(mc.player.getPos().withAxis(Direction.Axis.Y, newYLevel));
                // ClientPlayerAccess.of(mc.player).resyncPos();
            }
        }

        if (fakeMovementThisTick) {
            movementManagerEvent.cancel();
            movementManagerEvent.context.playerStatus.restorePos();
            fakeMovementThisTick = false;
        }
    }

    public boolean hasTargetNear() {
        if (targetAround.get()) {
            var entity = CombatTasks.getTargetSelector()
                    .searchAttackEntity(CombatTasks.getCombatExtra().getAttackRange(), false);
            return entity != null;
        }
        return true;
    }

    public boolean hasNoMovement() {
        var re = PlayerInputUtils.of(mc.options);
        if (movementOk.get()) {
            return !re.jump() && !re.sneak();
        } else {
            return !re.hasMovement() && !re.sneak();
        }
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        nextAttackIsKillarua = CombatTasks.getAttack().delayAttacking;
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (setbackFlag > 0 && mode.get() == Mode.GRIM_GROUND_SIMULATION && autoWalk.get()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.player.input);
            if (!input.hasWASDMovement()) {
                walkCnt += 1;
                if (walkCnt % 2 == 0) {
                    input.left(true);
                } else {
                    input.right(true);
                }
                input.applyInput(mc.player.input);
            }
        }
        LegalMovementManager.MovementModifier.super.applyAfterInputTick(movementManagerEvent);
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }

    public static enum Mode implements ConfigEnum {
        PACKET,
        FREEZE,
        //        OLD_GRIM_V2,
        //        OLD_GRIM_V3,
        GRIM_GROUND_SIMULATION;

        @Override
        public String getConfigEnumType() {
            return "critical_mode";
        }
    }
}
