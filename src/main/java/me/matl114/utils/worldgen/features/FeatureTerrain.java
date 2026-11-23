package me.matl114.utils.worldgen.features;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * Generated blocks as a feature sees them, before it runs: unlike {@link DungeonTerrain}, which reads
 * a world the feature has already changed, this lets the simulator run {@code DungeonFeature.generate}
 * itself, including its terrain check.
 */
public interface FeatureTerrain {
    BlockState getBlockState(BlockPos pos);

    /**
     * A cheaper necessary condition for {@code getBlockState(pos).isSolid()}: it may answer
     * {@code true} for a block that ends up not solid, but never {@code false} for a solid one.
     */
    default boolean mayBeSolid(BlockPos pos) {
        return getBlockState(pos).isSolid();
    }

    /**
     * A cheap height bound checked before any terrain is read: {@code false} means the room's floor
     * at {@code floor} is assumed to be above the terrain, so the attempt fails. Implementations may
     * use an estimate with a safety margin; the default never rejects.
     */
    default boolean mayHaveTerrainAt(BlockPos floor) {
        return true;
    }

    /**
     * Announces that every block in the box (inclusive) is about to be read, so an implementation
     * can generate it in one batch instead of per block. Purely a performance hint.
     */
    default void prefetch(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    /** {@code Feature.notInBlockTagPredicate(BlockTags.FEATURES_CANNOT_REPLACE)} negated. */
    boolean cannotReplace(BlockState state);
}
