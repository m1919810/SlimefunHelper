package me.matl114.mixins.access;

import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.accessors.moonrise.MoonriseChunkBlockCountingAccess;
import me.matl114.utils.CollisionUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registry;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ChunkSection.class)
public abstract class MoonriseChunkBlockCountingMixin implements MoonriseChunkBlockCountingAccess {
    @Shadow
    public abstract void calculateCounts();

    @Unique
    int specialCollidingBlocks = 0;

    public int getSpecialCollidingBlockCount() {
        return specialCollidingBlocks;
    }

    @Inject(method = "<init>(Lnet/minecraft/registry/Registry;)V", at = @At("RETURN"))
    private void calculateBlockCount(Registry biomeRegistry, CallbackInfo ci) {
        this.calculateCounts();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/chunk/ChunkSection;)V", at = @At("RETURN"))
    private void calculateBlockCount2(ChunkSection section, CallbackInfo ci) {
        this.calculateCounts();
    }

    @Inject(method = "readDataPacket", at = @At(value = "RETURN"))
    private void calculateBlockCount(PacketByteBuf buf, CallbackInfo ci) {
        this.calculateCounts();
    }

    @Inject(
            method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
            at =
                    @At(
                            value = "INVOKE",
                            shift = At.Shift.BEFORE,
                            target = "Lnet/minecraft/block/BlockState;getFluidState()Lnet/minecraft/fluid/FluidState;",
                            ordinal = 0))
    private void calculateSpecialCollidingBlocks(
            int x,
            int y,
            int z,
            BlockState state,
            boolean lock,
            CallbackInfoReturnable<BlockState> cir,
            @Local(ordinal = 1) BlockState blockState) {
        if (CollisionUtil.isSpecialCollidingBlock(blockState)) {
            --this.specialCollidingBlocks;
        }
        if (CollisionUtil.isSpecialCollidingBlock(state)) {
            ++this.specialCollidingBlocks;
        }
    }

    @Unique
    private int tmpSpecialCollidingBlocksCounter = 0;

    @Inject(
            method = "calculateCounts",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/chunk/PalettedContainer;count(Lnet/minecraft/world/chunk/PalettedContainer$Counter;)V",
                            shift = At.Shift.BEFORE))
    private void preBlockCount(CallbackInfo ci) {
        tmpSpecialCollidingBlocksCounter = 0;
    }

    @Inject(
            method = "calculateCounts",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/chunk/PalettedContainer;count(Lnet/minecraft/world/chunk/PalettedContainer$Counter;)V",
                            shift = At.Shift.AFTER))
    private void postBlockCount(CallbackInfo ci) {
        this.specialCollidingBlocks = tmpSpecialCollidingBlocksCounter;
        tmpSpecialCollidingBlocksCounter = 0;
    }

    @ModifyArg(
            method = "calculateCounts",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/chunk/PalettedContainer;count(Lnet/minecraft/world/chunk/PalettedContainer$Counter;)V"))
    private PalettedContainer.Counter addCountingWrapper(PalettedContainer.Counter counter) {
        return (PalettedContainer.Counter) (acc, i) -> {
            counter.accept(acc, i);
            if (CollisionUtil.isSpecialCollidingBlock((BlockState) acc)) {
                tmpSpecialCollidingBlocksCounter += i;
            }
        };
    }
}
