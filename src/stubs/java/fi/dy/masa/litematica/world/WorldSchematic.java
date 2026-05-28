package fi.dy.masa.litematica.world;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

public abstract class WorldSchematic extends World {
    protected WorldSchematic(
            MutableWorldProperties properties,
            RegistryKey<World> registryRef,
            DynamicRegistryManager registryManager,
            RegistryEntry<DimensionType> dimensionEntry,
            boolean isClient,
            boolean debugWorld,
            long seed,
            int maxChainedNeighborUpdates) {
        super(
                properties,
                registryRef,
                registryManager,
                dimensionEntry,
                isClient,
                debugWorld,
                seed,
                maxChainedNeighborUpdates);
    }
}
