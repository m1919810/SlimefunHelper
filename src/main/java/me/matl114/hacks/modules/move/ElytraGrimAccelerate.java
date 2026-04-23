package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.util.math.Vec3d;

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

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(TeleportConfirmC2SPacket.class), this::onSetBackReceive);
    }

    public Packet<?> storedPacket = null;
    int setBack = 0;
    int setBackCount = 0;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> preTickEvent) {}

    public void onSetBackReceive(Event<TeleportConfirmC2SPacket> packet) {
        setBack = Tasks.getTick();
        setBackCount++;
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> sendMovementPacketEvent) {
        if (enable.get()
                && mc.player.isFallFlying()
                && !mc.player.isOnGround()
                && !MovTasks.getElytraExtra().canFireworkControlMotion()) {
            Vec3d playerPos = mc.player.getPos();
            sendMovementPacketEvent.context().playerStatus.restorePos();
            sendMovementPacketEvent.cancel();
            boolean timeout = setBack + 20 < Tasks.getTick();
            storedPacket = VPacket.newFull(
                    sendMovementPacketEvent.context.playerStatus.pos.x,
                    sendMovementPacketEvent.context.playerStatus.pos.y
                            + 0.25 * ((Tasks.getTick() % 3) + 1), // - 20 * ((Tasks.getTick() % 2) +1 ),
                    sendMovementPacketEvent.context.playerStatus.pos.z,
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
