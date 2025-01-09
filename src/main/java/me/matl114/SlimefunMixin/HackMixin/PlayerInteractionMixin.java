package me.matl114.SlimefunMixin.HackMixin;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.Access.PlayerInteractionAccess;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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
    protected abstract void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

    @Shadow public abstract boolean breakBlock(BlockPos pos);

    @Shadow private int blockBreakingCooldown;

    @Shadow private float blockBreakingSoundCooldown;
    @Shadow private BlockPos currentBreakingPos;

    @Shadow private GameMode gameMode;

    @Shadow @Final private MinecraftClient client;

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
    public void autoSendStopPacket(){
        if(currentBreakingPos!=null){
            sendStopBreakPacket(currentBreakingPos, Direction.UP);
        }
    }

    public boolean preCalculateInstantBreak( BlockPos blockPos){
        if(this.gameMode.isCreative()){
            return true;
        }else {
            BlockState state=this.client.world.getBlockState(blockPos);
            float speed =state.calcBlockBreakingDelta(this.client.player, this.client.player.getWorld(), blockPos);
            if(speed>=1.0f||(speed>breakThreshold.get()&&HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE))||(enableFakeInstBreak.get()&& speed>((breakThreshold.get()/2.0)+0.04d))){
                return true;
            }else return false;
        }
    }

    @Unique
    private final static AtomicDouble breakThreshold=Configs.MINE_CONFIG.getDouble(Configs.MINE_FASTBREAK_THRESHOLD);
    @Unique
    private final static AtomicInteger breakCoolDown=Configs.MINE_CONFIG.getInt(Configs.MINE_FASTBREAK_BREAKCOOLDOWN);

    //speed up with early packet when progress>0.7
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/TutorialManager;onBlockBreaking(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;F)V",ordinal = 1,shift=At.Shift.AFTER),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void fastbreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir,net.minecraft.block.BlockState blockState) {
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
           // Debug.info(breakThreshold.get(),this.currentBreakingProgress);
            if (this.currentBreakingProgress >= breakThreshold.get()&& HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
                //Debug.info("here");
                this.breakingBlock = false;
                this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                    this.breakBlock(pos);
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                this.currentBreakingProgress = 0.0F;
                this.blockBreakingSoundCooldown = 0.0F;
                this.blockBreakingCooldown = breakCoolDown.get();
            }
        }
    }
    @Unique
    private boolean nextTickEarlyBreak=false;
    @Unique
    private static final AtomicBoolean enableFakeInstBreak= Configs.MINE_CONFIG.getBoolean(Configs.MINE_ENABLE_FAKE_INSTANT_BREAK);
    @Unique
    private static final AtomicDouble reachDistance=Configs.MINE_CONFIG.getDouble(Configs.MINE_FASTBREAK_REACH);
    //
    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 1,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
            float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
            //speed>1.0f可以秒破 此处不调用
            //Debug.info("speed",speed);
            if(!blockState.isAir()){
                if(speed>breakThreshold.get()&&speed<1.0f){
                    this.breakingBlock = false;
                    this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                        this.breakBlock(pos);
                        return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                    });
    //                CompletableFuture.runAsync(()->{
    //
    //                });

                    this.currentBreakingProgress = 0.0F;
                    this.blockBreakingSoundCooldown = 0.0F;
                    this.blockBreakingCooldown = breakCoolDown.get();
                }else if(enableFakeInstBreak.get()&& speed>((breakThreshold.get()/2.0)+0.04d)){
                    nextTickEarlyBreak=true;
                }
            }
        }
    }
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",ordinal = 0),cancellable = true,locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakNextTickPacketSend(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if(enableFakeInstBreak.get()&&this.nextTickEarlyBreak) {
            this.nextTickEarlyBreak=false;
            this.breakingBlock = false;
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                this.breakBlock(pos);
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
            });
            this.currentBreakingProgress = 0.0F;
            this.blockBreakingSoundCooldown = 0.0F;
            this.blockBreakingCooldown = breakCoolDown.get();
            MinecraftClient.getInstance().world.setBlockBreakingInfo(MinecraftClient.getInstance().player.getId(), this.currentBreakingPos, -1);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getReachDistance",at = @At(value = "HEAD"),cancellable = true)
    public void widerReachDistance(CallbackInfoReturnable<Float> cir){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
            cir.setReturnValue(reachDistance.floatValue());
        }
    }
    @Unique
    private static final AtomicBoolean DIS_INTERVAL=Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_INTERVEL) ;
    @Inject(method = "hasLimitedAttackSpeed",at = @At(value = "HEAD"),cancellable = true)
    public void cancelAttackSpeedLimit(CallbackInfoReturnable<Boolean> cir){
        if(DIS_INTERVAL.get()){
            cir.setReturnValue(false);
        }
    }
    @Inject(method = "hasExtendedReach",at = @At(value = "HEAD"),cancellable = true)
    public void extendedReach(CallbackInfoReturnable<Boolean> cir){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
            cir.setReturnValue(true);
        }
    }

}
