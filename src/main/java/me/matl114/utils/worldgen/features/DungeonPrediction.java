package me.matl114.utils.worldgen.features;

import java.util.List;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;

/**
 * One {@code monster_room} placement attempt with its simulated room and chests.
 *
 * @param attempt zero-based attempt within the chunk's placed-feature stream
 * @param radiusX interior half-width on X (2 or 3); the walls are one block further out
 * @param radiusZ interior half-width on Z (2 or 3)
 */
public record DungeonPrediction(
        DungeonFeatureConfig feature,
        int chunkX,
        int chunkZ,
        int attempt,
        BlockPos origin,
        int radiusX,
        int radiusZ,
        List<DungeonChest> chests,
        Confidence confidence) {
    public DungeonPrediction {
        Objects.requireNonNull(feature);
        origin = Objects.requireNonNull(origin).toImmutable();
        chests = List.copyOf(chests);
        Objects.requireNonNull(confidence);
    }

    public enum Confidence {
        /** Spawner at the origin and the floor's mossy pattern matches every simulated draw. */
        VERIFIED,
        /** Spawner at the origin, but the floor pattern could not confirm the simulated draws. */
        SPAWNER_ONLY,
        /** No spawner (likely mined), but the floor's mossy pattern matches every simulated draw. */
        FLOOR_PATTERN,
        /**
         * Terrain was unavailable. Whether the room generated is unknown; the room is simulated with
         * solid floors and walls, and earlier attempts in the chunk are assumed to have failed.
         */
        SEED_ONLY,
        /**
         * Generated from the seed alone: {@code DungeonFeature.generate} succeeded on simulated
         * pre-feature terrain (noise, aquifers and carvers).
         */
        TERRAIN_SIMULATED;

        /** Whether the room was confirmed against blocks of the actual world. */
        public boolean observed() {
            return this == VERIFIED || this == SPAWNER_ONLY || this == FLOOR_PATTERN;
        }
    }
}
