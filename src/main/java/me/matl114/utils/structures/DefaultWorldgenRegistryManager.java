package me.matl114.utils.structures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.LifecycledResourceManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

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
            DynamicRegistryManager.Immutable preceding =
                    combined.getPrecedingRegistryManagers(ServerDynamicRegistryType.WORLDGEN);
            DynamicRegistryManager.Immutable worldgen =
                    RegistryLoader.loadFromResource(resources, preceding, RegistryLoader.DYNAMIC_REGISTRIES);
            DynamicRegistryManager.Immutable registries =
                    combined.with(ServerDynamicRegistryType.WORLDGEN, worldgen).getCombinedRegistryManager();
            registries.streamAllRegistries().forEach(entry -> loadTags(resources, entry));
            return registries;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load default vanilla worldgen registries", exception);
        }
    }

    private static <T> void loadTags(ResourceManager resources, DynamicRegistryManager.Entry<T> entry) {
        Registry<T> registry = entry.value();
        TagGroupLoader<RegistryEntry<T>> loader =
                new TagGroupLoader<>(registry::getEntry, RegistryKeys.getTagPath(entry.key()));
        Map<TagKey<T>, List<RegistryEntry<T>>> tags = new HashMap<>();
        for (Map.Entry<Identifier, java.util.Collection<RegistryEntry<T>>> tag :
                loader.load(resources).entrySet()) {
            tags.put(TagKey.of(entry.key(), tag.getKey()), List.copyOf(tag.getValue()));
        }
        registry.populateTags(tags);
    }
}
