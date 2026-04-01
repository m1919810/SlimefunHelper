package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.entity.LegalMovementManager;

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

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onJump);
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
    public boolean mayModifyPos() {
        return false;
    }

    //    List<BlockState> blocks;
    //    BlockPos lastPosCenter;
    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        //        if(ExtraTasks.getTests().flag4.get()){
        //            ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //            blocks = new ArrayList<>(9);
        //            BlockPos pos = player.getVelocityAffectingPos();
        //            BlockPos topPos = pos.add(0, MathHelper.ceil( player.getHeight()) + 1, 0);
        //            lastPosCenter = topPos;
        //            for (var i = -1; i <= 1; i++) {
        //                for (var j = -1; j <= 1; j++) {
        //                    BlockPos pos2 = topPos.add(i, 0, j);
        //                    blocks.add(mc.world.getBlockState(pos2));
        //                    mc.world.setBlockState(pos2, Blocks.BARRIER.getDefaultState(), Block.NOTIFY_LISTENERS |
        //                        Block.FORCE_STATE);
        //                }
        //            }
        //        }
    }
    //    boolean thisTickJump = false;
    public void onJump(Event<Integer> event) {
        //        if(ExtraTasks.getTests().flag4.get()){
        //            mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(false, false));
        //            thisTickJump = true;
        //        }
    }
    //
    //    @Override
    //    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
    //        if(thisTickJump){
    //            thisTickJump = false;
    //            movementManagerEvent.cancel();
    //        }
    //    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        //        if(blocks != null && lastPosCenter != null){
        //            int idx = 0 ;
        //            for (var i = -1; i <= 1; i++) {
        //                for (var j = -1; j <= 1; j++) {
        //                    BlockState state = blocks.get(idx ++);
        //                    BlockPos pos2 = lastPosCenter.add(i, 0, j);
        //                    mc.world.setBlockState(pos2, state, Block.NOTIFY_LISTENERS |
        //                        Block.FORCE_STATE);
        //                }
        //            }
        //            blocks = null;
        //            lastPosCenter = null;
        //        }
        //        return true;
        return true;
    }
}
