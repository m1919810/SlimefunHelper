package me.matl114.utils.structures;

import java.util.List;
import net.minecraft.resource.LifecycledResourceManager;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.VanillaDataPackProvider;

/** Creates a resource manager containing only the built-in vanilla server-data pack. */
final class DefaultServerDataResources {
    private DefaultServerDataResources() {}

    static LifecycledResourceManager create() {
        ResourcePack vanillaPack = VanillaDataPackProvider.createDefaultPack();
        return new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, List.of(vanillaPack));
    }
}
