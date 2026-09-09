package me.matl114.hacks.modules.move;

import java.util.Random;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class ElytraGrimAccelerate extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;
    public final ModulePath elytra = makePath(Configs.MOV_CONFIG, "elytra");
    public final ModulePath elytraFlightLegit = elytra.add("elytra-flight-legit");
    public final ModulePath grimAccelerate = elytraFlightLegit.add("grim-accelerate");
    public static ElytraGrimAccelerate INSTANCE;

    public ElytraGrimAccelerate() {
        super("ElytraGrimAcc");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(grimAccelerate.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    grimAccelerate.add("hotkey"), new MultiKeyBind(), grimAccelerate.add("enable"))
            .build();

    public final EnumRef<Configs.SetBackTriggerType> mode = builder(
                    grimAccelerate.add("set-back-mode"), Configs.SetBackTriggerType.class)
            .defaultValue(Configs.SetBackTriggerType.SIMULATION)
            .build();

    public final DoubleRef maxVelocityAccept = builder(grimAccelerate.add("max-accelerate-velocity"), Double.class)
            .defaultValue(6.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();

    public final FlagRef fixOldVersionVelocityShit =
            flagBuilder(grimAccelerate.add("fix-old-version-velocity-shit")).build();

    public final FlagRef fixKickFromLag = builder(grimAccelerate.add("fix-kick-from-lag"), Boolean.class)
            .defaultValue(true)
            .build();

    //    public final DoubleRef mn

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(TeleportConfirmC2SPacket.class), this::onSetBackReceive);
        registerListener(Listener.getPacketPoint().getChannel(EntityVelocityUpdateS2CPacket.class), this::onVcUpdate);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class),
                this::onTeleportConfirm);
    }

    public Packet<?> storedPacket = null;
    int setBackCount = 0;
    int lastSendMoveAndWaitSetBackTick = 0;
    Vec3d lastVelocity;

    int lastVelocityTick = 0;
    boolean currentVelocityRevert = false;

    public void onVcUpdate(Event<EntityVelocityUpdateS2CPacket> event) {

        // todo: fix it
        if (mc.player != null && event.context.getEntityId() == mc.player.getId()) {
            // Debug.chat("Accept velocity", velocity, Tasks.getTick());
            if (lastWorkingTick + 10 > Tasks.getTick()) {}
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

    int lastWorkingTick = 0;

    public void onSetBackReceive(Event<TeleportConfirmC2SPacket> packet) {
        setBackCount++;
        lastSendMoveAndWaitSetBackTick = 0;
    }

    public void setTryWorkingTick() {
        currentTryWorking = true;
    }

    boolean currentTryWorking = false;
    boolean currentWorking = false;

    public void onTeleportConfirm(Event<PlayerPositionLookS2CPacket> event) {}

    private void createStorePacket() {
        // fix chunk lag
        if (fixKickFromLag.get() && AntiChunkLag.INSTANCE.currentMayFaceLagChunk) {
            return;
        }
        // check response, do not spam
        if (fixKickFromLag.get() && Tasks.getTick() < lastSendMoveAndWaitSetBackTick + 20) {
            return;
        }
        switch (mode.get()) {
            case SIMULATION -> {
                storedPacket = VPacket.newFull(
                        mc.player.getX(),
                        mc.player.getY() + 2.5 * ((Tasks.getTick() % 3) + 1), // - 20 * ((Tasks.getTick() % 2) +1 ),
                        mc.player.getZ(),
                        mc.player.getYaw(),
                        mc.player.getPitch(),
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision);
            }
            case CRASH_PACKETS -> {
                storedPacket = VPacket.newFull(
                        3.9999999E7D,
                        mc.player.getY() + 2.5 * ((Tasks.getTick() % 3) + 1), // - 20 * ((Tasks.getTick() % 2) +1 ),
                        3.9999999E7D,
                        mc.player.getYaw(),
                        mc.player.getPitch(),
                        true,
                        mc.player.horizontalCollision);
            }
        }
        PlayerMoveC2SPacketAccess.setCause(
                (PlayerMoveC2SPacket) storedPacket, PlayerMoveC2SPacketAccess.Cause.TRIGGER_SIMULATION);
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> preTickEvent) {
        boolean enable = this.enable.get();
        if (enable && fixOldVersionVelocityShit.get()) {
            Vec3d velocity = PlayerStateManager.INSTANCE.lastKnownMovementSpeed;
            if (Math.abs(velocity.x) >= 3.8 || Math.abs(velocity.z) >= 3.8) {
                enable = false;
            }
        }
        currentTryWorking = (enable || currentTryWorking)
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
            Vec3d velocity = PlayerStateManager.INSTANCE.lastKnownChangePosMovementSpeed;
            double speed = velocity.length();

            if (speed > maxVelocityAccept.get()) {
                if (fixOldVersionVelocityShit.get() && Math.abs(velocity.x) >= 3.8 || Math.abs(velocity.z) >= 3.8) {
                    currentWorking = true;
                } else {
                    currentWorking = false;
                }
            } else {
                currentWorking = true;
            }
            if (enable.get()
                    && fixOldVersionVelocityShit.get()
                    && (Math.abs(velocity.x) >= 3.8 || Math.abs(velocity.z) >= 3.8)) {
                currentWorking = false;
            }

            if (FloatingUtils.INSTANCE.workGrimFloatingThisTick()) {
                currentWorking = false;
            }
            if (currentWorking) {
                sendMovementPacketEvent.context().playerStatus.restorePos();
                sendMovementPacketEvent.cancel();
                if (storedPacket != null) {
                    storedPacket = null;
                    return;
                }
                // if no setback within a tick, then create one
                createStorePacket();
            }
        } else {
            currentWorking = false;
        }
    }

    Random rand = new Random();

    @Override
    public boolean postModify(Event<LegalMovementManager> postTickEvent, boolean enabledThisTick) {
        if (FloatingUtils.INSTANCE.workGrimFloatingThisTick()) {
            storedPacket = null;
        }
        if (storedPacket != null) {
            // mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(-rand.nextInt(0, Integer.MAX_VALUE - 1)));
            mc.getNetworkHandler().sendPacket(storedPacket);
            lastSendMoveAndWaitSetBackTick = Tasks.getTick();
            storedPacket = null;
        }
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
