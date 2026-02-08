package me.matl114.hacks.modules.models;

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

import java.util.*;

public class CustomTextures extends BaseModule {
    public static final String[] CUSTOM_TEXTURE_PATTERN = {"texture-config", "namespace-for-custom-textures"};
    public CustomTextures() {

    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getAtlasSourceSupply(), this::onAtlasSupply);
    }

    public final ListRef customTexturePath = builder(Configs.MODEL_CONFIG, CUSTOM_TEXTURE_PATTERN, ListRef.TYPE)
        .defaultValue(List.of("ae2", "slimefunhelper", "infinityexpansion", "avaritia"))

        .build();

    public void onAtlasSupply(Event<Set<Identifier>> event){
        if(targetIdentifier.equals(event.getArgs(1))){
            Debug.info("Loading blocks atlases");
            Debug.info("Appending our textures automatically");
            event.context().addAll(loadOurselvesCustomModelTexture(event.getArgs(0)));
        }
    }

    private static final String OUR_NAMESPACE = "slimefunhelper";
    public Collection<Identifier> loadOurselvesCustomModelTexture(ResourceManager manager){
        List<Identifier> textureIds = new ArrayList<>();
        Set<String> namespaces = new HashSet<>(customTexturePath.get());
        for(ResourcePack pack : manager.streamResourcePacks().toList()){
            //Debug.info("in resourcepack ",pack.getName());
            Set<String> namespacess= pack.getNamespaces(ResourceType.CLIENT_RESOURCES);
            for(String namespace : namespacess){
                if(OUR_NAMESPACE.equals(namespace) || namespaces.contains(namespace)){
                    Debug.info("Force load TEXTURE in pack",pack.getId(),"and namespace",namespace);
                    pack.findResources(ResourceType.CLIENT_RESOURCES,namespace,"textures",(i,j)->{
                            String realNamespace=i.getNamespace();
                            if(i.getPath().endsWith(".png")){
                                String realPath=i.getPath().replaceFirst("^textures/","").replaceAll(".png$","");

                                Identifier shouldId=new Identifier(realNamespace,realPath);
                                textureIds.add(shouldId);

                            }
                        }
                    );
                }
            }


        }
        return textureIds;
    }
    private static Identifier targetIdentifier = new Identifier("minecraft","blocks");
}
