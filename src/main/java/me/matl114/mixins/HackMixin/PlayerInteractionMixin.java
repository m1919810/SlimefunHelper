package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.sugar.Local;
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
import net.minecraft.network.packet.Packet;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
    @Nullable
    @Unique private BlockPos currentFailBreakPos = null;
    @Unique private int failBreakStartTick;
    @Override
    public BlockPos getCurrentMiningPos(){
        return currentBreakingPos;
    }
    @Override
    @Nullable
    public BlockPos getCurrentFailBreakPos(){
        return doubleMine.get()? currentFailBreakPos: null;
    }
    @Override
    public float getCurrentMiningProgress(boolean shouldPredict){
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if(block.isAir()){
            return -1.0F;
        }
        if(!shouldPredict || !optimizeOneBlockMine.get() || (breakingBlock && isCurrentlyBreaking(currentBreakingPos))){
            return this.currentBreakingProgress == 0.0F ? -1.0F : this.currentBreakingProgress;
        }

        //ack predict
        float speed = block.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), currentBreakingPos);
//                else if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE) && speed > breakThreshold.get()){
//                    //no need to restore currentBreakingProgress?
//                    return;
//                }
        int ticksSinceLastStart = Tasks.getTick() - this.lastStartMineBreakingProgressResetTick;
        //loading progress...
        return speed * ticksSinceLastStart;
    }
    @Override
    public float getFailBreakMiningProgress(){
        if(currentFailBreakPos == null){
            return -1.0F;
        }
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
        if(block.isAir()){
            return -1.0F;
        }
        float speed = block.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), currentBreakingPos);
        return (Tasks.getTick() - failBreakStartTick) * speed;
    }

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
    private final static Config.IntRef grimThreshold = Configs.MINE_CONFIG.getInt(Configs.FAST_BREAK_GRIMAC_THRESHOLD);
    @Unique
    private final static Config.FlagRef doubleMine = Configs.MINE_CONFIG.getBoolean(Configs.MINE_DOUBLE_BREAK);
    @Unique
    private int lastStartCooldownTick;
    @Unique
    private int gainedAdvantageCooldown;
    private boolean thisTimeOptimizedSamePosBreak;
    @Unique
    private static final Config.EnumRef<Configs.BypassMode> fastBreakBypass =Configs.MINE_CONFIG.getEnum(Configs.MINE_BYPASS_FAST_BREAK_BYPASS_MODE);
    @Unique
    private int cooldownManaging(){
        boolean fastBreak = HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE);
        int cooldownOverride = (fastBreak && breakCoolDown.get() >= 0) ? breakCoolDown.get() : 5;
        if(thisTimeOptimizedSamePosBreak){
            thisTimeOptimizedSamePosBreak = false;
            cooldownOverride =  Math.max(1, cooldownOverride);
        }
        if(cooldownOverride < 5){
            if(fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
                int currentTick = Tasks.getTick();
                lastStartCooldownTick = currentTick;
                if(gainedAdvantageCooldown > grimThreshold.get()){
                    return 5;
                }
            }
        }
        return cooldownOverride;
    }
//    private int fastBreakCooldownManaging(){
//        int cooldownOverride =  breakCoolDown.get();
//        if(thisTimeOptimizedSamePosBreak){
//            thisTimeOptimizedSamePosBreak = false;
//            cooldownOverride =  Math.max(1, cooldownOverride);
//        }
//        if(cooldownOverride < 5){
//            if(fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
//                int currentTick = Tasks.getTick();
//                lastStartCooldownTick = currentTick;
//                if(gainedAdvantageCooldown > grimThreshold.get()){
//                    return 5;
//                }
//            }
//
//
////            if(gainedAdvantage > 900){
////                //reset
////                shouldReset = true;
////                return 5;
////            }
//        }
//        return cooldownOverride;
//    }
//


    @Unique
    private void onStartingMine(BlockPos pos, float speed, boolean instaBreak){
        lastStartingMineIsInstantBreak = instaBreak || speed > Math.min(1.0F, breakThreshold.get());

        lastStartMineBreakingProgressResetTick = Tasks.getTick();

        if(!HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)){
            return;
        }
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
        if(gainedAdvantageCooldown > grimThreshold.get() && canResetThisTime &&  fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
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
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -2* grimThreshold.get(), 2* grimThreshold.get());
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
                if(!optimizeOneBlockMine.get()){
                    this.currentBreakingProgress = 0.0F;
                }
                this.blockBreakingSoundCooldown = 0.0F;
                this.blockBreakingCooldown = cooldownManaging();
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
        if(gainedAdvantageMining > grimThreshold.get() && fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
            gainedAdvantageMining = 150;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Direction dir = Direction.getFacing(pos.toCenterPos().subtract(player.getEyePos())).getOpposite();
            for (int i=0; i< 20; ++i){
                sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence);
                }));
            }
        }
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -2 * grimThreshold.get(), 2 * grimThreshold.get());
    }
    @Unique
    private int lastStartMineBreakingProgressResetTick = 0;
    private boolean lastStartingMineIsInstantBreak = false;
    @Unique
    private int gainedAdvantageMining;
    @Unique
    private int ignoreNextFastBreakStatus = 0;
    @Unique
    private void onPostStopMiningFastBreak(BlockPos pos, double speed, double currentProgress){
        ignoreNextFastBreakStatus = 0;
        if(lastStartMineBreakingProgressResetTick == 0){
            return;
        }

        int predictTick = (int) Math.ceil(1 / speed);
        int tickUsed = (int) Math.ceil( currentProgress / speed);
        int diff = predictTick - tickUsed;
        gainedAdvantageMining += (diff + 1) * 50;
        gainedAdvantageMining = MathHelper.clamp(gainedAdvantageMining, -2 * grimThreshold.get(), 2 * grimThreshold.get());
        if(gainedAdvantageMining > grimThreshold.get() && fastBreakBypass.getValue() == Configs.BypassMode.BYPASS_GRIM){
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
        //remove the flag, can work even if fastbreak off
        if(optimizeOneBlockMine.get()){

            if(Objects.equals(pos, currentBreakingPos)){

                if(this.lastStartMineBreakingProgressResetTick == 0 || this.lastStartingMineIsInstantBreak){
                    return;
                }
                //loading progress...
                this.currentBreakingPos = pos;
                this.currentBreakingProgress = getCurrentMiningProgress(true);
                this.breakingBlock = true;
                this.selectedStack = this.client.player.getMainHandStack();
                this.client.world.setBlockBreakingInfo(this.client.player.getId(), this.currentBreakingPos, this.getBlockBreakingProgress());
                //avoid targeting another block too quickly
                this.thisTimeOptimizedSamePosBreak = true;
                //update blockbreaking progress, do anything you want, sendpackets or sth
                this.updateBlockBreakingProgress(pos, direction);
                cir.setReturnValue(true);
            }else{
                //不同的时候
                //尝试把当前的挖掘进度转为doubleMine或者直接终止 double Mine需要考虑
                //是否需要考虑
                if(doubleMine.get()){

                    //FIXed: double break collapse with optimizeOneBlockBreak
                    //: if predicted progress should be finished, do not make doublebreak
                    float predictedProgress = getCurrentMiningProgress(true);
                    if(predictedProgress <= 1.0F){
                        if(onDoubleBreakAbort()){
                            this.sendSequencedPacket(MinecraftClient.getInstance().world, (seq)-> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBreakingPos, direction, seq));
                        }
                    }

                }
            }
        }
    }

    @Redirect(method = "cancelBlockBreaking", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;currentBreakingProgress:F"))
    private void sameBlockOptimizeDoNotResetProgress(ClientPlayerInteractionManager instance, float value){
        //do not set the fucking value
        if(!optimizeOneBlockMine.get()){
            ((PlayerInteractionMixin)(Object)instance).currentBreakingProgress = value;
        }
    }

    @Redirect(method = "cancelBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak(ClientPlayNetworkHandler instance, Packet packet){


        if(!optimizeOneBlockMine.get() && onDoubleBreakAbort()){
            //we make optimizeOneBlockMine delay its destroy packet to changing the currentPosition in method sameBlockOptimize
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (seq)->{
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBreakingPos, Direction.DOWN, seq);
            });
            return;
        }

        instance.sendPacket(packet);
    }

    @Redirect(method = "attackBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak2(ClientPlayNetworkHandler instance, Packet packet, @Local(argsOnly = true) Direction direction){
        //conflict with optimizeOneBlock

        if(!optimizeOneBlockMine.get() && onDoubleBreakAbort()){
            //we make optimizeOneBlockMine delay its destroy packet to check onDoubleBreakAbort() and  changing the currentPosition in method sameBlockOptimize
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (seq)->{
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBreakingPos, direction, seq);
            });
            return;
        }


        instance.sendPacket(packet);
    }

    private boolean onDoubleBreakAbort(){
        if(doubleMine.get() && currentFailBreakPos == null){
            ClientPlayerEntity playerEntity = MinecraftClient.getInstance().player;
            //FIX: DO NOT USE BlockPos.ZERO
            if(playerEntity.canInteractWithBlockAt(this.currentBreakingPos, 1.0D)){
                BlockState state = MinecraftClient.getInstance().world.getBlockState(this.currentBreakingPos);
                if(!state.isAir() && !state.isLiquid()){
                    float speed = state.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), currentBreakingPos);
                    if(speed > 0){
                        currentFailBreakPos = currentBreakingPos;
                        failBreakStartTick = lastStartMineBreakingProgressResetTick;
                        onPostStopMiningFastBreak(currentBreakingPos, speed, currentBreakingProgress);
                        return true;
                    }
                }
            }
        }

        return false;
    }


    @Shadow
    protected abstract int getBlockBreakingProgress() ;

    @Shadow public abstract boolean updateBlockBreakingProgress(BlockPos pos, Direction direction);

    @Shadow protected abstract boolean isCurrentlyBreaking(BlockPos pos);

    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 0,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        onStartingMine(pos, Float.MAX_VALUE, true);
    }

    @Inject(method = "attackBlock",at= @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I", shift = At.Shift.BEFORE),locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    public void fastBreakCreative(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        this.blockBreakingCooldown = cooldownManaging();
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)){
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 1,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
        onStartingMine(pos, speed, false);
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
            //make cooldown issues

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
                        if(!optimizeOneBlockMine.get()){
                            this.currentBreakingProgress = 0.0F;
                        }
                        this.blockBreakingSoundCooldown = 0.0F;
                        this.blockBreakingCooldown = cooldownManaging();
                    }else if(enableFakeInstBreak.get() && speed > ((breakThreshold.get()/2.0)+0.04d)){
                        nextTickEarlyBreak=true;
                    }
                }
            }
        }
    }
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", ordinal = 0, shift = At.Shift.AFTER),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacketWhenUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir){
        onStartingMine(pos, Float.MAX_VALUE, true);
        this.blockBreakingCooldown = cooldownManaging();
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
            if(!optimizeOneBlockMine.get())
                this.currentBreakingProgress = 0.0F;
            this.blockBreakingSoundCooldown = 0.0F;
            this.blockBreakingCooldown = cooldownManaging();
//
            MinecraftClient.getInstance().world.setBlockBreakingInfo(MinecraftClient.getInstance().player.getId(), this.currentBreakingPos, -1);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void onCommonBlockBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir){
        onPostStopMiningLegally(pos);
    }
    @Redirect(method = "updateBlockBreakingProgress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;currentBreakingProgress:F", ordinal = 4))
    private void onSameBlockDoNotResetProgress(ClientPlayerInteractionManager instance, float value){
        if(!optimizeOneBlockMine.get()){
            ((PlayerInteractionMixin)(Object)instance).currentBreakingProgress = value;
        }
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

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo ci){
        if(currentFailBreakPos != null){
            resetFailBreak:
            {
                if(MinecraftClient.getInstance().world != null){
                    BlockState state = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
                    if(client.player == null || gameMode != GameMode.SURVIVAL){
                        currentFailBreakPos=null;
                        break resetFailBreak;
                    }
                    //we do not mine air or liquid
                    if (state == null || state.isAir() || state.isLiquid()){
                        currentFailBreakPos = null;
                        break resetFailBreak;
                    }
                    float speed = state.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().world,currentFailBreakPos);
                    if(speed > 0.0F && ((Tasks.getTick() - failBreakStartTick) * speed > 1.0F)){
                        // speed < 0 hard
                        currentFailBreakPos = null;
                        break resetFailBreak;
                    }
                    if(client.player != null){
                        //leave too far
                        if(client.player.getPos().squaredDistanceTo(currentBreakingPos.toCenterPos()) > 225){
                            currentFailBreakPos = null;
                            break resetFailBreak;
                        }
                    }

                }
            }

        }
    }




}
