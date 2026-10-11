package me.matl114.utils.structures;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.structure.Structure;

/**
 * Resolves the per-generation-step structure index used by vanilla's
 * {@code ChunkGenerator.applyBiomeDecoration} random stream.
 */
public final class StructureDecorationIndex {
    private StructureDecorationIndex() {}

    public static Map<Identifier, Index> collectDefault() {
        return collect(DefaultWorldgenRegistryManager.registry());
    }

    public static Map<Identifier, Index> collect(RegistryWrapper.WrapperLookup registries) {
        RegistryWrapper<Structure> structures = registries.getWrapperOrThrow(RegistryKeys.STRUCTURE);
        Map<GenerationStep.Feature, Integer> nextIndex = new EnumMap<>(GenerationStep.Feature.class);
        Map<Identifier, Index> result = new LinkedHashMap<>();
        structures.streamEntries().forEachOrdered(entry -> {
            Structure structure = entry.value();
            GenerationStep.Feature step = structure.getFeatureGenerationStep();
            int index = nextIndex.getOrDefault(step, 0);
            Identifier id = entry.registryKey().getValue();
            result.put(id, new Index(step.ordinal(), index));
            nextIndex.put(step, index + 1);
        });
        return Map.copyOf(result);
    }

    public record Index(int generationStep, int structureIndex) {}
}
