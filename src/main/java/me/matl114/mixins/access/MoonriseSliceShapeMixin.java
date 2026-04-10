package me.matl114.mixins.access;

import me.matl114.accessors.moonrise.MoonriseVoxelShapeAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.SlicedVoxelShape;
import net.minecraft.util.shape.VoxelSet;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = SlicedVoxelShape.class, priority = 2000)
public abstract class MoonriseSliceShapeMixin extends VoxelShape {
    protected MoonriseSliceShapeMixin(VoxelSet voxels) {
        super(voxels);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void moonriseinitCache(VoxelShape shape, Direction.Axis axis, int sliceWidth, CallbackInfo ci) {
        MoonriseVoxelShapeAccess.of(this).moonrise$initCache();
    }
}
