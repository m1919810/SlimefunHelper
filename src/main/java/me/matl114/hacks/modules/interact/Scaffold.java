package me.matl114.hacks.modules.interact;

import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.ACPostTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.events.Event;
import me.matl114.utils.impl.entity.LegalMovementManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;

import java.util.Objects;

public class Scaffold extends BaseModule {
    public static final String[] SCAFFOLD_ENABLE = {"hotkeys-toggle", "scaffold"};
    public static final String[] INTERACT_SCAFFOLD_LEGAL = {"interact-scaffold", "legal-mode"};
    public static final String[] INTERACT_SCAFFOLD_TARGET_MODE = {"interact-scaffold", "legal-targeting"};
    public static final String[] INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE = {"interact-scaffold", "scaffold-cooldown-override"};


    public Scaffold(){
        bindFlag(enable);
    }

    public final FlagRef enable = toggle(SCAFFOLD_ENABLE)
        .build();

    public final KeyBindRef keyBind = toggleHotkey(SCAFFOLD_ENABLE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_SEMICOLON))
        .build();

    public final FlagRef legal = flagBuilder(Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_LEGAL)
        .build();

    public final EnumRef<Configs.LegalInteractMode> legalMode = builder(Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_TARGET_MODE, Configs.LegalInteractMode.class)
        .defaultValue(Configs.LegalInteractMode.USEITEM_PACKET)
        .build();

    public final IntRef cooldownOverride = builder(Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE, IntRef.TYPE)
        .defaultValue(-1)
        .build();


    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onRightClick);
    }

    private void placeBlockLegally(Hand hand, BlockHitResult result){
        if(mc.crosshairTarget instanceof BlockHitResult result1){
            //same block same side
            //use vanilla crosshairtarget
            if(Objects.equals(result1.getBlockPos(), result.getBlockPos()) && Objects.equals(result1.getSide(), result.getSide()) && Objects.equals(result1.getType(), result.getType())){
                InteractionTasks.placeBlock(hand, result1);
                return;
            }
        }

        if(legal.get()){
            var mode = legalMode.get();
            switch (mode){
                case USEITEM_PACKET -> placeBlockUseItem(hand, result);
                case DELAY_MOVEMENT -> placeBlockDelayMovement(hand, result);
                case MOVEMENT -> placeBlockMovement(hand, result);
            }
        }else {
            InteractionTasks.placeBlock(hand, result);
        }
    }

    private void placeBlockUseItem(Hand hand, BlockHitResult result){
        Vec2f rotation = EntityUtils.rotationToPitchYaw( result.getBlockPos().toCenterPos().subtract(mc.player.getEyePos()).normalize());
        mc.interactionManager.sendSequencedPacket(mc.world, (i)-> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
        InteractionTasks.placeBlock(hand, result);
        return;
    }

    private void placeBlockDelayMovement(Hand hand, BlockHitResult result){
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
                movementManagerEvent.context().tryCorrectMovementInput();
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                if(enabledThisTick){
                    movementManagerEvent.context.playerStatus.restoreRotation();
                    ACPostTasks.addPostTransactionAction((h)->{
                        InteractionTasks.placeBlock(hand, result);
                    });
                }
                return false;
            }
        });
    }

    private void placeBlockMovement(Hand hand, BlockHitResult result){
        Vec2f rotation = EntityUtils.rotationToPitchYaw( result.getBlockPos().toCenterPos().subtract(mc.player.getEyePos()).normalize());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotation.y, rotation.x, mc.player.isOnGround()));
        InteractionTasks.placeBlock(hand, result);
    }

    public void onRightClick(Event<Void> rightClickEvent){

        //check scaffold when player right pressed the mouse
        //todo: check this
        if(mc.player != null && enable.get() && mc.options.useKey.isPressed() ){
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
                    int cool = cooldownOverride.get();
                    if(cool >= 0){
                        ClientAccess.of(mc).setItemUseCooldown(cool);
                    }else{
                        Event<Integer> event = new Event<>(4, true, true);
                        Listener.getUseItemCooldownReset().handleValue(event);
                        if(!event.isCancelled() && event.context() != null){
                            ClientAccess.of(mc).setItemUseCooldown(event.context());
                        }
                    }

                    //todo should we autostack

                    return;
                }

            }
            //Debug.chat("nothing");

        }
    }
//    //fixme delete log
//    //fixme lefthand work
//    //fixme speed effect
//    private int lastScaffoldTick = 0;
//    public void onStopUseItem(Event<Hand> eventUseItem){
//        if(Tasks.getTick() < lastScaffoldTick + 3){
//            eventUseItem.cancel();
//        }
//    }


    public BlockHitResult guessTheBestPlacePositionForTargetingBlock(BlockPos pos){
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
        if(!legal.get()){
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

    public void onPresetReload(Event<EventContainer<ModulePreset>> event){
        switch (event.context().getValue()){
            case HACKING, VANILLA -> legal.set(false);
            default -> legal.set(true);
        }
    }
}
