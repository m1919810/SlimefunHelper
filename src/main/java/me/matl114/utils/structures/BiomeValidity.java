package me.matl114.utils.structures;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.world.biome.Biome;

/** Compares biome membership by registry key across independently loaded default registries. */
final class BiomeValidity {
    private BiomeValidity() {}

    static boolean contains(RegistryEntryList<Biome> allowedBiomes, RegistryEntry<Biome> biome) {
        RegistryKey<Biome> biomeKey = biome.getKey().orElse(null);
        return biomeKey != null
                && allowedBiomes.stream()
                        .anyMatch(entry -> entry.getKey().map(biomeKey::equals).orElse(false));
    }
}
