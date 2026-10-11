package me.matl114.utils.structures;

import net.minecraft.util.math.BlockPos;

/** Cubiomes 1.21.x Ancient City biome viability sample position. */
public final class CubiomesAncientCityBiome {
    private CubiomesAncientCityBiome() {}

    public static BlockPos samplePosition(long worldSeed, StructurePosition structure) {
        return AncientCityVariant.generate(worldSeed, structure).biomeSample();
    }
}
