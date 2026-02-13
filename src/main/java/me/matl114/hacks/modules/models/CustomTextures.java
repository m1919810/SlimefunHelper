package me.matl114.hacks.modules.models;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ListRef;
import me.matl114.utils.Debug;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class CustomTextures extends BaseModule {
    public static final String[] CUSTOM_TEXTURE_PATTERN = {"texture-config", "namespace-for-custom-textures"};

    public CustomTextures() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getAtlasSourceSupply(), this::onAtlasSupply);
    }

    public final ListRef customTexturePath = builder(Configs.MODEL_CONFIG, CUSTOM_TEXTURE_PATTERN, ListRef.TYPE)
            .defaultValue(List.of("ae2", "infinityexpansion", "avaritia"))
            .build();

    public void onAtlasSupply(Event<Set<Identifier>> event) {
        if (targetIdentifier.equals(event.getArgs(1))) {
            Debug.info("Loading blocks atlases");
            Debug.info("Appending our textures automatically");
            event.context().addAll(loadOurselvesCustomModelTexture(event.getArgs(0)));
        }
    }

    private static final String OUR_NAMESPACE = "slimefunhelper";

    public Collection<Identifier> loadOurselvesCustomModelTexture(ResourceManager manager) {
        List<Identifier> textureIds = new ArrayList<>();
        Set<String> namespaces = new HashSet<>(customTexturePath.get());
        List<Predicate<String>> predicates = namespaces.stream()
                .map(s -> {
                    if (s.contains(":")) {
                        try {
                            return Pattern.compile(s).asMatchPredicate();
                        } catch (Throwable e) {
                            Debug.info("Illegal format of texture path : ", s);
                            return null;
                        }
                    } else {
                        String sn = s + ":";
                        return (Predicate<String>) v -> v.startsWith(sn);
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        Debug.info("Custom Atlas load start");
        for (ResourcePack pack : manager.streamResourcePacks().toList()) {
            // Debug.info("in resourcepack ",pack.getName());
            String name = pack.getId();
            if (name.equals("minecraft")
                    || name.equals("realms")
                    || name.startsWith("fabric-")
                    || name.equals("fabric")
                    || name.equals("vanilla")) {
                continue;
            }
            if (name.equals(OUR_NAMESPACE)) {
                pack.findResources(ResourceType.CLIENT_RESOURCES, "slimefunhelper", "textures/slimefunitem", (i, j) -> {
                    String realNamespace = i.getNamespace();
                    if (i.getPath().endsWith(".png")) {
                        String realPath =
                                i.getPath().replaceFirst("^textures/", "").replaceAll(".png$", "");

                        Identifier shouldId = new Identifier(realNamespace, realPath);
                        textureIds.add(shouldId);
                    }
                });
            } else {
                Set<String> namespacess = pack.getNamespaces(ResourceType.CLIENT_RESOURCES);

                for (String namespace : namespacess) {
                    pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "textures", (i, j) -> {
                        String realNamespace = i.getNamespace();
                        if (i.getPath().endsWith(".png")) {
                            String realPath =
                                    i.getPath().replaceFirst("^textures/", "").replaceAll(".png$", "");

                            Identifier shouldId = new Identifier(realNamespace, realPath);
                            String string = shouldId.toString();
                            if (predicates.stream().anyMatch(p -> p.test(string))) {
                                textureIds.add(shouldId);
                            }
                        }
                    });
                }
            }
        }
        return textureIds;
    }

    private static Identifier targetIdentifier = new Identifier("minecraft", "blocks");
}
