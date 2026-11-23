package me.matl114.utils.worldgen.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Lazily generated pre-feature terrain, bounded by a least-recently-used chunk cache. Nothing is
 * computed for a chunk until one of its blocks is read, so a room inside its own chunk never touches
 * the neighbours. The primary chunk (the one being simulated) computes its noise in one full-chunk
 * pass; any other chunk computes only the 4x4-block cell columns that are read, since a room reaches
 * at most four blocks (one cell) into a neighbour. Before a room is read, {@link #prefetch} samples
 * all the cells it needs in each neighbour with a single sampler: building a sampler costs more than
 * filling a few banded cells, so per-cell samplers were dominated by setup. Noise is sampled only over the job's height band
 * (see {@link #setPrimaryChunk(int, int, int, int)}). Carver layers are computed on the first read of
 * a final (carved) block, so noise-only checks never pay for carving.
 * Not thread-safe: use one per worker thread.
 */
public final class TerrainChunkCache {
    private static final BlockState VOID_AIR = Blocks.VOID_AIR.getDefaultState();
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final int WORLD_MIN_Y = OverworldTerrainContext.BOTTOM_Y;
    private static final int WORLD_MAX_Y = OverworldTerrainContext.BOTTOM_Y + OverworldTerrainContext.HEIGHT;

    private final OverworldTerrainContext context;
    private final Map<Long, LazyChunk> chunks;
    private long noiseChunks;
    private long noiseCells;
    private long cellSamplers;
    private long carveLayers;
    private long aquifers;
    private long bandMisses;
    private long primaryChunk = Long.MAX_VALUE;
    /** Height band sampled for the current job, {@code [bandMinY, bandMaxY)}; full height by default. */
    private int bandMinY = WORLD_MIN_Y;

    private int bandMaxY = WORLD_MAX_Y;

    public TerrainChunkCache(OverworldTerrainContext context, int capacity) {
        this.context = Objects.requireNonNull(context);
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.chunks = new LinkedHashMap<>(capacity * 2, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, LazyChunk> eldest) {
                return size() > capacity;
            }
        };
    }

    public OverworldTerrainContext context() {
        return context;
    }

    /** The block after the {@code NOISE} and {@code CARVERS} statuses. */
    public BlockState getBlockState(BlockPos pos) {
        if (context.heightLimitView().isOutOfHeightLimit(pos.getY())) {
            return VOID_AIR;
        }
        LazyChunk chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
        // NOISE status: what the density functions and aquifer put here before carving.
        BlockState noise = chunk.noise(pos.getX(), pos.getY(), pos.getZ());
        // CARVERS status, replayed on an all-stone chunk (see OverworldTerrainContext.carveLayer):
        // stone means no carver reached this point; LAVA means a carver reached it below the lava
        // level; CARVED means a carver reached it and would ask the aquifer what to place.
        BlockState carved = chunk.carveLayer().getBlockState(pos);
        // Carver.carveAtPoint only replaces blocks in #overworld_carver_replaceables; air, fluids
        // and anything else the noise produced keep their state, exactly as in vanilla.
        if (carved == STONE || !context.carverReplaceable(noise)) {
            return noise;
        }
        if (carved == OverworldTerrainContext.CARVED) {
            // Deferred aquifer query: the same call Carver.getState makes, done only now that this
            // point is actually read. Building the aquifer is the expensive part of carving, and
            // rooms in solid rock usually never read a carved point. A null answer means vanilla
            // leaves the block untouched.
            carved = chunk.aquifer(pos.getY())
                    .apply(new DensityFunction.UnblendedNoisePos(pos.getX(), pos.getY(), pos.getZ()), 0.0);
            return carved == null ? noise : carved;
        }
        return carved;
    }

    /**
     * Whether the block may be solid after carving, from noise alone. Carvers only replace blocks
     * with air or fluid, so a block that is not solid in the noise is not solid afterwards either.
     */
    public boolean mayBeSolid(BlockPos pos) {
        if (context.heightLimitView().isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        LazyChunk chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk.noise(pos.getX(), pos.getY(), pos.getZ()).isSolid();
    }

    /** Marks the chunk whose attempts are simulated next; it gets a full-chunk, full-height noise pass. */
    public void setPrimaryChunk(int chunkX, int chunkZ) {
        setPrimaryChunk(chunkX, chunkZ, WORLD_MIN_Y, WORLD_MAX_Y);
    }

    /**
     * Marks the chunk whose attempts are simulated next, sampling noise only over
     * {@code [minY, maxY)}: the heights the next job can read. Noise computed for other jobs is reused
     * when it covers a read; a read outside every computed band recomputes that chunk or cell over
     * the union. A band that turns out too small therefore costs time but never changes a block.
     */
    public void setPrimaryChunk(int chunkX, int chunkZ, int minY, int maxY) {
        primaryChunk = ChunkPos.toLong(chunkX, chunkZ);
        bandMinY = Math.max(WORLD_MIN_Y, minY);
        bandMaxY = Math.min(WORLD_MAX_Y, Math.max(maxY, bandMinY + 1));
    }

    private LazyChunk chunk(int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(ChunkPos.toLong(chunkX, chunkZ), key -> new LazyChunk(chunkX, chunkZ));
    }

    /**
     * The highest preliminary surface estimate over a 5x5 grid covering the chunk (corners and every
     * 4 blocks), computed once per chunk without any noise sampling.
     */
    public int surfaceEstimate(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ).surfaceEstimate();
    }

    /** Chunks whose noise, and chunks whose carver layer, this cache has computed (including evicted). */
    public long noiseChunks() {
        return noiseChunks;
    }

    public long carveLayers() {
        return carveLayers;
    }

    /** Aquifer samplers built only to resolve carved points (primary chunks reuse their noise sampler). */
    public long aquifers() {
        return aquifers;
    }

    /** Cell columns computed for neighbour chunks (16 make up one chunk). */
    public long noiseCells() {
        return noiseCells;
    }

    /** Samplers built for neighbour cells; batching makes this smaller than {@link #noiseCells()}. */
    public long cellSamplers() {
        return cellSamplers;
    }

    /**
     * Samples, for each non-primary chunk the box overlaps, every cell the box needs that is not
     * already available over the box's heights, using one sampler per chunk (the smallest square of
     * cells covering them, kept inside the chunk). The primary chunk gets its full-chunk pass lazily.
     */
    public void prefetch(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (maxY < WORLD_MIN_Y || minY >= WORLD_MAX_Y) {
            return;
        }
        minY = Math.max(minY, WORLD_MIN_Y);
        maxY = Math.min(maxY, WORLD_MAX_Y - 1);
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (ChunkPos.toLong(chunkX, chunkZ) == primaryChunk) {
                    continue;
                }
                chunk(chunkX, chunkZ)
                        .prefetchCells(
                                Math.max(minX, chunkX * 16),
                                Math.max(minZ, chunkZ * 16),
                                Math.min(maxX, chunkX * 16 + 15),
                                Math.min(maxZ, chunkZ * 16 + 15),
                                minY,
                                maxY);
            }
        }
    }

    /**
     * Reads outside an already computed band that forced a wider recomputation. Chunks and cells are
     * shared between jobs whose bands differ, so a later job may need heights an earlier one skipped.
     */
    public long bandMisses() {
        return bandMisses;
    }

    /** Noise for some cells of a chunk; {@code startX}/{@code startZ} is the region's first block. */
    private record CellRegion(NoiseBlocks blocks, int startX, int startZ) {}

    private final class LazyChunk {
        private final int chunkX;
        private final int chunkZ;
        private NoiseBlocks noise;
        /** Neighbour cells by index; several may share one batch region. */
        private CellRegion[] cells;

        private ProtoChunk carveLayer;
        private AquiferSampler aquifer;
        /** Heights {@link #aquifer} was built for, {@code [aquiferMinY, aquiferMaxY)}. */
        private int aquiferMinY;

        private int aquiferMaxY;
        private int surfaceEstimate = Integer.MIN_VALUE;

        private LazyChunk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private BlockState noise(int x, int y, int z) {
            if (noise != null && !noise.covers(y)) {
                // Computed for another job's band that misses this height: recompute over the union.
                bandMisses++;
                sample(
                        Math.min(noise.minY(), Math.min(bandMinY, y)),
                        Math.max(noise.maxY(), Math.max(bandMaxY, y + 1)));
            } else if (noise == null && ChunkPos.toLong(chunkX, chunkZ) == primaryChunk) {
                sample(Math.min(bandMinY, y), Math.max(bandMaxY, y + 1));
            }
            if (noise != null) {
                return noise.get(x & 15, y, z & 15);
            }
            int cellWidth = context.cellWidth();
            int cellsPerSide = 16 / cellWidth;
            int localX = x & 15;
            int localZ = z & 15;
            int cell = (localZ / cellWidth) * cellsPerSide + localX / cellWidth;
            if (cells == null) {
                cells = new CellRegion[cellsPerSide * cellsPerSide];
            }
            CellRegion current = cells[cell];
            if (current == null || !current.blocks().covers(y)) {
                // Not prefetched (or prefetched for other heights): sample just this cell.
                int minY = Math.min(bandMinY, y);
                int maxY = Math.max(bandMaxY, y + 1);
                if (current != null) {
                    bandMisses++;
                    minY = Math.min(minY, current.blocks().minY());
                    maxY = Math.max(maxY, current.blocks().maxY());
                }
                int cellStartX = x - (localX % cellWidth);
                int cellStartZ = z - (localZ % cellWidth);
                current = new CellRegion(context.noiseCell(cellStartX, cellStartZ, minY, maxY), cellStartX, cellStartZ);
                cells[cell] = current;
                noiseCells++;
                cellSamplers++;
            }
            return current.blocks().get(x - current.startX(), y, z - current.startZ());
        }

        /**
         * Batch-samples the cells covering the block rectangle {@code [x0, x1] x [z0, z1]} (inside
         * this chunk) over {@code [minY, maxY]}, unless all of them are already available.
         */
        private void prefetchCells(int x0, int z0, int x1, int z1, int minY, int maxY) {
            if (noise != null && noise.covers(minY) && noise.covers(maxY)) {
                return;
            }
            int cellWidth = context.cellWidth();
            int cellsPerSide = 16 / cellWidth;
            int cellX0 = (x0 & 15) / cellWidth;
            int cellX1 = (x1 & 15) / cellWidth;
            int cellZ0 = (z0 & 15) / cellWidth;
            int cellZ1 = (z1 & 15) / cellWidth;
            if (cells == null) {
                cells = new CellRegion[cellsPerSide * cellsPerSide];
            }
            boolean missing = false;
            for (int cx = cellX0; cx <= cellX1 && !missing; cx++) {
                for (int cz = cellZ0; cz <= cellZ1; cz++) {
                    CellRegion region = cells[cz * cellsPerSide + cx];
                    if (region == null
                            || !region.blocks().covers(minY)
                            || !region.blocks().covers(maxY)) {
                        missing = true;
                        break;
                    }
                }
            }
            if (!missing) {
                return;
            }
            // One square sampler over the needed cells, shifted back so it stays inside this chunk.
            int size = Math.max(cellX1 - cellX0, cellZ1 - cellZ0) + 1;
            int startCellX = Math.min(cellX0, cellsPerSide - size);
            int startCellZ = Math.min(cellZ0, cellsPerSide - size);
            int startX = chunkX * 16 + startCellX * cellWidth;
            int startZ = chunkZ * 16 + startCellZ * cellWidth;
            NoiseBlocks blocks =
                    context.noiseCells(startX, startZ, size, Math.min(bandMinY, minY), Math.max(bandMaxY, maxY + 1));
            cellSamplers++;
            for (int cx = startCellX; cx < startCellX + size; cx++) {
                for (int cz = startCellZ; cz < startCellZ + size; cz++) {
                    int index = cz * cellsPerSide + cx;
                    CellRegion previous = cells[index];
                    // Keep an existing cell that already covers more heights than this batch.
                    if (previous == null
                            || previous.blocks().minY() > blocks.minY()
                            || previous.blocks().maxY() < blocks.maxY()) {
                        cells[index] = new CellRegion(blocks, startX, startZ);
                        noiseCells++;
                    }
                }
            }
        }

        /**
         * Samples the whole chunk over {@code [minY, maxY)}. The sampler already built this chunk's
         * aquifer for that band; keep it so resolving carved points never needs a second sampler.
         * {@link #aquifer(int)} falls back to a full-height aquifer for reads outside the band.
         */
        private void sample(int minY, int maxY) {
            OverworldTerrainContext.ChunkNoise sampled = context.sampleChunk(chunkX, chunkZ, minY, maxY);
            noise = sampled.blocks();
            if (aquifer == null || aquiferMinY > noise.minY() || aquiferMaxY < noise.maxY()) {
                aquifer = sampled.aquifer();
                aquiferMinY = noise.minY();
                aquiferMaxY = noise.maxY();
            }
            noiseChunks++;
            cells = null;
        }

        private int surfaceEstimate() {
            if (surfaceEstimate == Integer.MIN_VALUE) {
                double highest = Double.NEGATIVE_INFINITY;
                for (int dx = 0; dx <= 16; dx += 4) {
                    for (int dz = 0; dz <= 16; dz += 4) {
                        highest = Math.max(
                                highest,
                                context.preliminarySurface(
                                        chunkX * 16 + Math.min(dx, 15), chunkZ * 16 + Math.min(dz, 15)));
                    }
                }
                surfaceEstimate = (int) Math.ceil(highest);
            }
            return surfaceEstimate;
        }

        /**
         * The aquifer for resolving a carved point at {@code y}: the primary chunk's noise sampler's
         * when it covers {@code y}, otherwise a full-height one built on the first such read
         * (neighbour chunks only have single-cell samplers, which cannot answer for the chunk).
         */
        private AquiferSampler aquifer(int y) {
            if (aquifer == null || y < aquiferMinY || y >= aquiferMaxY) {
                aquifer = context.aquifer(chunkX, chunkZ);
                aquiferMinY = WORLD_MIN_Y;
                aquiferMaxY = WORLD_MAX_Y;
                aquifers++;
            }
            return aquifer;
        }

        /**
         * Which points carvers reach in this chunk. It does not depend on the chunk's noise, so it
         * is computed once and combined with noise per block in {@link #getBlockState}.
         */
        private ProtoChunk carveLayer() {
            if (carveLayer == null) {
                carveLayer = context.carveLayer(new ChunkPos(chunkX, chunkZ));
                carveLayers++;
            }
            return carveLayer;
        }
    }
}
