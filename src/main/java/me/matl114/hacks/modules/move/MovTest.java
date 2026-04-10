package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ExtraTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class MovTest extends BaseModule implements LegalMovementManager.MovementModifier {
    public static LegalMovementManager.DelegateMovementModifier instance;

    public MovTest() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public boolean enable() {
        return ExtraTasks.getTests().flag4.get();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        // registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onJump);
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onSetback);
    }

    @Override
    public int priority() {
        // the least important shit
        return 10000000;
    }

    @Override
    public boolean mayModifyRotation() {
        return false;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;

        if (enable()) {

        } else {
            step = null;
        }
    }

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
        if (step == Step.WALK) {
            //            PlayerInputUtils.of(mc.player.input)
            //                .forward(true)
            //                .jump(true)
            //                .applyInput(mc.player.input);
            //            mc.player.setOnGround(true);
        }
    }

    Step step;
    int lastOnGround;
    Vec3d storePos;
    boolean runOnGroundThisTick;
    int sleep = 0;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enable()) {

            var entity = movementManagerEvent.context.playerStatus;
            ClientPlayerEntity player = entity.entity;
            if (step == Step.SLEEP && sleep++ > 20) {
                step = null;
                sleep = 0;
                player.setOnGround(true);
                step = Step.ON_GROUND_1;
            }
            if (step == null) {
                if (!player.isOnGround()) {
                    step = Step.ON_GROUND_1;
                }
            }
            if (step != null) {
                if (step == Step.SLEEP) {
                    movementManagerEvent.context.playerStatus.restorePos();
                    movementManagerEvent.cancel();
                    Listener.sendPacketNoEvents(VPacket.newFull(
                            mc.player.getX(),
                            mc.player.getY() + 9e-8,
                            mc.player.getZ(),
                            mc.player.getYaw(),
                            mc.player.getPitch(),
                            true,
                            mc.player.horizontalCollision));
                    player.setOnGround(true);
                } else if (step == Step.ON_GROUND_1) {
                    // mc.player.setPosition(storePos.x, yLevel, storePos.z);
                    storePos = mc.player.getPos();
                    movementManagerEvent.context.playerStatus.restorePos();
                    movementManagerEvent.cancel();
                    player.setOnGround(true);

                    Listener.sendPacketNoEvents(VPacket.newFull(
                            mc.player.getX(),
                            mc.player.getY() + 9e-8,
                            mc.player.getZ(),
                            mc.player.getYaw(),
                            mc.player.getPitch(),
                            true,
                            mc.player.horizontalCollision));
                    step = Step.ON_GROUND_2;
                } else if (step == Step.ON_GROUND_2) {
                    storePos = mc.player.getPos();
                    movementManagerEvent.context.playerStatus.restorePos();
                    movementManagerEvent.cancel();
                    player.setOnGround(true);

                    Listener.sendPacketNoEvents(VPacket.newFull(
                            mc.player.getX(),
                            mc.player.getY() + 9e-8,
                            mc.player.getZ(),
                            mc.player.getYaw(),
                            mc.player.getPitch(),
                            true,
                            mc.player.horizontalCollision));
                    step = Step.WALK;
                } else if (step == Step.WALK) {
                    player.setOnGround(true);
                    movementManagerEvent.cancel();
                    mc.player.setPosition(
                            mc.player.getX(), movementManagerEvent.context.playerStatus.pos.y, mc.player.getZ());
                    Listener.sendPacketNoEvents(VPacket.newFull(
                            mc.player.getX(),
                            mc.player.getY() + 9e-8,
                            mc.player.getZ(),
                            mc.player.getYaw(),
                            mc.player.getPitch(),
                            true,
                            mc.player.horizontalCollision));
                    step = Step.SLEEP;
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (runOnGroundThisTick) {
            mc.player.setPosition(storePos.x, mc.player.getY(), storePos.z);
            mc.player.setOnGround(true);
            mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, 0));
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
