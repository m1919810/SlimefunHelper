package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class FloatingUtils extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;
    public final ModulePath velocityManagement = makePath(Configs.MOV_CONFIG, "velocity-management");
    public final ModulePath floatingUtils = velocityManagement.add("floating-utils");
    public final ModulePath grimFloating = floatingUtils.add("grim-floating");
    public static FloatingUtils INSTANCE;

    public FloatingUtils() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enableGrim);
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

    public void setUseSnapRotPacket(boolean t) {
        useSnapPacket = t;
    }

    @Override
    public void registerAll() {
        super.registerAll();

        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onMoveNoPosition);
    }

    @Override
    public int priority() {
        return PRIORITY_MONITOR;
    }

    PlayerMoveC2SPacket storedPacket;
    // fix timer
    boolean hasNoPositionMovementPacketThisTick = false;
    boolean forceSilentThisTick = false;

    public void onMoveNoPosition(Event<PlayerMoveC2SPacket> eventMove) {
        // FIX: legacy snap seen as noPosition
        if (!eventMove.isCancelled() && (!eventMove.context.changesPosition())) {
            hasNoPositionMovementPacketThisTick = true;
        }
    }

    public boolean workGrimFloatingThisTick() {
        return (enableGrim.get() // && !mc.player.isOnGround()
                )
                || forceFloatingThisTick;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    boolean lastUsingOnGroundDeceive;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
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
            if (!forceSilentThisTick
                    && ((!hasNoPositionMovementPacketThisTick
                            || PlayerStateManager.INSTANCE.isRotationDifferent(
                                    storedPacket.getPitch(mc.player.getPitch()),
                                    storedPacket.getYaw(mc.player.getYaw()))))) {
                mc.getNetworkHandler().sendPacket(storedPacket);
            }
            // Listener.sendPacketNoEvents(storedPacket);
            storedPacket = null;
        }
        forceSilentThisTick = false;
        hasNoPositionMovementPacketThisTick = false;
        return true;
    }
}
