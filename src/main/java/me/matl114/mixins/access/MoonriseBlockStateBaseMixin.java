package me.matl114.mixins.access;

import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MoonriseBlockStateBaseMixin implements MoonriseBlockStateBaseAccess {
    @Unique
    private VoxelShape constantCollisionShape;

    @Shadow
    public abstract VoxelShape getCollisionShape(BlockView world, BlockPos pos, ShapeContext context);

    @Unique
    private void initCache0() {
        try {
            constantCollisionShape = getCollisionShape(null, null, null);
        } catch (Throwable e) {
            constantCollisionShape = null;
        }
    }

    @Inject(method = "initShapeCache", at = @At("RETURN"))
    public void onInitCache(CallbackInfo ci) {
        initCache0();
    }

    @Unique
    public VoxelShape moonrise$getConstantCollisionShape() {
        return this.constantCollisionShape;
    }
}
