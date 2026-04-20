package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.Packet;

public class ElytraGrimAccelerate extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraGrimAccelerate() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
    }

    public static final String[] ENABLE = makePath("elytra.elytra-flight-legit.grim-accelerate.enable");

    public static final String[] HOTKEY = makePath("elytra.elytra-flight-legit.grim-accelerate.hotkey");

    public final FlagRef enable = flagBuilder(Configs.MOV_CONFIG, ENABLE).build();

    public final KeyBindRef hotkey =
            toggleHotkey(Configs.MOV_CONFIG, HOTKEY, new MultiKeyBind(), ENABLE).build();

    public Packet<?> storedPacket = null;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> preTickEvent) {}

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> sendMovementPacketEvent) {
        if (enable.get()
                && mc.player.isFallFlying()
                && !mc.player.isOnGround()
                && !MovTasks.getElytraExtra().canFireworkControlMotion()) {
            sendMovementPacketEvent.context().playerStatus.restorePos();
            sendMovementPacketEvent.cancel();
            storedPacket = VPacket.newFull(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    mc.player.getYaw(),
                    mc.player.getPitch(),
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> postTickEvent, boolean enabledThisTick) {
        if (storedPacket != null) {
            mc.getNetworkHandler().sendPacket(storedPacket);
            storedPacket = null;
        }
        return true;
    }
}
