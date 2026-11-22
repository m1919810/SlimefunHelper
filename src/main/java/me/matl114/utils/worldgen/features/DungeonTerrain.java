package me.matl114.utils.worldgen.features;

import net.minecraft.util.math.BlockPos;

/**
 * The block observations {@code DungeonFeature.generate} depends on. The simulator never reads the
 * world directly; callers adapt a loaded world or use {@link #UNKNOWN} for seed-only prediction.
 */
public interface DungeonTerrain {
    /** Terrain is never available, so every attempt is predicted under {@link #UNKNOWN}'s assumptions. */
    DungeonTerrain UNKNOWN = new DungeonTerrain() {
        @Override
        public boolean isObservable(BlockPos origin, int radiusX, int radiusZ) {
            return false;
        }

        @Override
        public boolean hasSpawner(BlockPos origin) {
            return false;
        }

        @Override
        public FloorState floor(BlockPos pos) {
            return FloorState.SOLID;
        }

        @Override
        public boolean isWallSolid(BlockPos pos) {
            return true;
        }
    };

    /** Whether every block of the room (walls included) can be read. */
    boolean isObservable(BlockPos origin, int radiusX, int radiusZ);

    /** Whether the generated world has a spawner at the attempt origin. */
    boolean hasSpawner(BlockPos origin);

    /**
     * The current floor block. Vanilla draws {@code nextInt(4)} for each floor block that stayed
     * solid; a floor block above a non-solid block is turned into cave air without a draw.
     */
    FloorState floor(BlockPos pos);

    /** Whether a wall block at the chest layer is solid; openings stay non-solid. */
    boolean isWallSolid(BlockPos pos);

    enum FloorState {
        /** Mossy cobblestone: the draw was {@code nextInt(4) != 0}. */
        MOSSY,
        /** Cobblestone: the draw was {@code nextInt(4) == 0}. */
        COBBLESTONE,
        /** A solid floor block whose draw outcome cannot be read. */
        SOLID,
        /** Not solid, so vanilla replaced it without drawing. */
        NOT_SOLID;

        boolean consumesDraw() {
            return this != NOT_SOLID;
        }
    }
}
