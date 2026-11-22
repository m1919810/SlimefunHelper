package me.matl114.utils.worldgen.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

/** Exposes vanilla's protected per-block sample so a sampler can cover only some cell rows. */
final class TerrainNoiseSampler extends ChunkNoiseSampler {
    TerrainNoiseSampler(
            int horizontalCellCount,
            NoiseConfig noiseConfig,
            int startBlockX,
            int startBlockZ,
            GenerationShapeConfig shape,
            DensityFunctionTypes.Beardifying beardifying,
            ChunkGeneratorSettings settings,
            AquiferSampler.FluidLevelSampler fluidLevelSampler) {
        super(
                horizontalCellCount,
                noiseConfig,
                startBlockX,
                startBlockZ,
                shape,
                beardifying,
                settings,
                fluidLevelSampler,
                Blender.getNoBlending());
    }

    @Nullable
    BlockState sample() {
        return sampleBlockState();
    }
}
