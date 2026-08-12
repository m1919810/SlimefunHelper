package me.matl114.hacks.modules.move;

import java.util.ArrayDeque;
import java.util.Deque;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

public class MovTest extends BaseModule implements LegalMovementManager.MovementModifier {
    public static LegalMovementManager.DelegateMovementModifier instance;

    public MovTest() {
        super("MovTest");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public boolean enable() {
        return false;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        // registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onJump);
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onSetback);
        registerListener(Listener.getPacketPoint().getChannel(CommonPingS2CPacket.class), this::onTransaction);
        registerListener(
                Listener.getPacketPoint().getChannel(EntityVelocityUpdateS2CPacket.class), this::onVelocityPacket);
    }

    @Override
    public int priority() {
        // the least important shit
        return 10000000;
    }

    public void onTransaction(Event<CommonPingS2CPacket> event) {
        if (enable()) {
            //            delayedPackets.add(event.context());
            //            event.cancel();
        }
    }

    public void onVelocityPacket(Event<EntityVelocityUpdateS2CPacket> event) {
        if (enable() && !checkNull() && event.context.getEntityId() == mc.player.getId()) {
            //            if(veryBigVelocity == null || (veryBigVelocity.getVelocity().lengthSquared() <
            // event.context.getVelocity().lengthSquared())){
            //                veryBigVelocity = event.context;
            //            }
            //            delayedPackets.add(event.context);
            //            event.cancel();
        }
    }

    Deque<EntityVelocityUpdateS2CPacket> delayedPackets = new ArrayDeque<>();

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    public void onSetback(Event<MovTasks.MovInfo> setBack) {
        //            if(Tasks.getTick() < lastOnGround + 5){
        //                MovTasks.MovInfo set = setBack.context();
        //                setBack.context(set.withOGroundOverride(Boolean.TRUE));
        //                Debug.chat("OnGround");
        //                lastOnGround = 0;
        //            }
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (!enable()) {
            EntityVelocityUpdateS2CPacket packet;
            //            while ((packet = delayedPackets.poll()) != null){
            //                Vec3d velocity = packet.getVelocity();
            //                if(velocity.lengthSquared() > mc.player.getVelocity().lengthSquared()){
            //                    Debug.chat("Use cached Velocity");
            //                    mc.player.setVelocity(velocity);
            //                }
            //            }
        }
    }

    Step step;
    int lastOnGround;
    Vec3d storePos;
    boolean runOnGroundThisTick;
    int sleep = 0;
    Packet<?> storedPacket = null;
    Deque<Vec3d> posDeque = new ArrayDeque<>();

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enable()) {
            movementManagerEvent.cancel();
            movementManagerEvent.context.playerStatus.restorePos();
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
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (storedPacket != null) {
            Listener.sendPacketNoEvents(storedPacket);
            storedPacket = null;
        }
        if (enable()) {
            if (posDeque == null) {
                posDeque = new ArrayDeque<>();
            }
            posDeque.add(mc.player.getPos());
            Vec3d last19Vec3d = null;
            // >= 21,
            while (posDeque.size() > 20) {
                last19Vec3d = posDeque.removeFirst();
            }
            if (last19Vec3d != null) {
                Debug.chat(
                        "Speed last one sec :",
                        mc.player.getPos().subtract(last19Vec3d).length());
            }
        } else {
            if (posDeque != null) {
                posDeque.clear();
                posDeque = null;
            }
        }
        return true;
    }

    public static enum Step {
        ON_GROUND_1,
        ON_GROUND_2,
        SLEEP,
        WALK;
    }
}
