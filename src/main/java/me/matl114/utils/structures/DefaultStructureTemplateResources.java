package me.matl114.utils.structures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.datafixer.Schemas;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.LifecycledResourceManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorage;

/** Vanilla server-data resources and a private temporary cache for offline structure templates. */
final class DefaultStructureTemplateResources {
    private static final Resources RESOURCES = create();

    private DefaultStructureTemplateResources() {}

    static StructureTemplateManager templates() {
        return RESOURCES.templates();
    }

    static StructureContext context(DynamicRegistryManager registries) {
        return new StructureContext(RESOURCES.resourceManager(), registries, RESOURCES.templates());
    }

    private static Resources create() {
        LifecycledResourceManager resourceManager = DefaultServerDataResources.create();
        LevelStorage.Session session = null;
        try {
            Path cacheDirectory = Files.createTempDirectory("slimefunhelper-structure-templates-");
            cacheDirectory.toFile().deleteOnExit();
            session = LevelStorage.create(cacheDirectory)
                    .createSessionWithoutSymlinkCheck("template-cache");
            StructureTemplateManager templates = new StructureTemplateManager(
                    resourceManager,
                    session,
                    Schemas.getFixer(),
                    DefaultWorldgenRegistryManager.registry().getWrapperOrThrow(RegistryKeys.BLOCK));
            LevelStorage.Session openSession = session;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    openSession.close();
                } catch (IOException ignored) {
                }
                resourceManager.close();
            }, "slimefunhelper-template-cache-close"));
            return new Resources(resourceManager, templates);
        } catch (Throwable throwable) {
            if (session != null) {
                try {
                    session.close();
                } catch (IOException ignored) {
                }
            }
            resourceManager.close();
            throw new IllegalStateException("Unable to initialize vanilla structure templates", throwable);
        }
    }

    private record Resources(ResourceManager resourceManager, StructureTemplateManager templates) {}
}
