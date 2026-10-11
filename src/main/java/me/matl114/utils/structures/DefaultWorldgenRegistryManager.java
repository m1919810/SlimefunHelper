package me.matl114.utils.structures;

import java.util.List;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.resource.LifecycledResourceManager;

/** Loads vanilla's default worldgen registries without using the connected world's registry packet. */
public final class DefaultWorldgenRegistryManager {
    private static final DynamicRegistryManager.Immutable REGISTRIES = create();

    private DefaultWorldgenRegistryManager() {}

    public static DynamicRegistryManager.Immutable registry() {
        return REGISTRIES;
    }

    private static DynamicRegistryManager.Immutable create() {
        try (LifecycledResourceManager resources = DefaultServerDataResources.create()) {
            CombinedDynamicRegistries<ServerDynamicRegistryType> combined =
                    ServerDynamicRegistryType.createCombinedDynamicRegistries();
            List<net.minecraft.registry.Registry.PendingTagLoad<?>> pendingTags =
                    TagGroupLoader.startReload(
                            resources, combined.get(ServerDynamicRegistryType.STATIC));
            DynamicRegistryManager.Immutable preceding =
                    combined.getPrecedingRegistryManagers(ServerDynamicRegistryType.WORLDGEN);
            List<RegistryWrapper.Impl<?>> registryLookups =
                    TagGroupLoader.collectRegistries(preceding, pendingTags);
            DynamicRegistryManager.Immutable worldgen = RegistryLoader.loadFromResource(
                    resources, registryLookups, RegistryLoader.DYNAMIC_REGISTRIES);
            pendingTags.forEach(net.minecraft.registry.Registry.PendingTagLoad::apply);
            return combined.with(ServerDynamicRegistryType.WORLDGEN, worldgen)
                    .getCombinedRegistryManager();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load default vanilla worldgen registries", exception);
        }
    }
}
