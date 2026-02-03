package me.matl114.accessors.moonrise;

import me.matl114.utils.impl.world.CachedShapeData;
import me.matl114.utils.impl.world.CachedToAABBs;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

public interface MoonriseVoxelShapeAccess {
    public double moonrise$offsetX();

    public double moonrise$offsetY();

    public double moonrise$offsetZ();

    public double[] moonrise$rootCoordinatesX();

    public double[] moonrise$rootCoordinatesY();

    public double[] moonrise$rootCoordinatesZ();


    // rets null if not possible to represent this shape as one AABB
    public Box moonrise$getSingleAABBRepresentation();
    CachedToAABBs moonrise$cachedToAABBs();
    public void moonriss$setCachedToAABBs(CachedToAABBs aabBs);

    public boolean moonrise$isFullBlock();

    public CachedShapeData moonrise$getCachedVoxelData();

    // ONLY USE INTERNALLY, ONLY FOR INITIALISING IN CONSTRUCTOR: VOXELSHAPES ARE STATIC
    public void moonrise$initCache();
    static MoonriseVoxelShapeAccess of(VoxelShape voxel){
        return (MoonriseVoxelShapeAccess) (Object)voxel;
    }
}
