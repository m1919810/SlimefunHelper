package me.matl114.hackUtils;

import me.matl114.access.ClientAccess;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.access.MoonriseBlockStateBaseAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LegalMovementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;
import java.util.function.Predicate;

public class InteractionTasks {
    public static void init(){

    }
    private static BlockHitResult lastInteract = null;
    private static int lastInteractTimestamp = -1;
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static void listenInteractBlockPacket(PlayerInteractBlockC2SPacket packet){
        if(packet.getBlockHitResult().getType() == HitResult.Type.BLOCK){
            lastInteract = packet.getBlockHitResult();
            lastInteractTimestamp = Tasks.getTick();
        }
    }
    public static BlockPos predictScreenFrom(Predicate<Block> targetBlock){
        int timeStamp = Tasks.getTick();
        //在一秒内反应的 可以考虑
        if(timeStamp < lastInteractTimestamp + 20 && lastInteract != null){
            BlockPos hitPose = lastInteract.getBlockPos();
            if(hitPose != null && targetBlock.test( mc.world.getBlockState(hitPose).getBlock())){
                return hitPose;
            }
            //block Type not match,
        }
        return RaycastUtils.rayTraceSpecificBlock((b)->b == Blocks.DISPENSER || b == Blocks.DROPPER).orElse(null);
    }

    @Unique
    private static final Config.IntRef USE_ITEM_NO_COOLDOWN = Configs.INTERACT_CONFIG.getInt(Configs.INTERACT_NO_COOLDOWN);

    public static void onCooldown(Event<Integer> event){
        if(USE_ITEM_NO_COOLDOWN.get() >= 0){
            event.context(USE_ITEM_NO_COOLDOWN.get());
        }
    }

    private static final Config.FlagRef legalScaffold = Configs.INTERACT_CONFIG.getBoolean(Configs.INTERACT_SCAFFOLD_LEGAL);
    private static final Config.EnumRef<Configs.LegalTargetingMode> legalScaffoldMode = Configs.INTERACT_CONFIG.getEnum(Configs.INTERACT_SCAFFOLD_TARGET_MODE);
    private static final Config.IntRef scaffoldCooldownOverride = Configs.INTERACT_CONFIG.getInt(Configs.INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE);
    private static int lastTickScaffoldWalk = 0;

    private static void placeBlock(Hand hand, BlockHitResult result){
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        if (actionResult2.isAccepted()) {
            if (actionResult2.shouldSwingHand()) {
                mc.player.swingHand(hand);
            }
            return;
        }
    }
    private static void placeBlockLegally(Hand hand, BlockHitResult result){
        if(mc.crosshairTarget instanceof BlockHitResult result1){
            //same block same side
            //use vanilla crosshairtarget
            if(Objects.equals(result1.getBlockPos(), result.getBlockPos()) && Objects.equals(result1.getSide(), result.getSide()) && Objects.equals(result1.getType(), result.getType())){
                placeBlock(hand, result1);
                return;
            }
        }

        if(legalScaffold.get()){
            var mode = legalScaffoldMode.getValue();
            boolean useDelayMovement = mode == Configs.LegalTargetingMode.DELAY_MOVEMENT;
            if(mode == Configs.LegalTargetingMode.USEITEM_PACKET){
                Vec2f rotation = EntityUtils.rotationToPitchYaw( result.getBlockPos().toCenterPos().subtract(mc.player.getEyePos()).normalize());
                mc.interactionManager.sendSequencedPacket(mc.world, (i)-> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
                placeBlock(hand, result);
                return;
            }
            if(useDelayMovement){
                //grimac checked two fucking things
                //1. facing
                //2. eye position with direction
                ClientPlayerAccess.of(mc.player).getLegalMovementManager().addMovementModifier(new LegalMovementManager.MovementModifier() {
                    @Override
                    public int priority() {
                        return -10_000_000;
                    }

                    @Override
                    public boolean mayModifyRotation() {
                        return true;
                    }

                    @Override
                    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                        Vec2f rotation = EntityUtils.rotationToPitchYaw( result.getBlockPos().toCenterPos().subtract(mc.player.getEyePos()).normalize());
                        EntityUtils.setEntityYawSafe(player, rotation.y);
                        EntityUtils.setEntityPitchSafe(player, rotation.x);
                    }

                    @Override
                    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
                        //after input tick,
                        //we may change some of the direction flag, so the velocity will be better
                        if(!MovTasks.MOVEMENT_CORRECTION.get()){
                            return;
                        }
                        movementManagerEvent.context().tryCorrectMovementInput();
                    }

                    @Override
                    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        if(enabledThisTick){
                            movementManagerEvent.context.playerStatus.restoreRotation();
                            ACPostTasks.addPostTransactionAction((h)->{
                                placeBlock(hand, result);
                            });
                        }
                        return false;
                    }
                });
            }
        }else {
            placeBlock(hand, result);
        }
    }

    public static void scaffoldTaskWhenRightClick(Event<Void> rightClickEvent){

        //check scaffold when player right pressed the mouse
         if(mc.player != null && HotKeys.getHotkeyToggleManager().getState(HotKeys.SCAFFOLD_WALK) && mc.options.useKey.isPressed() ){
             //check hand item
             Hand hand = null;
             for (Hand hand0 : Hand.values()){
                 ItemStack item = mc.player.getStackInHand(hand0);
                 //we assert player hold block while scaffold, or it will be really annoying
                 //the holding block must be a full cube
                 if(!item.isEmpty() && item.getItem() instanceof BlockItem blockItem && !blockItem.getBlock().getDefaultState().isAir() && blockItem.getBlock().getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)){
                     //make position estimate, 2ticks after current position
                     hand = hand0;
                     break;
                     //the supporting block cannot support the player
                     //the supporting block can be replaced


                 }
             }
             if(hand == null){
                 return;
             }
            // Debug.chat("tick", ClientAccess.of(mc).getCooldown());
            //check if we can have any scaffold
             //todo add lerp to config
             Vec3d playerPos = mc.player.getLerpedPos(2.0F);//mc.player.getPos();
             //do not predict y level
             playerPos = new Vec3d(playerPos.x, mc.player.getY(), playerPos.z);

             BlockPos testPos1 = BlockPos.ofFloored(playerPos);
             BlockState blockState = mc.world.getBlockState(testPos1);
             if(!blockState.isAir() && !MoonriseBlockStateBaseAccess.of(blockState).isConstantCollisionShapeEmpty()){
                //if player is on a slab or something
              //  Debug.chat("has");
              //  Debug.chat("ret 1");
                return;
            }
            //test if the supporting block can support player
            BlockPos supportingPos = testPos1.down();
            BlockState supportingState = mc.world.getBlockState(supportingPos);
            if(!supportingState.isAir() && !MoonriseBlockStateBaseAccess.of(supportingState).isConstantCollisionShapeEmpty()){
                //Debug.info("empty");
              //  Debug.info("item");
               // Debug.chat("ret 2", supportingState);
                return;
            }
            if(supportingState.isReplaceable()){
                BlockHitResult hitResult = guessTheBestPlacePositionForTargetingBlock(supportingPos);
                if(hitResult != null){
                    //Debug.chat("interact", hitResult.getBlockPos(), hitResult.getSide(), hitResult.getPos());
                    placeBlockLegally(hand, hitResult);
                    int cool = scaffoldCooldownOverride.get();
                    if(cool >= 0){
                        ClientAccess.of(mc).setCooldown(cool);
                    }else{
                        Event<Integer> event = new Event<>(4, true, true);
                        Listener.getUseItemCooldownReset().handleValue(event);
                        if(!event.isCancelled() && event.context() != null){
                            ClientAccess.of(mc).setCooldown(event.context());
                        }
                    }

                    //todo should we autostack

                    return;
                }

            }
            //Debug.chat("nothing");

        }
    }
//fixme delete log
    //fixme lefthand work
    //fixme speed effect
    private static int lastScaffoldTick = 0;
    public static void stopUseItemBlockWhenScaffolding(Event<Hand> eventUseItem){
        if(Tasks.getTick() < lastScaffoldTick + 3){
            eventUseItem.cancel();
        }
    }


    public static BlockHitResult guessTheBestPlacePositionForTargetingBlock(BlockPos pos){
        if(mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK ){
            BlockHitResult hitResult = ((BlockHitResult) mc.crosshairTarget);
            BlockPos targetPos = hitResult.getBlockPos();
            Direction dir = hitResult.getSide();
            BlockPos estimatePlacingPos = targetPos.offset(dir);
            //use vanilla
            if(Objects.equals(estimatePlacingPos, pos)){
                return hitResult;
            }
        }
        for (Direction direction : Direction.values()){
            BlockPos testPos = pos.offset(direction);
            BlockState state = mc.world.getBlockState(testPos);
            //fixme donot place on liquid,
            //air liquidplace
            if(!state.isAir() && !state.isLiquid()){
                return RaycastUtils.createHitResult(testPos, direction.getOpposite());
            }
        }
        if(!legalScaffold.get()){
            //not legal, we can airplace
            return RaycastUtils.createHitResult(pos.offset(Direction.DOWN), Direction.UP);

        }
        //todo find better block to place,
        //todo copy copy
        //
        //
        //
        return null;
    }


    static{
        Listener.registerSinglePacketListener(PlayerInteractBlockC2SPacket.class, InteractionTasks::listenInteractBlockPacket);
        Listener.getUseItemCooldownReset().registerHandler(InteractionTasks::onCooldown);
        Listener.getPreHandleInput().registerHandler(InteractionTasks::scaffoldTaskWhenRightClick);
    }
}
