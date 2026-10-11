package me.matl114.utils.structures;

final class JavaLegacyRandom {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private long seed;

    JavaLegacyRandom(long seed) {
        setSeed(seed);
    }

    private void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
    }

    private int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    int nextInt() {
        return next(32);
    }

    int nextInt(int bound) {
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

    long nextLong() {
        return ((long) next(32) << 32) + (long) next(32);
    }

    boolean nextBoolean() {
        return next(1) != 0;
    }

    float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }
}
