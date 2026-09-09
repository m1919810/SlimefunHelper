package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class FloatingUtils extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;
    public final ModulePath velocityManagement = makePath(Configs.MOV_CONFIG, "velocity-management");
    public final ModulePath floatingUtils = velocityManagement.add("floating-utils");
    public final ModulePath grimFloating = floatingUtils.add("grim-floating");
    public final ModulePath conditionalFreeze = floatingUtils.add("conditional-freeze");
    public final ModulePath elytraSlowFalling = floatingUtils.add("elytra-slow-falling");
    public static FloatingUtils INSTANCE;

    public FloatingUtils() {
        super("FloatingUtils");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        INSTANCE = this;
    }

    public final FlagRef enableGrim = flagBuilder(grimFloating.addEnable()).build();

    public final KeyBindRef hotkeyGrim = moduleEntry(
                    grimFloating.addHotkey(), new MultiKeyBind(), grimFloating.addEnable())
            .build();

    public final FlagRef onGroundFloat =
            flagBuilder(grimFloating.add("on-ground-float")).build();

    public final FlagRef useSnap =
            flagBuilder(grimFloating.add("use-snap-packet")).build();

    public final NBTRef<OptionalPrimitive<Double>> freezeVoid = builder(
                    conditionalFreeze.add("freeze-void"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, -2.0D))
            .build();

    public final NBTRef<OptionalPrimitive<Double>> freezeAbsoluteY = builder(
                    conditionalFreeze.add("freeze-absolute-y"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 256.0D))
            .build();

    public final FlagRef enableElytraSlowFall =
            flagBuilder(elytraSlowFalling.addEnable()).build();

    public final KeyBindRef hotkeySlowFall = moduleEntry(
                    elytraSlowFalling.addHotkey(), new MultiKeyBind(), elytraSlowFalling.addEnable())
            .build();

    boolean forceFloatingThisTick = false;
    boolean forceOnGroundVia1205 = false;
    boolean useSnapPacket = false;

    public void setGrimFloatingTick(boolean t) {
        forceFloatingThisTick = t;
    }

    public void setForceOnGroundVia(boolean t) {
        forceOnGroundVia1205 = t;
    }

    public void setForceSilent(boolean t) {
        forceSilentThisTick = t;
    }

    public void setSendPacketIgnoreRotation(boolean t) {
        forceNotSilentThisTick = t;
    }

    public void setUseSnapRotPacket(boolean t) {
        useSnapPacket = t;
    }

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public int priority() {
        return PRIORITY_MONITOR;
    }

    PlayerMoveC2SPacket storedPacket;
    // fix timer
    boolean forceSilentThisTick = false;
    boolean forceNotSilentThisTick = false;

    public boolean workGrimFloatingThisTick() {
        return (enableGrim.get() // && !mc.player.isOnGround()
                )
                || forceFloatingThisTick;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (!workGrimFloatingThisTick() && enableElytraSlowFall.get()) {
            if (mc.player.isFallFlying() && !mc.player.isOnGround()) {
                boolean rotateYaw = Tasks.getTick() % 2 == 0;
                movementManagerEvent.context.pushImportantRotation(true, rotateYaw);
                EntityUtils.setEntityPitchSafe(mc.player, 0);
                if (rotateYaw) {
                    PlayerStateManager.setPlayerYawSafe(mc.player, mc.player.getYaw() + 180);
                }
                movementManagerEvent.context.markForResetRot();
            }
        }
    }

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        if (workGrimFloatingThisTick()) {
            movementManagerEvent.cancel();
        }
    }

    boolean lastUsingOnGroundDeceive;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        boolean conditionalFreeze = false;
        if (!conditionalFreeze && freezeVoid.get().isPresent()) {
            if (mc.player.getY() <= mc.world.getBottomY() + freezeVoid.get().getValue()) {
                conditionalFreeze = true;
            }
        }
        if (!conditionalFreeze && freezeAbsoluteY.get().isPresent()) {
            if (mc.player.getY() <= freezeAbsoluteY.get().getValue()) {
                conditionalFreeze = true;
            }
        }
        if (conditionalFreeze) {
            forceFloatingThisTick = true;
        }

        if (workGrimFloatingThisTick()) {
            movementManagerEvent.cancel();
            movementManagerEvent.context.playerStatus.restorePos();
            // also reset onground status to avoid false flag
            boolean useOnGroundFloat = ((onGroundFloat.get()) || (forceOnGroundVia1205));
            if (useOnGroundFloat) {
                storedPacket =
                        LegacySnapRotManager.INSTANCE.createSnapAt(mc.player.getPitch(), mc.player.getYaw(), true);
                mc.player.setOnGround(true);
                lastUsingOnGroundDeceive = true;
            } else {
                if (lastUsingOnGroundDeceive) {
                    lastUsingOnGroundDeceive = false;
                    storedPacket = LegacySnapRotManager.INSTANCE.createSnapAt(
                            mc.player.getPitch(), mc.player.getYaw(), PlayerStateManager.INSTANCE.lastHasGroundSupport);
                } else {
                    storedPacket = VPacket.newLookAndOnGround(
                            mc.player.getYaw(),
                            mc.player.getPitch(),
                            mc.player.isOnGround(),
                            mc.player.horizontalCollision);
                    if (useSnapPacket) {
                        storedPacket = LegacySnapRotManager.INSTANCE.createAsSnap(storedPacket);
                    }
                }
            }
        } else {
            lastUsingOnGroundDeceive = false;
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        forceFloatingThisTick = false;
        forceOnGroundVia1205 = false;

        useSnapPacket = useSnap.get();
        if (storedPacket != null) {
            // optimize current, only if rotation different, or has No Position packet, send duplicate packet
            if (!forceSilentThisTick) {
                boolean shouldSend = false;
                if (forceNotSilentThisTick) {
                    shouldSend = true;
                } else if (PlayerStateManager.INSTANCE.isRotationDifferent(
                        storedPacket.getPitch(mc.player.getPitch()), storedPacket.getYaw(mc.player.getYaw()))) {
                    shouldSend = true;
                }
                if (shouldSend) {
                    mc.getNetworkHandler().sendPacket(storedPacket);
                }
            }
            // Listener.sendPacketNoEvents(storedPacket);
            storedPacket = null;
        }
        forceSilentThisTick = false;
        forceNotSilentThisTick = false;
        return true;
    }
}
