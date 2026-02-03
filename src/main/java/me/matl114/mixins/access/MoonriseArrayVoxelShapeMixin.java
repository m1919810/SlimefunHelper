package me.matl114.mixins.access;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import me.matl114.accessors.moonrise.MoonriseVoxelShapeAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.shape.ArrayVoxelShape;
import net.minecraft.util.shape.VoxelSet;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ArrayVoxelShape.class)
public abstract class MoonriseArrayVoxelShapeMixin extends VoxelShape{
    protected MoonriseArrayVoxelShapeMixin(VoxelSet voxels) {
        super(voxels);
    }

    @Inject(method = "<init>(Lnet/minecraft/util/shape/VoxelSet;Lit/unimi/dsi/fastutil/doubles/DoubleList;Lit/unimi/dsi/fastutil/doubles/DoubleList;Lit/unimi/dsi/fastutil/doubles/DoubleList;)V", at = @At("RETURN"))
    private void moonriseinitCache(VoxelSet shape, DoubleList xPoints, DoubleList yPoints, DoubleList zPoints, CallbackInfo ci){
        MoonriseVoxelShapeAccess.of(this).moonrise$initCache();
    }


}
