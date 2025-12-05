package me.matl114.mixins.HackMixin;

import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.matl114.access.PlayerInteractionAccess;
import me.matl114.hackUtils.InvTasks;
import me.matl114.hackUtils.Tasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class PlayerInteractionMixin implements PlayerInteractionAccess {
    @Shadow
    private float currentBreakingProgress;
    @Shadow
    private boolean breakingBlock;
    @Shadow
    private ItemStack selectedStack;

    @Shadow
    protected abstract void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

    @Shadow public abstract boolean breakBlock(BlockPos pos);

    @Shadow private int blockBreakingCooldown;

    @Shadow private float blockBreakingSoundCooldown;
    @Shadow private BlockPos currentBreakingPos;

    @Shadow private GameMode gameMode;

    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private ClientPlayNetworkHandler networkHandler;

    public void sendStopBreakPacket(BlockPos pos, Direction direction){
        this.sendSequencedPacket(MinecraftClient.getInstance().world,(sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }
    public void sendStartBreakPacket(BlockPos pos, Direction direction){
        this.sendSequencedPacket(MinecraftClient.getInstance().world,(sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }
//    public void autoSendStopPacket(){
//        if(currentBreakingPos != null){
//            sendStopBreakPacket(currentBreakingPos, Direction.UP);
//        }
//    }
    public float calculateBreakingSpeed(BlockPos blockPos){
        if(this.gameMode.isCreative()){
            return 100000.0f;
        }else {
            BlockState state=this.client.world.getBlockState(blockPos);
            return state.calcBlockBreakingDelta(this.client.player, this.client.player.getWorld(), blockPos);

        }
    }

    public boolean preCalculateInstantBreak( BlockPos blockPos){
        if(this.gameMode.isCreative()){
            return true;
        }else {
            BlockState state=this.client.world.getBlockState(blockPos);
            float speed =state.calcBlockBreakingDelta(this.client.player, this.client.player.getWorld(), blockPos);
            if(speed>=1.0f || (speed>breakThreshold.get()&&HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE))||(enableFakeInstBreak.get() && speed>((breakThreshold.get()/2.0)+0.04d))){
                return true;
            }else return false;
        }
    }

    @Unique
    private final static Config.DoubleRef breakThreshold=Configs.MINE_CONFIG.getDouble(Configs.MINE_FASTBREAK_THRESHOLD);
    @Unique
    private final static Config.IntRef breakCoolDown=Configs.MINE_CONFIG.getInt(Configs.MINE_FASTBREAK_BREAKCOOLDOWN);

    @Unique
    private int lastStartCooldownTick;
    @Unique
    private int gainedAdvantageCooldown;
    private boolean thisTimeOptimizedSamePosBreak;
    @Unique
    private static final Config.EnumRef<Configs.BypassMode> fastBreakBypass =Configs.MINE_CONFIG.getEnum(Configs.MINE_BYPASS_FAST_BREAK_BYPASS_MODE);
    @Unique
    private int fastBreakCooldownManaging(){
        int cooldownOverride =  breakCoolDown.get();
        if(thisTimeOptimizedSamePosBreak){
            thisTimeOptimizedSamePosBreak = false;
            cooldownOverride =  Math.max(1, cooldownOverride);
        }
        if(cooldownOverride < 5){
            if(fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
                int currentTick = Tasks.getTick();
                lastStartCooldownTick = currentTick;
                if(gainedAdvantageCooldown > 750){
                    return 5;
                }
            }


//            if(gainedAdvantage > 900){
//                //reset
//                shouldReset = true;
//                return 5;
//            }
        }
        return cooldownOverride;
    }



    @Unique
    private void onStartingMine(BlockPos pos, float speed, boolean instaBreak){
        if(!HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)){
            return;
        }
        lastStartMiningTick = Tasks.getTick();
        lastStartingMineIsInstantBreak = instaBreak || speed > Math.min(1.0F, breakThreshold.get());
        //escape init case
        if(lastStartCooldownTick == 0)return;
        if(instaBreak)return;
        int thisCurrentTick = Tasks.getTick();
        //this means it is ok to directly mine
        boolean canResetThisTime = false;
        if(thisCurrentTick >= lastStartCooldownTick + 5){
            canResetThisTime = true;
            gainedAdvantageCooldown = (int) (gainedAdvantageCooldown* 0.9);
        }else {
            gainedAdvantageCooldown += 300 - (thisCurrentTick - lastStartCooldownTick) * 50;
        }
        if(gainedAdvantageCooldown > 750 && canResetThisTime &&  fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
            //reset
            gainedAdvantageCooldown = 150;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Direction dir = Direction.getFacing(pos.toCenterPos().subtract(player.getEyePos())).getOpposite();
            for (int i=0 ;i < 20; ++i){
                sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir, sequence);
                }));
            }
        }
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);
    }

    //speed up with early packet when progress>0.7
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/TutorialManager;onBlockBreaking(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;F)V",ordinal = 1,shift=At.Shift.AFTER),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void fastbreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir,net.minecraft.block.BlockState blockState) {
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
           // Debug.info(breakThreshold.get(),this.currentBreakingProgress);
            //do insta break
            if (this.currentBreakingProgress >= breakThreshold.get() ) {
                //Debug.info("here");
                if(ignoreNextFastBreakStatus > 0){
                    return;
                }
                this.breakingBlock = false;
                this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                    this.breakBlock(pos);
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().world, pos);
                onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
                this.currentBreakingProgress = 0.0F;
                this.blockBreakingSoundCooldown = 0.0F;
                this.blockBreakingCooldown = fastBreakCooldownManaging();
            }
        }
    }
    @Unique
    private boolean nextTickEarlyBreak=false;
    @Unique
    private static final Config.FlagRef enableFakeInstBreak= Configs.MINE_CONFIG.getBoolean(Configs.MINE_ENABLE_FAKE_INSTANT_BREAK);


    @Unique
    private void onPostStopMiningLegally(BlockPos pos){
        ignoreNextFastBreakStatus = 0;
        gainedAdvantageMining = (int) (gainedAdvantageMining * 0.9);
        if(gainedAdvantageMining > 750 && fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
            gainedAdvantageMining = 150;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Direction dir = Direction.getFacing(pos.toCenterPos().subtract(player.getEyePos())).getOpposite();
            for (int i=0; i< 20; ++i){
                sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence);
                }));
            }
        }
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);
    }
    @Unique
    private int lastStartMiningTick = 0;
    private boolean lastStartingMineIsInstantBreak = false;
    @Unique
    private int gainedAdvantageMining;
    @Unique
    private int ignoreNextFastBreakStatus = 0;
    @Unique
    private void onPostStopMiningFastBreak(BlockPos pos, double speed, double currentProgress){
        ignoreNextFastBreakStatus = 0;
        if(lastStartMiningTick == 0){
            return;
        }

        int predictTick = (int) Math.ceil(1 / speed);
        int tickUsed = (int) Math.ceil( currentProgress / speed);
        int diff = predictTick - tickUsed;
        gainedAdvantageMining += (diff + 1) * 50;
        gainedAdvantageMining = MathHelper.clamp(gainedAdvantageMining, -1000, 1000);
        if(gainedAdvantageMining > 750 && fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
            //only when starting bypass will we do
            //trigger a common mine
            ignoreNextFastBreakStatus = 2;
        }
    }
    //
    //fixme: fix
    @Unique
    private static final Config.FlagRef optimizeOneBlockMine = Configs.MINE_CONFIG.getBoolean(Configs.MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE);
    @Inject(method = "attackBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", ordinal = 1, shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    public void samePositionOptimize(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, BlockState blockState){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE) && optimizeOneBlockMine.get()){
            if(Objects.equals(pos, currentBreakingPos)){

                if(this.lastStartMiningTick == 0 || this.lastStartingMineIsInstantBreak){
                    return;
                }
                float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
                if(speed > Math.min(1.0F, breakThreshold.get())){
                    //can directly instant break
                    return;
                }
                int ticksSinceLastStart = Tasks.getTick() - this.lastStartMiningTick;
                //loading progress...
                this.currentBreakingProgress = speed * ticksSinceLastStart;
                this.breakingBlock = true;
                this.currentBreakingPos = pos;
                this.selectedStack = this.client.player.getMainHandStack();
                this.client.world.setBlockBreakingInfo(this.client.player.getId(), this.currentBreakingPos, this.getBlockBreakingProgress());
                this.thisTimeOptimizedSamePosBreak = true;
                //update blockbreaking progress, do anything you want, sendpackets or sth
                this.updateBlockBreakingProgress(pos, direction);
                cir.setReturnValue(true);
            }
        }
    }
    @Shadow
    protected abstract int getBlockBreakingProgress() ;

    @Shadow public abstract boolean updateBlockBreakingProgress(BlockPos pos, Direction direction);
    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 0,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        onStartingMine(pos, Float.MAX_VALUE, true);
    }

    @Inject(method = "attackBlock",at= @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I", shift = At.Shift.BEFORE),locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    public void fastBreakCreative(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)){
            this.blockBreakingCooldown = fastBreakCooldownManaging();
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 1,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){

        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
            //make cooldown issues
            float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
            onStartingMine(pos, speed, false);
            if(ignoreNextFastBreakStatus > 0){
                //I accept the status !
                ignoreNextFastBreakStatus -= 1;
                //somehow we left one status here because of fastBreak
                if(ignoreNextFastBreakStatus > 0){
                    return;
                }
            }

            //speed>1.0f可以秒破 此处不调用
            //Debug.info("speed",speed);
            if(!blockState.isAir()){
                if(speed < 1.0f){
                    if(speed > breakThreshold.get()){
                        this.breakingBlock = false;
                        //this start-break + end-break can not pass grimac check, need a normal break to reset buffers
                        //calculate advantages
                        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                            this.breakBlock(pos);
                            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                        });
                        this.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
                        //                CompletableFuture.runAsync(()->{
                        //
                        //                });

                        this.currentBreakingProgress = 0.0F;
                        this.blockBreakingSoundCooldown = 0.0F;
                        this.blockBreakingCooldown = fastBreakCooldownManaging();
                    }else if(enableFakeInstBreak.get() && speed > ((breakThreshold.get()/2.0)+0.04d)){
                        nextTickEarlyBreak=true;
                    }
                }
            }
        }
    }
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", ordinal = 0, shift = At.Shift.AFTER),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacketWhenUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)){
            onStartingMine(pos, Float.MAX_VALUE, true);
            this.blockBreakingCooldown = fastBreakCooldownManaging();
        }
    }

    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",ordinal = 0),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakNextTickPacketSend(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if(enableFakeInstBreak.get() && this.nextTickEarlyBreak) {
            BlockState blockState = MinecraftClient.getInstance().world.getBlockState(pos);
            float speed = blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
            this.nextTickEarlyBreak=false;
            this.breakingBlock = false;
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                this.breakBlock(pos);
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
            });
            onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress + speed);
            this.currentBreakingProgress = 0.0F;
            this.blockBreakingSoundCooldown = 0.0F;
            this.blockBreakingCooldown = fastBreakCooldownManaging();
//
            MinecraftClient.getInstance().world.setBlockBreakingInfo(MinecraftClient.getInstance().player.getId(), this.currentBreakingPos, -1);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void onCommonBlockBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir){
        onPostStopMiningLegally(pos);
    }

//    @Inject(method = "getReachDistance",at = @At(value = "HEAD"),cancellable = true)
//    public void widerReachDistance(CallbackInfoReturnable<Float> cir){
//
//    }
    @Unique
    private static final Config.FlagRef DIS_INTERVAL=Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_INTERVEL) ;
    @Inject(method = "hasLimitedAttackSpeed",at = @At(value = "HEAD"),cancellable = true)
    public void cancelAttackSpeedLimit(CallbackInfoReturnable<Boolean> cir){
        if(DIS_INTERVAL.get()){
            cir.setReturnValue(false);
        }
    }

    @Unique
    private RecipeEntry<?> lastlyCrafted;
    @Unique
    public RecipeEntry<?> getLastlyCrafted(){
        return lastlyCrafted;
    }
    @Unique
    private AtomicBoolean lockRecipe = new AtomicBoolean(false);
    @Unique
    public boolean getRecipeLock(){
        return lockRecipe.get();
    }
    public void setLastlyCrafted(RecipeEntry<?> recipe){
        if(!lockRecipe.get()){
            lastlyCrafted=recipe;
        }
    }
    @Inject(method = "clickRecipe",at = @At("HEAD"))
    public void recordLastRecipe(int syncId, RecipeEntry<?> recipe, boolean craftAll, CallbackInfo ci){
        setLastlyCrafted(recipe);
    }
    @Unique
    public void toggleRecipeLock(){
        lockRecipe.set(!lockRecipe.get());
        Debug.chat("Toggle RecipeLock ",lockRecipe.get());
    }
    private static final AtomicBoolean OPTIMIZE_REMOTE_STACK = InvTasks.OPTIMIZE_SLOT_CLICK_PACKET;
    @Inject(method = "clickSlot",at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/ScreenHandler;onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",shift = At.Shift.AFTER), cancellable = true)
    public void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci){
        if(OPTIMIZE_REMOTE_STACK.get()){
            this.networkHandler.sendPacket(new ClickSlotC2SPacket(syncId, player.currentScreenHandler.getRevision(), slotId, button, actionType, player.currentScreenHandler.getCursorStack().copy(), new Int2ObjectOpenHashMap<>()));
            ci.cancel();
        }
    }


    @Inject(method = "interactBlock", at = @At(value = "HEAD"))
    public void onPreInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir){
       if( !Listener.doItemUseAtBlockPre(hand, hitResult)){
           cir.setReturnValue(ActionResult.PASS);
       }
    }
    @Inject(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",shift = At.Shift.AFTER))
    public void onPostInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir){
        Listener.doItemUseAtBlockPost(hand, hitResult);
    }



    @ModifyArg(method = "interactItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V"), index = 1)
    private SequencedPacketCreator onInteractItemPacket(SequencedPacketCreator packetCreator){
        return (i)->{
            Event<PlayerInteractItemC2SPacket> mutableObject = new Event<>(((PlayerInteractItemC2SPacket) packetCreator.predict(i)), true, true);
            Listener.getPlayerItemUsePacketCreate().handleValue(mutableObject);
            return mutableObject.isCancelled()? null: mutableObject.context();
        };
    }



}
