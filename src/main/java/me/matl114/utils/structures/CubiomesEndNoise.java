package me.matl114.utils.structures;

/**
 * The 1.21 End biome and terrain checks used by Cubiomes for End Cities.
 *
 * <p>This class intentionally owns the noise state for one world seed. It can
 * therefore be reused for every candidate in a generated area without using
 * client chunk or biome data.</p>
 */
public final class CubiomesEndNoise {
    private static final long MASK_48 = (1L << 48) - 1L;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final double SKEW = 0.5D * (Math.sqrt(3.0D) - 1.0D);
    private static final double UNSKEW = (3.0D - Math.sqrt(3.0D)) / 6.0D;

    private final long seed;
    private final PerlinNoise endBiomeNoise;
    private final SurfaceNoise surfaceNoise;

    public CubiomesEndNoise(long seed) {
        this.seed = seed;

        Random48 endRandom = new Random48(seed);
        endRandom.skip(17_292L);
        this.endBiomeNoise = new PerlinNoise(endRandom);
        this.surfaceNoise = new SurfaceNoise(seed);
    }

    public long seed() {
        return seed;
    }

    /** Returns whether a spread candidate is a valid 1.21 End City position. */
    public boolean isViableEndCity(long chunkX, long chunkZ) {
        int x = Math.toIntExact(chunkX);
        int z = Math.toIntExact(chunkZ);
        return isEndCityBiome(x, z) && getEndCityTerrainHeight(x, z) >= 60;
    }

    /** Cubiomes mapEndBiome() at the 1:16 chunk scale. */
    public boolean isEndCityBiome(int chunkX, int chunkZ) {
        long hx = 2L * chunkX + 1L;
        long hz = 2L * chunkZ + 1L;
        // mapEndBiome() keeps the central-island guard in chunk coordinates;
        // hx/hz are only the block-center coordinates used for biome mapping.
        long radiusSquared = (long) chunkX * chunkX + (long) chunkZ * chunkZ;
        if (radiusSquared <= 4096L) {
            return false;
        }
        if ((int) radiusSquared < 0) {
            return false;
        }

        int elevationWidth = 27;
        int[] elevation = new int[elevationWidth * elevationWidth];
        for (int j = 0; j < elevationWidth; j++) {
            for (int i = 0; i < elevationWidth; i++) {
                long rx = (long) chunkX + i - 12L;
                long rz = (long) chunkZ + j - 12L;
                long rsq = rx * rx + rz * rz;
                int value = 0;
                if (rsq > 4096L && sampleSimplex2D(endBiomeNoise, rx, rz) < (double) -0.9F) {
                    float island = Math.abs((float) rx) * 3439.0F + Math.abs((float) rz) * 147.0F;
                    value = ((int) island % 13) + 9;
                    value *= value;
                }
                elevation[j * elevationWidth + i] = value;
            }
        }

        int biome = getEndBiome((int) hx, (int) hz, elevation, elevationWidth, chunkX, chunkZ);
        return biome == EndBiome.HIGHLANDS || biome == EndBiome.MIDLANDS;
    }

    /** Cubiomes isViableEndCityTerrain(), including the rotation-dependent corners. */
    public boolean isEndCityTerrain(int chunkX, int chunkZ) {
        return getEndCityTerrainHeight(chunkX, chunkZ) >= 60;
    }

    /** Returns Cubiomes' minimum of the four rotation-dependent surface samples, or zero if too low. */
    public int getEndCityTerrainHeight(int chunkX, int chunkZ) {
        int blockX = chunkX * 16 + 7;
        int blockZ = chunkZ * 16 + 7;
        int cellX = blockX >> 3;
        int cellZ = blockZ >> 3;

        double[][][] columns = new double[3][3][4];
        sampleNoiseColumnEnd(columns[0][0], cellX, cellZ);
        sampleNoiseColumnEnd(columns[0][1], cellX, cellZ + 1);
        sampleNoiseColumnEnd(columns[1][0], cellX + 1, cellZ);
        sampleNoiseColumnEnd(columns[1][1], cellX + 1, cellZ + 1);

        int h00 = getSurfaceHeight(
                columns[0][0], columns[0][1], columns[1][0], columns[1][1], (blockX & 7) / 8.0D, (blockZ & 7) / 8.0D);
        int h01;
        int h10;
        int h11;

        Random48 chunkRandom = new Random48(chunkGenerateRandom(seed, chunkX, chunkZ));
        switch (chunkRandom.nextInt(4)) {
            case 0 -> {
                sampleNoiseColumnEnd(columns[0][2], cellX, cellZ + 2);
                sampleNoiseColumnEnd(columns[1][2], cellX + 1, cellZ + 2);
                sampleNoiseColumnEnd(columns[2][0], cellX + 2, cellZ);
                sampleNoiseColumnEnd(columns[2][1], cellX + 2, cellZ + 1);
                sampleNoiseColumnEnd(columns[2][2], cellX + 2, cellZ + 2);
                h01 = getSurfaceHeight(
                        columns[0][1],
                        columns[0][2],
                        columns[1][1],
                        columns[1][2],
                        (blockX & 7) / 8.0D,
                        ((blockZ + 5) & 7) / 8.0D);
                h10 = getSurfaceHeight(
                        columns[1][0],
                        columns[1][1],
                        columns[2][0],
                        columns[2][1],
                        ((blockX + 5) & 7) / 8.0D,
                        (blockZ & 7) / 8.0D);
                h11 = getSurfaceHeight(
                        columns[1][1],
                        columns[1][2],
                        columns[2][1],
                        columns[2][2],
                        ((blockX + 5) & 7) / 8.0D,
                        ((blockZ + 5) & 7) / 8.0D);
            }
            case 1 -> {
                sampleNoiseColumnEnd(columns[0][2], cellX, cellZ + 2);
                sampleNoiseColumnEnd(columns[1][2], cellX + 1, cellZ + 2);
                h01 = getSurfaceHeight(
                        columns[0][1],
                        columns[0][2],
                        columns[1][1],
                        columns[1][2],
                        (blockX & 7) / 8.0D,
                        ((blockZ + 5) & 7) / 8.0D);
                h10 = getSurfaceHeight(
                        columns[0][0],
                        columns[0][1],
                        columns[1][0],
                        columns[1][1],
                        ((blockX - 5) & 7) / 8.0D,
                        (blockZ & 7) / 8.0D);
                h11 = getSurfaceHeight(
                        columns[0][1],
                        columns[0][2],
                        columns[1][1],
                        columns[1][2],
                        ((blockX - 5) & 7) / 8.0D,
                        ((blockZ + 5) & 7) / 8.0D);
            }
            case 2 -> {
                h01 = getSurfaceHeight(
                        columns[0][0],
                        columns[0][1],
                        columns[1][0],
                        columns[1][1],
                        (blockX & 7) / 8.0D,
                        ((blockZ - 5) & 7) / 8.0D);
                h10 = getSurfaceHeight(
                        columns[0][0],
                        columns[0][1],
                        columns[1][0],
                        columns[1][1],
                        ((blockX - 5) & 7) / 8.0D,
                        (blockZ & 7) / 8.0D);
                h11 = getSurfaceHeight(
                        columns[0][0],
                        columns[0][1],
                        columns[1][0],
                        columns[1][1],
                        ((blockX - 5) & 7) / 8.0D,
                        ((blockZ - 5) & 7) / 8.0D);
            }
            case 3 -> {
                sampleNoiseColumnEnd(columns[2][0], cellX + 2, cellZ);
                sampleNoiseColumnEnd(columns[2][1], cellX + 2, cellZ + 1);
                h01 = getSurfaceHeight(
                        columns[0][0],
                        columns[0][1],
                        columns[1][0],
                        columns[1][1],
                        (blockX & 7) / 8.0D,
                        ((blockZ - 5) & 7) / 8.0D);
                h10 = getSurfaceHeight(
                        columns[1][0],
                        columns[1][1],
                        columns[2][0],
                        columns[2][1],
                        ((blockX + 5) & 7) / 8.0D,
                        (blockZ & 7) / 8.0D);
                h11 = getSurfaceHeight(
                        columns[1][0],
                        columns[1][1],
                        columns[2][0],
                        columns[2][1],
                        ((blockX + 5) & 7) / 8.0D,
                        ((blockZ - 5) & 7) / 8.0D);
            }
            default -> throw new AssertionError("nextInt(4) returned outside its range");
        }

        int height = Math.min(Math.min(h00, h01), Math.min(h10, h11));
        return height >= 60 ? height : 0;
    }

    private void sampleNoiseColumnEnd(double[] column, int x, int z) {
        long radiusSquared = (long) x * x + (long) z * z;
        if (radiusSquared < 0L || (int) radiusSquared < 0) {
            java.util.Arrays.fill(column, Double.NaN);
            return;
        }
        double depth = getEndHeightNoise(x, z) - 8.0F;
        for (int y = 15; y <= 18; y++) {
            double lowerDrop = lowerDrop(y);
            if (lowerDrop == 0.0D) {
                column[y - 15] = -30.0D;
                continue;
            }
            double noise = sampleSurfaceNoiseBetween(surfaceNoise, x, y, z, -128.0D, 128.0D);
            double clamped = noise + depth;
            clamped = lerp(upperDrop(y), -3000.0D, clamped);
            column[y - 15] = lerp(lowerDrop, -30.0D, clamped);
        }
    }

    private float getEndHeightNoise(int x, int z) {
        int hx = x / 2;
        int hz = z / 2;
        int oddX = x % 2;
        int oddZ = z % 2;
        long height = 64L * ((long) x * x + (long) z * z);

        for (int j = -12; j <= 12; j++) {
            for (int i = -12; i <= 12; i++) {
                long rx = (long) hx + i;
                long rz = (long) hz + j;
                long radiusSquared = rx * rx + rz * rz;
                if (radiusSquared <= 4096L || sampleSimplex2D(endBiomeNoise, rx, rz) >= (double) -0.9F) {
                    continue;
                }
                float island = Math.abs((float) rx) * 3439.0F + Math.abs((float) rz) * 147.0F;
                int v = ((int) island % 13) + 9;
                rx = oddX - i * 2L;
                rz = oddZ - j * 2L;
                long noise = (rx * rx + rz * rz) * (long) v * v;
                if (noise < height) {
                    height = noise;
                }
            }
        }

        float result = 100.0F - (float) Math.sqrt((float) height);
        return Math.max(-100.0F, Math.min(80.0F, result));
    }

    private static int getEndBiome(int hx, int hz, int[] elevation, int width, long chunkX, long chunkZ) {
        int[] distances = {
            625, 529, 441, 361, 289, 225, 169, 121, 81, 49, 25, 9, 1,
            1, 9, 25, 49, 81, 121, 169, 225, 289, 361, 441, 529, 625
        };
        int xOffset = hx < 0 ? 1 : 0;
        int zOffset = hz < 0 ? 1 : 0;
        int height = Math.abs(hx) <= 15 && Math.abs(hz) <= 15 ? 64 * (hx * hx + hz * hz) : 14_401;
        int startX = (int) (hx / 2L - chunkX);
        int startZ = (int) (hz / 2L - chunkZ);
        for (int j = 0; j < 25; j++) {
            for (int i = 0; i < 25; i++) {
                int value = elevation[(startZ + j) * width + startX + i];
                if (value != 0) {
                    int distance = distances[i + xOffset] + distances[j + zOffset];
                    height = Math.min(height, distance * value);
                }
            }
        }
        if (height < 3600) {
            return EndBiome.HIGHLANDS;
        }
        if (height <= 10000) {
            return EndBiome.MIDLANDS;
        }
        if (height <= 14400) {
            return EndBiome.BARRENS;
        }
        return EndBiome.SMALL_ISLANDS;
    }

    private static double sampleSurfaceNoiseBetween(
            SurfaceNoise noise, int x, int y, int z, double noiseMin, double noiseMax) {
        double persist = 1.0D / 32768.0D;
        double amplitude = 64.0D;
        double xzScale = 684.412D * noise.xzScale;
        double yScale = 684.412D * noise.yScale;
        double min = 0.0D;
        double max = 0.0D;
        for (int i = 15; i >= 0; i--) {
            double dx = x * xzScale * persist;
            double dz = z * xzScale * persist;
            double sy = yScale * persist;
            double dy = y * sy;
            min += samplePerlin(noise.min[i], dx, dy, dz, sy, dy) * amplitude;
            max += samplePerlin(noise.max[i], dx, dy, dz, sy, dy) * amplitude;
            if (min - amplitude > noiseMax && max - amplitude > noiseMax) {
                return noiseMax;
            }
            if (min + amplitude < noiseMin && max + amplitude < noiseMin) {
                return noiseMin;
            }
            amplitude *= 0.5D;
            persist *= 2.0D;
        }

        double xzStep = xzScale / noise.xzFactor;
        double yStep = yScale / noise.yFactor;
        double main = 0.5D;
        persist = 1.0D / 128.0D;
        amplitude = 0.05D * 128.0D;
        for (int i = 7; i >= 0; i--) {
            double dx = x * xzStep * persist;
            double dz = z * xzStep * persist;
            double sy = yStep * persist;
            double dy = y * sy;
            main += samplePerlin(noise.main[i], dx, dy, dz, sy, dy) * amplitude;
            if (main - amplitude > 1.0D) {
                return max;
            }
            if (main + amplitude < 0.0D) {
                return min;
            }
            amplitude *= 0.5D;
            persist *= 2.0D;
        }
        return clampedLerp(main, min, max);
    }

    private static int getSurfaceHeight(
            double[] ncol00, double[] ncol01, double[] ncol10, double[] ncol11, double dx, double dz) {
        for (int cellY = 17; cellY >= 15; cellY--) {
            int index = cellY - 15;
            double v000 = ncol00[index];
            double v001 = ncol01[index];
            double v100 = ncol10[index];
            double v101 = ncol11[index];
            double v010 = ncol00[index + 1];
            double v011 = ncol01[index + 1];
            double v110 = ncol10[index + 1];
            double v111 = ncol11[index + 1];
            for (int y = 3; y >= 0; y--) {
                double dy = y / 4.0D;
                double value = lerp3(dy, dx, dz, v000, v010, v100, v110, v001, v011, v101, v111);
                if (value > 0.0D) {
                    return cellY * 4 + y;
                }
            }
        }
        return 0;
    }

    private static double sampleSimplex2D(PerlinNoise noise, double x, double y) {
        double skew = (x + y) * SKEW;
        int hx = (int) Math.floor(x + skew);
        int hz = (int) Math.floor(y + skew);
        double unskewed = (hx + hz) * UNSKEW;
        double x0 = x - (hx - unskewed);
        double y0 = y - (hz - unskewed);
        int offX = x0 > y0 ? 1 : 0;
        int offZ = offX == 0 ? 1 : 0;
        double x1 = x0 - offX + UNSKEW;
        double y1 = y0 - offZ + UNSKEW;
        double x2 = x0 - 1.0D + 2.0D * UNSKEW;
        double y2 = y0 - 1.0D + 2.0D * UNSKEW;
        int gi0 = noise.permutation[255 & hz];
        int gi1 = noise.permutation[255 & (hz + offZ)];
        int gi2 = noise.permutation[255 & (hz + 1)];
        gi0 = noise.permutation[255 & (gi0 + hx)];
        gi1 = noise.permutation[255 & (gi1 + hx + offX)];
        gi2 = noise.permutation[255 & (gi2 + hx + 1)];
        return 70.0D
                * (simplexGradient(gi0 % 12, x0, y0)
                        + simplexGradient(gi1 % 12, x1, y1)
                        + simplexGradient(gi2 % 12, x2, y2));
    }

    private static double simplexGradient(int index, double x, double y) {
        double contribution = 0.5D - x * x - y * y;
        if (contribution < 0.0D) {
            return 0.0D;
        }
        contribution *= contribution;
        return contribution * contribution * indexedLerp(index, x, y, 0.0D);
    }

    private static double samplePerlin(
            PerlinNoise noise, double x, double y, double z, double yAmplitude, double yMin) {
        int yHash;
        double yFade;
        if (y == 0.0D) {
            y = noise.d2;
            yHash = noise.h2;
            yFade = noise.t2;
        } else {
            y += noise.b;
            double floor = Math.floor(y);
            y -= floor;
            yHash = ((int) floor) & 255;
            yFade = fade(y);
        }
        x += noise.a;
        z += noise.c;
        double xFloor = Math.floor(x);
        double zFloor = Math.floor(z);
        x -= xFloor;
        z -= zFloor;
        int xHash = ((int) xFloor) & 255;
        int zHash = ((int) zFloor) & 255;
        double xFade = fade(x);
        double zFade = fade(z);

        if (yAmplitude != 0.0D) {
            double yClamp = yMin < y ? yMin : y;
            y -= Math.floor(yClamp / yAmplitude) * yAmplitude;
        }

        int a1 = (noise.permutation[xHash] + yHash) & 255;
        int b1 = (noise.permutation[xHash + 1] + yHash) & 255;
        int a2 = (noise.permutation[a1] + zHash) & 255;
        int b2 = (noise.permutation[b1] + zHash) & 255;
        int a3 = (noise.permutation[a1 + 1] + zHash) & 255;
        int b3 = (noise.permutation[b1 + 1] + zHash) & 255;

        double l1 = indexedLerp(noise.permutation[a2], x, y, z);
        double l2 = indexedLerp(noise.permutation[b2], x - 1.0D, y, z);
        double l3 = indexedLerp(noise.permutation[a3], x, y - 1.0D, z);
        double l4 = indexedLerp(noise.permutation[b3], x - 1.0D, y - 1.0D, z);
        double l5 = indexedLerp(noise.permutation[a2 + 1], x, y, z - 1.0D);
        double l6 = indexedLerp(noise.permutation[b2 + 1], x - 1.0D, y, z - 1.0D);
        double l7 = indexedLerp(noise.permutation[a3 + 1], x, y - 1.0D, z - 1.0D);
        double l8 = indexedLerp(noise.permutation[b3 + 1], x - 1.0D, y - 1.0D, z - 1.0D);
        l1 = lerp(xFade, l1, l2);
        l3 = lerp(xFade, l3, l4);
        l5 = lerp(xFade, l5, l6);
        l7 = lerp(xFade, l7, l8);
        l1 = lerp(yFade, l1, l3);
        l5 = lerp(yFade, l5, l7);
        return lerp(zFade, l1, l5);
    }

    private static double indexedLerp(int index, double a, double b, double c) {
        return switch (index & 15) {
            case 0 -> a + b;
            case 1 -> -a + b;
            case 2 -> a - b;
            case 3 -> -a - b;
            case 4 -> a + c;
            case 5 -> -a + c;
            case 6 -> a - c;
            case 7 -> -a - c;
            case 8 -> b + c;
            case 9 -> -b + c;
            case 10 -> b - c;
            case 11 -> -b - c;
            case 12 -> a + b;
            case 13 -> -b + c;
            case 14 -> -a + b;
            case 15 -> -b - c;
            default -> throw new AssertionError();
        };
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double part, double from, double to) {
        return from + part * (to - from);
    }

    private static double lerp3(
            double dx,
            double dy,
            double dz,
            double v000,
            double v100,
            double v010,
            double v110,
            double v001,
            double v101,
            double v011,
            double v111) {
        double first = lerp(dy, lerp(dx, v000, v100), lerp(dx, v010, v110));
        double second = lerp(dy, lerp(dx, v001, v101), lerp(dx, v011, v111));
        return lerp(dz, first, second);
    }

    private static double clampedLerp(double part, double from, double to) {
        if (part <= 0.0D) {
            return from;
        }
        if (part >= 1.0D) {
            return to;
        }
        return lerp(part, from, to);
    }

    private static double upperDrop(int y) {
        return y <= 15 ? (y == 15 ? 63.0D / 64.0D : 1.0D) : (78.0D - y) / 64.0D;
    }

    private static double lowerDrop(int y) {
        if (y <= 1) {
            return 0.0D;
        }
        return y < 8 ? (y - 1.0D) / 7.0D : 1.0D;
    }

    private static long chunkGenerateRandom(long worldSeed, int chunkX, int chunkZ) {
        Random48 random = new Random48(worldSeed);
        long mixed = random.nextLong() * chunkX ^ random.nextLong() * chunkZ ^ worldSeed;
        return mixed;
    }

    private static final class SurfaceNoise {
        private final double xzScale = 2.0D;
        private final double yScale = 1.0D;
        private final double xzFactor = 80.0D;
        private final double yFactor = 160.0D;
        private final PerlinNoise[] min = new PerlinNoise[16];
        private final PerlinNoise[] max = new PerlinNoise[16];
        private final PerlinNoise[] main = new PerlinNoise[8];

        private SurfaceNoise(long seed) {
            Random48 random = new Random48(seed);
            initOctaves(random, min, -15);
            initOctaves(random, max, -15);
            initOctaves(random, main, -7);
        }

        private static void initOctaves(Random48 random, PerlinNoise[] target, int minimumOctave) {
            int length = target.length;
            int end = minimumOctave + length - 1;
            double persistence = 1.0D / ((1L << length) - 1.0D);
            double lacunarity = Math.pow(2.0D, end);
            int index = 0;
            if (end == 0) {
                target[0] = new PerlinNoise(random);
                target[0].amplitude = persistence;
                target[0].lacunarity = lacunarity;
                persistence *= 2.0D;
                lacunarity *= 0.5D;
                index = 1;
            } else {
                random.skip((long) -end * 262L);
            }
            for (; index < length; index++) {
                target[index] = new PerlinNoise(random);
                target[index].amplitude = persistence;
                target[index].lacunarity = lacunarity;
                persistence *= 2.0D;
                lacunarity *= 0.5D;
            }
        }
    }

    private static final class PerlinNoise {
        private final int[] permutation = new int[257];
        private final double a;
        private final double b;
        private final double c;
        private final int h2;
        private final double d2;
        private final double t2;
        private double amplitude = 1.0D;
        private double lacunarity = 1.0D;

        private PerlinNoise(Random48 random) {
            a = random.nextDouble() * 256.0D;
            b = random.nextDouble() * 256.0D;
            c = random.nextDouble() * 256.0D;
            for (int i = 0; i < 256; i++) {
                permutation[i] = i;
            }
            for (int i = 0; i < 256; i++) {
                int j = random.nextInt(256 - i) + i;
                int value = permutation[i];
                permutation[i] = permutation[j];
                permutation[j] = value;
            }
            permutation[256] = permutation[0];
            double floor = Math.floor(b);
            h2 = ((int) floor) & 255;
            d2 = b - floor;
            t2 = fade(d2);
        }
    }

    private static final class Random48 {
        private long state;

        private Random48(long seed) {
            state = (seed ^ MULTIPLIER) & MASK_48;
        }

        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK_48;
            return (int) (state >>> (48 - bits));
        }

        private int nextInt(int bound) {
            int mask = bound - 1;
            if ((mask & bound) == 0) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + mask < 0);
            return value;
        }

        private long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        private double nextDouble() {
            long value = ((long) next(26) << 27) + next(27);
            return value / (double) (1L << 53);
        }

        private void skip(long count) {
            long multiplier = 1L;
            long addend = 0L;
            long currentMultiplier = MULTIPLIER;
            long currentAddend = ADDEND;
            for (long value = count; value != 0L; value >>>= 1) {
                if ((value & 1L) != 0L) {
                    multiplier *= currentMultiplier;
                    addend = currentMultiplier * addend + currentAddend;
                }
                currentAddend = (currentMultiplier + 1L) * currentAddend;
                currentMultiplier *= currentMultiplier;
            }
            state = (state * multiplier + addend) & MASK_48;
        }
    }

    private static final class EndBiome {
        private static final int HIGHLANDS = 0;
        private static final int MIDLANDS = 1;
        private static final int BARRENS = 2;
        private static final int SMALL_ISLANDS = 3;
    }
}
