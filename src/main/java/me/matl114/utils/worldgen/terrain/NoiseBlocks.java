package me.matl114.utils.worldgen.terrain;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;

/**
 * Noise blocks of a square column region over a height band, with a byte palette. Noise only
 * produces the default block, air and the aquifer fluids, so a full chunk needs about 100 KB instead
 * of a reference per block. Reads outside {@link #covers} are the caller's responsibility.
 */
public final class NoiseBlocks {
    private final BlockState[] palette;
    private final byte[] indices;
    private final int width;
    private final int minY;
    private final int maxY;

    private NoiseBlocks(BlockState[] palette, byte[] indices, int width, int minY, int maxY) {
        this.palette = palette;
        this.indices = indices;
        this.width = width;
        this.minY = minY;
        this.maxY = maxY;
    }

    /** Lowest covered Y (inclusive). */
    public int minY() {
        return minY;
    }

    /** Highest covered Y (exclusive). */
    public int maxY() {
        return maxY;
    }

    public boolean covers(int y) {
        return y >= minY && y < maxY;
    }

    /** The block at region-local {@code localX}/{@code localZ} and absolute {@code y}. */
    public BlockState get(int localX, int y, int localZ) {
        return palette[indices[((y - minY) * width + localZ) * width + localX] & 0xFF];
    }

    static final class Builder {
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Integer> ids = new IdentityHashMap<>();
        private final byte[] indices;
        private final int width;
        private final int minY;
        private final int maxY;

        Builder(int width, int minY, int maxY) {
            this.indices = new byte[width * width * (maxY - minY)];
            this.width = width;
            this.minY = minY;
            this.maxY = maxY;
        }

        void set(int localX, int y, int localZ, BlockState state) {
            Integer id = ids.get(state);
            if (id == null) {
                id = palette.size();
                if (id > 0xFF) {
                    throw new IllegalStateException("More than 256 distinct noise block states");
                }
                palette.add(state);
                ids.put(state, id);
            }
            indices[((y - minY) * width + localZ) * width + localX] = (byte) (int) id;
        }

        NoiseBlocks build() {
            return new NoiseBlocks(palette.toArray(BlockState[]::new), indices, width, minY, maxY);
        }
    }
}
