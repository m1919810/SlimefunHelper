package me.matl114.utils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class ResourceUtils {
    public static Set<Identifier> lookupResources(
            ResourceManager resourceManager,
            String packId,
            String namespace,
            String prefix,
            String fileType,
            Predicate<String> pathPredicate) {
        Set<Identifier> identifiers = new LinkedHashSet<>();
        for (var pack : resourceManager.streamResourcePacks().toList()) {
            if (pack.getId().equals(packId)) {
                pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, prefix, (i, j) -> {
                    String realNamespace = i.getNamespace();
                    if (i.getPath().endsWith(fileType)) {
                        String realPath =
                                i.getPath().replaceFirst("^" + prefix + "/", "").replaceAll(fileType + "$", "");
                        if (pathPredicate.test(realPath)) {
                            identifiers.add(new Identifier(realNamespace, realPath));
                        }
                    }
                });
            }
        }
        return identifiers;
    }

    public static Set<Identifier> lookupOurModelResources(ResourceManager m, String prefix) {
        return ResourceUtils.lookupResources(
                m, "slimefunhelper", "slimefunhelper", "models", ".json", s -> s.startsWith(prefix));
    }

    public static Set<Identifier> lookupOurTextureResources(ResourceManager m, String prefix) {
        return ResourceUtils.lookupResources(
                m, "slimefunhelper", "slimefunhelper", "textures", ".png", s -> s.startsWith(prefix));
    }

    public static Identifier ofAtlasTexture(String type) {
        return Identifier.ofVanilla("textures/atlas/" + type + ".png");
    }
}
