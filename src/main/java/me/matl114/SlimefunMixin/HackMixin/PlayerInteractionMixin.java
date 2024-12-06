package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.HotKeyUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.bukkit.block.BlockState;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class PlayerInteractionMixin {
    @Shadow
    private float currentBreakingProgress;
    @Shadow
    private boolean breakingBlock;
    @Shadow
    protected abstract void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

    @Shadow public abstract boolean breakBlock(BlockPos pos);

    @Shadow private int blockBreakingCooldown;

    @Shadow private float blockBreakingSoundCooldown;
    //speed up with early packet when progress>0.7
    @Inject(method = "updateBlockBreakingProgress",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/TutorialManager;onBlockBreaking(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;F)V",ordinal = 1,shift=At.Shift.AFTER),cancellable = true)
    public void fastbreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.currentBreakingProgress >= 0.72F&& HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
            this.breakingBlock = false;
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                this.breakBlock(pos);
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
            });
            this.currentBreakingProgress = 0.0F;
            this.blockBreakingSoundCooldown = 0.0F;
            this.blockBreakingCooldown = 1;
        }
    }
    @Inject(method = "attackBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",ordinal = 1,shift = At.Shift.AFTER),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, net.minecraft.block.BlockState blockState){
        Debug.stackTrace();
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE)) {
            float speed=blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, MinecraftClient.getInstance().player.getWorld(), pos);
            //speed>1.0f可以秒破 此处不调用
            if(!blockState.isAir()&&speed>0.72f&&speed<1.0f){
                this.breakingBlock = false;
                this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                    this.breakBlock(pos);
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                this.currentBreakingProgress = 0.0F;
                this.blockBreakingSoundCooldown = 0.0F;
                this.blockBreakingCooldown = 1;
            }
        }
    }

    @Inject(method = "getReachDistance",at = @At(value = "HEAD"),cancellable = true)
    public void widerReachDistance(CallbackInfoReturnable<Float> cir){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
            cir.setReturnValue(5.5f);
        }
    }

}
