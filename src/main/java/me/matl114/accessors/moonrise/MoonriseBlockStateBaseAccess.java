package me.matl114.accessors.moonrise;

import net.minecraft.block.AbstractBlock;
import net.minecraft.util.shape.VoxelShape;

public interface MoonriseBlockStateBaseAccess {
    public VoxelShape moonrise$getConstantCollisionShape();

    default boolean isConstantCollisionShapeEmpty(){
        return moonrise$getConstantCollisionShape() == null || moonrise$getConstantCollisionShape().isEmpty();
    }

    static MoonriseBlockStateBaseAccess of(AbstractBlock.AbstractBlockState state){
        return (MoonriseBlockStateBaseAccess) state;
    }
}
