package me.matl114.utils.structures;

/**
 * The Xoroshiro-backed {@code WorldgenRandom} stream used by 1.21.11 structure
 * decoration. This deliberately implements the legacy Random API exposed by
 * WorldgenRandom, rather than XoroshiroRandomSource's bounded-int methods.
 */
public final class MinecraftWorldgenRandom {
    private static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    private static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final long MIX_A = -4658895280553007687L;
    private static final long MIX_B = -7723592293110705685L;

    private long seedLo;
    private long seedHi;

    MinecraftWorldgenRandom(long seed) {
        setSeed(seed);
    }

    public static MinecraftWorldgenRandom forDecorationChunk(
            long worldSeed, int chunkX, int chunkZ, StructureDecorationIndex.Index index) {
        MinecraftWorldgenRandom random = new MinecraftWorldgenRandom(worldSeed);
        long decorationSeed = random.setDecorationSeed(worldSeed, chunkX * 16, chunkZ * 16);
        random.setFeatureSeed(decorationSeed, index.structureIndex(), index.generationStep());
        return random;
    }

    public void setSeed(long seed) {
        long unmixedLo = seed ^ SILVER_RATIO_64;
        long unmixedHi = unmixedLo + GOLDEN_RATIO_64;
        seedLo = mixStafford13(unmixedLo);
        seedHi = mixStafford13(unmixedHi);
        if ((seedLo | seedHi) == 0L) {
            seedLo = GOLDEN_RATIO_64;
            seedHi = SILVER_RATIO_64;
        }
    }

    public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
        setSeed(worldSeed);
        long xMultiplier = nextLong() | 1L;
        long zMultiplier = nextLong() | 1L;
        long decorationSeed = ((long) blockX * xMultiplier + (long) blockZ * zMultiplier) ^ worldSeed;
        setSeed(decorationSeed);
        return decorationSeed;
    }

    public void setFeatureSeed(long decorationSeed, int structureIndex, int generationStep) {
        setSeed(decorationSeed + structureIndex + 10000L * generationStep);
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public long nextLootSeed() {
        // StructurePiece and StructureTemplate store random.nextLong() as LootTableSeed.
        return nextLong();
    }

    private int next(int bits) {
        long random = nextXoroshiroLong();
        return (int) (random >>> (64 - bits));
    }

    private long nextXoroshiroLong() {
        long lo = seedLo;
        long hi = seedHi;
        long result = Long.rotateLeft(lo + hi, 17) + lo;
        hi ^= lo;
        seedLo = Long.rotateLeft(lo, 49) ^ hi ^ (hi << 21);
        seedHi = Long.rotateLeft(hi, 28);
        return result;
    }

    private static long mixStafford13(long value) {
        value = (value ^ (value >>> 30)) * MIX_A;
        value = (value ^ (value >>> 27)) * MIX_B;
        return value ^ (value >>> 31);
    }
}
