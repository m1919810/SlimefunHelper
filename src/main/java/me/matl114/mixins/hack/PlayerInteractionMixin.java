package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        return MineTasks.getMineExtra().doubleBreak.get()? currentFailBreakPos: null;
    }
    @Override
    public float getCurrentMiningProgress(boolean shouldPredict){
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if(block.isAir()){
            return -1.0F;
        }
        if(!shouldPredict || !MineTasks.getMineExtra().optimizeOneBlock.get() || (breakingBlock && isCurrentlyBreaking(currentBreakingPos))){
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
            MineExtra mineExtra = MineTasks.getMineExtra();
            if(speed>=1.0f || (speed > mineExtra.breakThreshold.get()&& mineExtra.quickMine.get())||(mineExtra.fakeInstaBreak.get() && speed>((mineExtra.breakThreshold.get() / 2.0)+0.04d))){
                return true;
            }else return false;
        }
    }

    @Unique
    private int lastStartCooldownTick;
    @Unique
    private int gainedAdvantageCooldown;
    private boolean thisTimeOptimizedSamePosBreak;

    @Unique
    private int cooldownManaging(){
        MineExtra mineExtra = MineTasks.getMineExtra();
        boolean fastBreak = mineExtra.quickMine.get();
        int cooldownOverride = (fastBreak && mineExtra.breakCooldown.get() >= 0) ? mineExtra.breakCooldown.get() : 5;
        if(thisTimeOptimizedSamePosBreak){
            thisTimeOptimizedSamePosBreak = false;
            cooldownOverride =  Math.max(1, cooldownOverride);
        }
        if(cooldownOverride < 5){
            if(mineExtra.fastBreakBypassMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
                int currentTick = Tasks.getTick();
                lastStartCooldownTick = currentTick;
                if(gainedAdvantageCooldown > mineExtra.grimAcCounterThreshold.get()){
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
        MineExtra mineExtra = MineTasks.getMineExtra();
        lastStartingMineIsInstantBreak = instaBreak || speed > Math.min(1.0F, mineExtra.breakThreshold.get());

        lastStartMineBreakingProgressResetTick = Tasks.getTick();

        if(!mineExtra.quickMine.get()){
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
        int threshold = mineExtra.grimAcCounterThreshold.get();
        if(gainedAdvantageCooldown > threshold && canResetThisTime &&  mineExtra.fastBreakBypassMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
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
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -2* threshold, 2* threshold);
    }

    //speed up with early packet when progress>0.7
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/TutorialManager;onBlockBreaking(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;F)V",ordinal = 1,shift=At.Shift.AFTER),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void fastbreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir,net.minecraft.block.BlockState blockState) {
        MineExtra mineExtra = MineTasks.getMineExtra();
        if(mineExtra.quickMine.get()) {
           // Debug.info(breakThreshold.get(),this.currentBreakingProgress);
            //do insta break
            if (this.currentBreakingProgress >= mineExtra.breakThreshold.get()) {
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
                if(!mineExtra.optimizeOneBlock.get()){
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
    private void onPostStopMiningLegally(BlockPos pos){
        MineExtra mineExtra = MineTasks.getMineExtra();
        ignoreNextFastBreakStatus = 0;
        gainedAdvantageMining = (int) (gainedAdvantageMining * 0.9);
        int threshold = mineExtra.grimAcCounterThreshold.get();
        if(gainedAdvantageMining > threshold && mineExtra.fastBreakBypassMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
            gainedAdvantageMining = 150;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Direction dir = Direction.getFacing(pos.toCenterPos().subtract(player.getEyePos())).getOpposite();
            for (int i=0; i< 20; ++i){
                sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence);
                }));
            }
        }
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -2 * threshold, 2 * threshold);
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
        MineExtra mineExtra = MineTasks.getMineExtra();
        int predictTick = (int) Math.ceil(1 / speed);
        int tickUsed = (int) Math.ceil( currentProgress / speed);
        int diff = predictTick - tickUsed;
        gainedAdvantageMining += (diff + 1) * 50;
        int threshold = mineExtra.grimAcCounterThreshold.get();
        gainedAdvantageMining = MathHelper.clamp(gainedAdvantageMining, -2 * threshold, 2 * threshold);
        if(gainedAdvantageMining > threshold && mineExtra.fastBreakBypassMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
            //only when starting bypass will we do
            //trigger a common mine
            ignoreNextFastBreakStatus = 2;
        }
    }
    //
    //fixme: fix
    @Inject(method = "attackBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", ordinal = 1, shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    public void samePositionOptimize(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, BlockState blockState){
        //remove the flag, can work even if fastbreak off
        MineExtra mineExtra = MineTasks.getMineExtra();
        if(mineExtra.optimizeOneBlock.get()){

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
                if(mineExtra.doubleBreak.get()){

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
        if(!MineTasks.getMineExtra().optimizeOneBlock.get()){
            ((PlayerInteractionMixin)(Object)instance).currentBreakingProgress = value;
        }
    }

    @Redirect(method = "cancelBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak(ClientPlayNetworkHandler instance, Packet packet){


        if(!MineTasks.getMineExtra().optimizeOneBlock.get() && onDoubleBreakAbort()){
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

        if(!MineTasks.getMineExtra().optimizeOneBlock.get() && onDoubleBreakAbort()){
            //we make optimizeOneBlockMine delay its destroy packet to check onDoubleBreakAbort() and  changing the currentPosition in method sameBlockOptimize
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (seq)->{
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBreakingPos, direction, seq);
            });
            return;
        }


        instance.sendPacket(packet);
    }

    private boolean onDoubleBreakAbort(){
        if(MineTasks.getMineExtra().doubleBreak.get() && currentFailBreakPos == null){
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
        if(MineTasks.getMineExtra().quickMine.get()){
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 1,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
        onStartingMine(pos, speed, false);
        if(MineTasks.getMineExtra().quickMine.get()) {
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
                    MineExtra mineExtra = MineTasks.getMineExtra();
                    if(speed > mineExtra.breakThreshold.get()){
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
                        if(!mineExtra.optimizeOneBlock.get()){
                            this.currentBreakingProgress = 0.0F;
                        }
                        this.blockBreakingSoundCooldown = 0.0F;
                        this.blockBreakingCooldown = cooldownManaging();
                    }else if(mineExtra.fakeInstaBreak.get() && speed > ((mineExtra.breakThreshold.get()/2.0)+0.04d)){
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
        if(MineTasks.getMineExtra().fakeInstaBreak.get() && this.nextTickEarlyBreak) {
            BlockState blockState = MinecraftClient.getInstance().world.getBlockState(pos);
            float speed = blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
            this.nextTickEarlyBreak=false;
            this.breakingBlock = false;
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                this.breakBlock(pos);
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
            });
            onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress + speed);
            if(!MineTasks.getMineExtra().optimizeOneBlock.get())
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
        if(!MineTasks.getMineExtra().optimizeOneBlock.get()){
            ((PlayerInteractionMixin)(Object)instance).currentBreakingProgress = value;
        }
    }


//    @Inject(method = "getReachDistance",at = @At(value = "HEAD"),cancellable = true)
//    public void widerReachDistance(CallbackInfoReturnable<Float> cir){
//
//    }
    @Inject(method = "hasLimitedAttackSpeed",at = @At(value = "HEAD"),cancellable = true)
    public void cancelAttackSpeedLimit(CallbackInfoReturnable<Boolean> cir){
        if(CombatTasks.getCombatExtra().noCooldown.get()){
            cir.setReturnValue(false);
        }
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
