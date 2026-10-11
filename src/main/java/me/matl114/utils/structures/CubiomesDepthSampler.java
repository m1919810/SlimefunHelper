package me.matl114.utils.structures;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.noise.NoiseConfig;

/** Ports cubiomes' {@code sampleClimatePara(NP_DEPTH)} approximation. */
final class CubiomesDepthSampler {
    private static final int CONTINENTALNESS = 0;
    private static final int EROSION = 1;
    private static final int RIDGES = 2;
    private static final int WEIRDNESS = 3;
    private static final Node SPLINE = createSpline();

    private final DoublePerlinNoiseSampler continentalness;
    private final DoublePerlinNoiseSampler erosion;
    private final DoublePerlinNoiseSampler weirdness;

    CubiomesDepthSampler(NoiseConfig noiseConfig) {
        continentalness = sampler(noiseConfig, "continentalness");
        erosion = sampler(noiseConfig, "erosion");
        weirdness = sampler(noiseConfig, "ridge");
    }

    double sample(int blockX, int blockZ) {
        return sample(blockX / 4.0, blockZ / 4.0);
    }

    double sample(double quartX, double quartZ) {
        float c = (float) continentalness.sample(quartX, 0.0, quartZ);
        float e = (float) erosion.sample(quartX, 0.0, quartZ);
        float w = (float) weirdness.sample(quartX, 0.0, quartZ);
        float peaksAndValleys = -3.0F * (Math.abs(Math.abs(w) - 0.6666667F) - 0.33333334F);
        float[] parameters = {c, e, peaksAndValleys, w};
        float offset = evaluate(SPLINE, parameters) + 0.015F;
        float depth = 1.0F - 83.0F / 160.0F + offset;
        return depth;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DoublePerlinNoiseSampler sampler(NoiseConfig noiseConfig, String id) {
        RegistryKey noiseKey = RegistryKey.of(RegistryKeys.NOISE_PARAMETERS, Identifier.ofVanilla(id));
        return noiseConfig.getOrCreateSampler(noiseKey);
    }

    private static Node createSpline() {
        Node root = node(CONTINENTALNESS);
        Node land0 = createLandSpline(-0.15F, 0.00F, 0.0F, 0.1F, 0.00F, -0.03F, false);
        Node land1 = createLandSpline(-0.10F, 0.03F, 0.1F, 0.1F, 0.01F, -0.03F, false);
        Node land2 = createLandSpline(-0.10F, 0.03F, 0.1F, 0.7F, 0.01F, -0.03F, true);
        Node land3 = createLandSpline(-0.05F, 0.03F, 0.1F, 1.0F, 0.01F, 0.01F, true);
        add(root, -1.10F, fixed(0.044F), 0.0F);
        add(root, -1.02F, fixed(-0.2222F), 0.0F);
        add(root, -0.51F, fixed(-0.2222F), 0.0F);
        add(root, -0.44F, fixed(-0.12F), 0.0F);
        add(root, -0.18F, fixed(-0.12F), 0.0F);
        add(root, -0.16F, land0, 0.0F);
        add(root, -0.15F, land0, 0.0F);
        add(root, -0.10F, land1, 0.0F);
        add(root, 0.25F, land2, 0.0F);
        add(root, 1.00F, land3, 0.0F);
        return root;
    }

    private static Node createLandSpline(
            float f, float g, float h, float i, float j, float k, boolean peaks) {
        Node sp1 = createSpline38219(lerp(i, 0.6F, 1.5F), peaks);
        Node sp2 = createSpline38219(lerp(i, 0.6F, 1.0F), peaks);
        Node sp3 = createSpline38219(i, peaks);
        float half = 0.5F * i;
        Node sp4 = createFlatOffsetSpline(f - 0.15F, half, half, half, i * 0.6F, 0.5F);
        Node sp5 = createFlatOffsetSpline(f, j * i, g * i, half, i * 0.6F, 0.5F);
        Node sp6 = createFlatOffsetSpline(f, j, j, g, h, 0.5F);
        Node sp7 = createFlatOffsetSpline(f, j, j, g, h, 0.5F);
        Node sp8 = node(RIDGES);
        add(sp8, -1.0F, fixed(f), 0.0F);
        add(sp8, -0.4F, sp6, 0.0F);
        add(sp8, 0.0F, fixed(h + 0.07F), 0.0F);
        Node sp9 = createFlatOffsetSpline(-0.02F, k, k, g, h, 0.0F);
        Node sp = node(EROSION);
        add(sp, -0.85F, sp1, 0.0F);
        add(sp, -0.7F, sp2, 0.0F);
        add(sp, -0.4F, sp3, 0.0F);
        add(sp, -0.35F, sp4, 0.0F);
        add(sp, -0.1F, sp5, 0.0F);
        add(sp, 0.2F, sp6, 0.0F);
        if (peaks) {
            add(sp, 0.4F, sp7, 0.0F);
            add(sp, 0.45F, sp8, 0.0F);
            add(sp, 0.55F, sp8, 0.0F);
            add(sp, 0.58F, sp7, 0.0F);
        }
        add(sp, 0.7F, sp9, 0.0F);
        return sp;
    }

    private static Node createSpline38219(float continentalness, boolean peaks) {
        Node sp = node(RIDGES);
        float i = getOffsetValue(-1.0F, continentalness);
        float k = getOffsetValue(1.0F, continentalness);
        float l = 1.0F - (1.0F - continentalness) * 0.5F;
        float u = 0.5F * (1.0F - continentalness);
        l = u / (0.46082947F * l) - 1.17F;
        if (l > -0.65F && l < 1.0F) {
            u = getOffsetValue(-0.65F, continentalness);
            float p = getOffsetValue(-0.75F, continentalness);
            float q = (p - i) * 4.0F;
            float r = getOffsetValue(l, continentalness);
            float s = (k - r) / (1.0F - l);
            add(sp, -1.0F, fixed(i), q);
            add(sp, -0.75F, fixed(p), 0.0F);
            add(sp, -0.65F, fixed(u), 0.0F);
            add(sp, l - 0.01F, fixed(r), 0.0F);
            add(sp, l, fixed(r), s);
            add(sp, 1.0F, fixed(k), s);
        } else {
            u = (k - i) * 0.5F;
            if (peaks) {
                add(sp, -1.0F, fixed(Math.max(i, 0.2F)), 0.0F);
                add(sp, 0.0F, fixed(lerp(0.5F, i, k)), u);
            } else {
                add(sp, -1.0F, fixed(i), u);
            }
            add(sp, 1.0F, fixed(k), u);
        }
        return sp;
    }

    private static Node createFlatOffsetSpline(float f, float g, float h, float i, float j, float k) {
        Node sp = node(RIDGES);
        float l = Math.max(0.5F * (g - f), k);
        float m = 5.0F * (h - g);
        add(sp, -1.0F, fixed(f), l);
        add(sp, -0.4F, fixed(g), Math.min(l, m));
        add(sp, 0.0F, fixed(h), m);
        add(sp, 0.4F, fixed(i), 2.0F * (i - h));
        add(sp, 1.0F, fixed(j), 0.7F * (j - i));
        return sp;
    }

    private static float getOffsetValue(float weirdness, float continentalness) {
        float f0 = 1.0F - (1.0F - continentalness) * 0.5F;
        float f1 = 0.5F * (1.0F - continentalness);
        float f2 = (weirdness + 1.17F) * 0.46082947F;
        float offset = f2 * f0 - f1;
        if (weirdness < -0.7F) {
            return Math.max(offset, -0.2222F);
        }
        return Math.max(offset, 0.0F);
    }

    private static float evaluate(Node spline, float[] parameters) {
        if (spline.length == 1) {
            return spline.values[0].fixedValue;
        }
        float f = parameters[spline.type];
        int i = 0;
        while (i < spline.length && spline.locations[i] < f) {
            i++;
        }
        if (i == 0 || i == spline.length) {
            if (i == spline.length) {
                i--;
            }
            float value = evaluate(spline.values[i], parameters);
            return value + spline.derivatives[i] * (f - spline.locations[i]);
        }
        float g = spline.locations[i - 1];
        float h = spline.locations[i];
        float k = (f - g) / (h - g);
        float l = spline.derivatives[i - 1];
        float m = spline.derivatives[i];
        float n = evaluate(spline.values[i - 1], parameters);
        float o = evaluate(spline.values[i], parameters);
        float p = l * (h - g) - (o - n);
        float q = -m * (h - g) + (o - n);
        float r = (float) (lerp(k, n, o) + k * (1.0F - k) * lerp(k, p, q));
        return r;
    }

    private static Node node(int type) {
        return new Node(type);
    }

    private static Node fixed(float value) {
        Node node = new Node(RIDGES);
        node.length = 1;
        node.values[0] = new Node(value);
        return node;
    }

    private static void add(Node spline, float location, Node value, float derivative) {
        int index = spline.length++;
        spline.locations[index] = location;
        spline.values[index] = value;
        spline.derivatives[index] = derivative;
    }

    private static float lerp(float part, float from, float to) {
        return (float) ((double) from + (double) part * ((double) to - (double) from));
    }

    private static final class Node {
        private final int type;
        private final float fixedValue;
        private final float[] locations = new float[12];
        private final Node[] values = new Node[12];
        private final float[] derivatives = new float[12];
        private int length;

        private Node(int type) {
            this.type = type;
            this.fixedValue = 0.0F;
        }

        private Node(float fixedValue) {
            this.type = RIDGES;
            this.fixedValue = fixedValue;
        }
    }
}
