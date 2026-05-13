package me.matl114.hacks.modules.move;

import java.util.Random;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

public class ElytraGrimAccelerate extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraGrimAccelerate() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public static final String[] ENABLE = makePath("elytra.elytra-flight-legit.grim-accelerate.enable");

    public static final String[] HOTKEY = makePath("elytra.elytra-flight-legit.grim-accelerate.hotkey");

    public final FlagRef enable = flagBuilder(Configs.MOV_CONFIG, ENABLE).build();

    public final KeyBindRef hotkey =
            toggleHotkey(Configs.MOV_CONFIG, HOTKEY, new MultiKeyBind(), ENABLE).build();

    public final EnumRef<Configs.SetBackTriggerType> mode = builder(
                    Configs.MOV_CONFIG,
                    makePath("elytra.elytra-flight-legit.grim-accelerate.set-back-mode"),
                    Configs.SetBackTriggerType.class)
            .defaultValue(Configs.SetBackTriggerType.SIMULATION)
            .build();

    public final DoubleRef maxVelocityAccept = builder(
                    Configs.MOV_CONFIG,
                    makePath("elytra.elytra-flight-legit.grim-accelerate.max-accelerate-velocity"),
                    Double.class)
            .defaultValue(4.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();

    public final DoubleRef minVelocityAccept = builder(
                    Configs.MOV_CONFIG,
                    makePath("elytra.elytra-flight-legit.grim-accelerate.min-accelerate-velocity"),
                    Double.class)
            .defaultValue(3.6D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();

    //    public final DoubleRef mn

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(TeleportConfirmC2SPacket.class), this::onSetBackReceive);
        registerListener(Listener.getPacketPoint().getChannel(EntityVelocityUpdateS2CPacket.class), this::onVcUpdate);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
    }

    public Packet<?> storedPacket = null;
    int setBack = 0;
    int setBackCount = 0;
    int exemptTicks = 0;

    public void onVcUpdate(Event<EntityVelocityUpdateS2CPacket> event) {
        Vec3d velocity = event.context.getVelocity();
        if (mc.player != null && event.context.getEntityId() == mc.player.getId()) {

            if ((enable.get() && mc.player.isFallFlying()) || lastWorkingTick + 10 > Tasks.getTick()) {
                if (mc.player.isFallFlying()) {
                    if (velocity.horizontalLengthSquared() < 1E-4) {
                        event.cancel();
                    } else {
                        Vec3d vec3d = mc.player.getVelocity();
                        if (vec3d.horizontalLengthSquared() > 1E-4
                                && vec3d.getHorizontal().dotProduct(velocity.getHorizontal()) < 0.0) {
                            event.cancel();
                        }
                    }
                }
            }
        }

        //        if(enable.get() && mc.player != null && event.context.getEntityId() == mc.player.getId() &&
        // mc.player.isFallFlying()){
        //            Debug.chat("VC update" + event.context.getVelocity().length());
        //            Vec3d vec3d = event.context.getVelocity();
        //            if(vec3d.lengthSquared() < 1E-6){
        //                event.cancel();
        //                return;
        //            }
        //            if(exemptTicks > 0){
        //                event.cancel();
        //            }else
        //            if(vec3d.lengthSquared() > 1E-6 && vec3d.lengthSquared() > mc.player.getVelocity().lengthSquared()
        // && vec3d.dotProduct(mc.player.getRotationVector()) > 0){
        //                exemptTicks = 1;
        //            }
        //        }
    }

    boolean receiveSetBackTick = false;
    int lastWorkingTick = 0;

    public void onSetBackReceive(Event<TeleportConfirmC2SPacket> packet) {
        setBack = Tasks.getTick();
        setBackCount++;
        receiveSetBackTick = true;
    }

    public void setTryWorkingTick() {
        currentTryWorking = true;
    }

    boolean currentTryWorking = false;
    boolean currentWorking = false;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> preTickEvent) {
        currentTryWorking |= enable.get()
                && mc.player.isFallFlying()
                && !mc.player.isOnGround()
                && !MovTasks.getElytraExtra().canFireworkControlMotion();
        if (currentTryWorking) {
            lastWorkingTick = Tasks.getTick();
        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> sendMovementPacketEvent) {
        if (currentTryWorking) {

            Vec3d velocity = mc.player.getVelocity();
            double speed = velocity.horizontalLength();
            if (currentWorking) {
                if (speed > maxVelocityAccept.get()) {
                    currentWorking = false;
                }
            } else {
                if (speed < minVelocityAccept.get()) {
                    currentWorking = true;
                }
            }
            if (currentWorking) {
                sendMovementPacketEvent.context().playerStatus.restorePos();
                sendMovementPacketEvent.cancel();
                boolean timeout = setBack + 20 < Tasks.getTick();
                // mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                // ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                switch (mode.get()) {
                    case SIMULATION -> {
                        storedPacket = VPacket.newFull(
                                sendMovementPacketEvent.context.playerStatus.pos.x,
                                sendMovementPacketEvent.context.playerStatus.pos.y
                                        + 2.5 * ((Tasks.getTick() % 3) + 1), // - 20 * ((Tasks.getTick() % 2) +1 ),
                                sendMovementPacketEvent.context.playerStatus.pos.z,
                                mc.player.getYaw(),
                                mc.player.getPitch(),
                                mc.player.isOnGround(),
                                mc.player.horizontalCollision);
                    }
                    case CRASH_PACKETS -> {
                        storedPacket = VPacket.newFull(
                                3.9999999E7D,
                                sendMovementPacketEvent.context.playerStatus.pos.y
                                        + 2.5 * ((Tasks.getTick() % 3) + 1), // - 20 * ((Tasks.getTick() % 2) +1 ),
                                Double.NEGATIVE_INFINITY,
                                mc.player.getYaw(),
                                mc.player.getPitch(),
                                true,
                                mc.player.horizontalCollision);
                    }
                }
            }
        }
    }

    Random rand = new Random();

    @Override
    public boolean postModify(Event<LegalMovementManager> postTickEvent, boolean enabledThisTick) {
        if (storedPacket != null) {
            // mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(-rand.nextInt(0, Integer.MAX_VALUE - 1)));
            mc.getNetworkHandler().sendPacket(storedPacket);
            storedPacket = null;
        }
        receiveSetBackTick = false;
        currentTryWorking = false;
        return true;
    }

    //    public static enum Mode implements ConfigEnum{
    //        SIMULATION,
    //        BAD_PACKETS;
    //
    //        @Override
    //        public String getConfigEnumType() {
    //            return "elytra_accelerate_mode";
    //        }
    //
    //        @Override
    //        public Text getDisplay() {
    //            return Text.translatable(
    //                "configenum.elytra-accelerate-mode." + this.name().toLowerCase(Locale.ROOT));
    //        }
    //    }
}
