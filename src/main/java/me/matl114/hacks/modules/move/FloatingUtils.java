package me.matl114.hacks.modules.move;

import java.util.ArrayDeque;
import java.util.Deque;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;

public class FloatingUtils extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public FloatingUtils() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
    }

    public static final String[] ENABLE_GRIM = makePath("velocity-management.floating-utils.grim-floating.enable");

    public static final String[] HOTKEY_GRIM = makePath("velocity-management.floating-utils.grim-floating.hotkey");

    public static final String[] ENABLE_ELYTRA =
            makePath("velocity-management.floating-utils.elytra-slow-falling.enable");

    public static final String[] HOTKEY_ELYTRA =
            makePath("velocity-management.floating-utils.elytra-slow-falling.hotkey");

    public final FlagRef enableGrim = flagBuilder(Configs.MOV_CONFIG, ENABLE_GRIM)
            .updateListener(this::onToggleGrimFloat)
            .build();

    public final KeyBindRef hotkeyGrim = toggleHotkey(Configs.MOV_CONFIG, HOTKEY_GRIM, new MultiKeyBind(), ENABLE_GRIM)
            .build();

    public final FlagRef enableElytraSlowFall =
            flagBuilder(Configs.MOV_CONFIG, ENABLE_ELYTRA).build();

    public final KeyBindRef hotkeySlowFall = toggleHotkey(
                    Configs.MOV_CONFIG, HOTKEY_ELYTRA, new MultiKeyBind(), ENABLE_ELYTRA)
            .build();

    public Deque<Packet<?>> delayedPackets = new ArrayDeque<>();

    public void onToggleGrimFloat(boolean val) {
        if (val) {
            delayedPackets.clear();
        } else {
            // false
            if (!delayedPackets.isEmpty()) {
                for (var packet : delayedPackets) {
                    try {
                        ((Packet) packet).apply(mc.getNetworkHandler());
                    } catch (Exception gi) {
                    }
                }
            }
        }
    }

    boolean forceFloatingThisTick = false;

    public void setGrimFloatingTick(boolean t) {
        forceFloatingThisTick = true;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(CommonPingS2CPacket.class), this::onTransactionPacket);
    }

    Packet<?> storedPacket;

    public void onTransactionPacket(Event<CommonPingS2CPacket> packet) {
        if (false && enableGrim.get()) {
            delayedPackets.add(packet.context());
            packet.cancel();
        }
    }

    public boolean workGrimFloatingThisTick() {
        return enableGrim.get() || forceFloatingThisTick;
    }

    boolean workElytraRotateThisTick = false;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enableElytraSlowFall.get()) {
            if (mc.player.isFallFlying() && !mc.player.isOnGround()) {
                workElytraRotateThisTick = true;
                boolean rotateYaw = Tasks.getTick() % 2 == 0;
                movementManagerEvent.context.pushImportantRotation(true, rotateYaw);
                EntityUtils.setEntityPitchSafe(mc.player, 0);
                if (rotateYaw) {
                    EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() + 180);
                }
            }
        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (workGrimFloatingThisTick() && !mc.player.isOnGround()) {
            movementManagerEvent.cancel();
            movementManagerEvent.context.playerStatus.restorePos();
            storedPacket = VPacket.newLookAndOnGround(
                    mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (workElytraRotateThisTick) {
            workElytraRotateThisTick = false;
            movementManagerEvent.context.playerStatus.restoreRotation();
        }
        forceFloatingThisTick = false;

        if (storedPacket != null) {
            mc.getNetworkHandler().sendPacket(storedPacket);
            // Listener.sendPacketNoEvents(storedPacket);
            storedPacket = null;
        }
        return true;
    }
}
