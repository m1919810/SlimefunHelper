package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class StepHeight extends BaseModule implements LegalMovementManager.MovementModifier {

    public static final String[] MOVE_ENHANCED_STEPHEIGHT = {"move-safety", "enhance-stepheight"};

    public static LegalMovementManager.DelegateMovementModifier instance;

    public StepHeight() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ENHANCED_STEPHEIGHT).build();

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        runTicks = 0;
        toggleRunning = false;
        y = 0.0D;
        ticksEnd = 6;
        jumpTriggerStepHeight = this::triggerJump;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        jumpTriggerStepHeight = null;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onJump);
    }

    private Runnable jumpTriggerStepHeight;

    private void onJump(Event<Integer> jumpEvent) {
        if (jumpTriggerStepHeight != null) {
            jumpTriggerStepHeight.run();
        }
    }

    boolean toggleRunning;
    int runTicks;
    double y;
    int ticksEnd = 6;

    @Override
    public int priority() {
        return PRIORITY_COMMON;
    }

    public void triggerJump() {
        if (isActive()) {
            double jumpStrength = LivingEntityAccess.of(mc.player).getJumpUpwardSpeed(1.0F);
            double gravity = mc.player.getFinalGravity();
            int ticksNeeded = (int) ((jumpStrength - 1E-5) / gravity);
            ticksEnd = ticksNeeded + 1;
            toggleRunning = true;
            runTicks = 0;
        }
    }

    @Override
    public boolean mayModifyPos() {
        return isActive() && runTicks >= ticksEnd - 1;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {

        if (isActive()) {
            if (toggleRunning) {

                if (runTicks < 0) {
                    runTicks = -114514;
                    //                            Debug.info("check out");
                    //                            mc.options.jumpKey.setPressed(true);
                    // In case setback packets set false
                    movementManagerEvent.context.playerStatus.entity.setOnGround(true);
                    //                            Vec3d vec =
                    // movementManagerEvent.context.playerStatus.entity.getVelocity();
                    //
                    // movementManagerEvent.context.playerStatus.entity.addVelocityInternal(new Vec3d(0, 0.4,0));
                    //
                    // LivingEntityAccess.of(movementManagerEvent.context.playerStatus.entity).setJumpingCooldown(0);
                }
            }
        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        //                Vec3d vec3 = movementManagerEvent.context.playerStatus.entity.getPos();
        ////                if(vec3.getY() != 0){
        ////                    Debug.info(vec3);
        ////                }
        //                if(vec3.getY() > 86){
        //                    Debug.info(vec3);
        //                }

        if (isActive()) {
            if (toggleRunning) {
                // 5刻后达到最高点
                runTicks += 1;
                if (runTicks >= ticksEnd) {

                    ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                    if (!player.isOnGround()) {
                        Vec3d vec3d2 = new Vec3d((double) player.sidewaysSpeed, 0.0, (double) player.forwardSpeed);
                        Vec3d vec3d0 = player.getVelocity();
                        Vec3d movement = // new Vec3d(vec3d0.x, 0, vec3d0.z)
                                vec3d0.add(EntityUtils.movementInputToVelocity(
                                        vec3d2, player.getMovementSpeed(), player.getYaw()));
                        // important simulation
                        player.setOnGround(true);
                        boolean useStepHeightFeature = MovTasks.doMovementInvolveStepheight(player, movement);
                        if (useStepHeightFeature) {
                            // Debug.chat("pass stepheight");
                            runTicks = -200;
                            movementManagerEvent.cancel();
                            //                        movementManagerEvent.context.playerStatus.restorePos();
                            movementManagerEvent.context.playerStatus.entity.setOnGround(true);
                            mc.getNetworkHandler()
                                    .sendPacket(VPacket.newOnGroundOnly(true, player.horizontalCollision));
                            return;
                        }
                        player.setOnGround(false);
                    }

                    runTicks = -11451;
                }
                // mc.options.jumpKey.setPressed(false);
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (isActive()) {
            if (runTicks < -1145) {
                //                        if(runTicks == -114514){
                //                            mc.options.jumpKey.setPressed(false);
                //                        }
                runTicks = 0;
                toggleRunning = false;
            }
        }

        return true;
    }
}
