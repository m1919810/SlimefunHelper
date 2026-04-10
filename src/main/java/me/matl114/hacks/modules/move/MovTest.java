package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ExtraTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
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
        registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onJump);
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

    public Step step;
    private static final int latency = 5;

    public void onSetback(Event<MovTasks.MovInfo> setBack) {
        if (enable()) {
            Vec3d nowV3d = setBack.context().vec3d();
            if (lastStartWaitPos != null
                    && lastStartWaitPos.squaredDistanceTo(nowV3d) < 3
                    && Tasks.getTick() < lastStartWaitResyncTick + latency) {
                // accept
                lastStartWaitPos = null;
                lastStartWaitAcceptPos = nowV3d;
                lastStartWaitAcceptTick = Tasks.getTick();
                step = Step.WAIT_RESYNC;
            }
        }
    }

    int lastStartWaitResyncTick = 0;
    Vec3d lastStartWaitPos = null;
    boolean afterSetbackFlag;
    Vec3d lastStartWaitAcceptPos;
    int lastStartWaitAcceptTick = 0;
    PlayerInputUtils.Input lastCacheInput;

    @Override
    public boolean mayModifyPos() {
        return false;
    }

    //    List<BlockState> blocks;
    //    BlockPos lastPosCenter;
    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    //    boolean thisTickJump = false;
    public void onJump(Event<Integer> event) {
        lastJump = Tasks.getTick();
        if (applyJumpThisTick) {
            event.context(0);
        }
    }

    public static enum Step {
        COMMON,
        WAIT_RESYNC,
        APPLY_JUMP;
    }
    //
    //    @Override
    //    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
    //        if(thisTickJump){
    //            thisTickJump = false;
    //            movementManagerEvent.cancel();
    //        }
    //    }

    boolean applyJumpThisTick = false;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {

        // do not make velocity input
        // Debug.info("check input");
        ClientPlayerEntity entity = movementManagerEvent.context.playerStatus.entity;
        var input = PlayerInputUtils.of(entity.input);
        if (step == Step.WAIT_RESYNC) {
            //
            // Debug.chat("Wait Resync op");
            // calculate which way is ok,
            if (lastStartWaitResyncTick + latency * 2 >= Tasks.getTick()) {
                if (lastStartWaitAcceptPos != null && Tasks.getTick() <= lastStartWaitAcceptTick + 2) {
                    step = Step.APPLY_JUMP;
                    // Debug.chat("Apply jump " + lastStartWaitAcceptPos);
                    lastStartWaitPos = lastStartWaitAcceptPos;
                    lastStartWaitResyncTick = Tasks.getTick();
                    lastStartWaitAcceptPos = null;
                    mc.player.setOnGround(true);
                    // make some horizontal movement to avoid duplicate resync
                    input.clone()
                            .jump(true)
                            .left(false)
                            .right(false)
                            .forward(true)
                            .backward(false)
                            .applyInput(entity.input);
                    applyJumpThisTick = true;
                } else {
                    // Debug.chat("Apply Input");

                    if (lastCacheInput != null) {
                        lastCacheInput
                                .clone()
                                .forward(true)
                                .jump(true)
                                .sprint(input.sprint())
                                .applyInput(entity.input);
                        applyJumpThisTick = true;
                    }
                }
            } else {
                // Debug.chat("Timeout");
                step = Step.COMMON;
            }

        } else if (step == Step.APPLY_JUMP) {

            step = Step.COMMON;
        }
    }

    public int lastJump;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {

        if (applyJumpThisTick) {
            applyJumpThisTick = false;
        }
        if (enable()) {
            var entity = movementManagerEvent.context.playerStatus;
            // check Y after fall
            if (step == Step.APPLY_JUMP) {
                // common movement
                step = Step.COMMON;
            } else {
                //                    if (lastNoFallPos != null) {
                //                        // near
                //                        if (Math.abs(lastNoFallPos.y - entity.entity.getY()) < 1e-2
                //                            && entity.entity.getPos().squaredDistanceTo(lastNoFallPos) < 1
                //                            && lastNoFall + latency >= Tasks.getTick()) {
                //                            step = Step.HANDLE_RESYNC;
                //                        }
                //                    }
                if (step == Step.COMMON || step == null) {
                    boolean shouldCheck = lastJump + 6 == Tasks.getTick();
                    if (shouldCheck) {
                        if (true) {

                            afterSetbackFlag = false;

                            // ClientTickEndC2SPacket());
                            Vec3d lastPosPos = movementManagerEvent.context.playerStatus.pos;
                            //                            storedPacketMove =
                            //                                VPacket.newPositionAndOnGround(
                            //                                lastPosPos.x, lastPosPos.y + 9E-8, lastPosPos.z, false,
                            // entity.horizontalCollision
                            //                            );
                            storedPacketMove = VPacket.newOnGroundOnly(true, entity.horizontalCollision);

                            movementManagerEvent.cancel();
                            lastStartWaitPos = mc.player.getPos();
                            lastStartWaitResyncTick = Tasks.getTick();
                            mc.player.setPosition(movementManagerEvent.context.playerStatus.pos.withAxis(
                                    Direction.Axis.Y, mc.player.getY()));
                            step = Step.WAIT_RESYNC;
                            mc.player.setOnGround(true);
                            lastCacheInput = PlayerInputUtils.of(mc.player.input);
                            // idk
                            return;
                        }
                    }
                } else if (step == Step.WAIT_RESYNC) {
                    // movementManagerEvent.cancel();
                }
            }
        }
    }

    Packet<?> storedPacketMove = null;

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (storedPacketMove != null) {
            mc.player.setOnGround(true);
            mc.getNetworkHandler().sendPacket(storedPacketMove);
        }
        storedPacketMove = null;
        return true;
    }
}
