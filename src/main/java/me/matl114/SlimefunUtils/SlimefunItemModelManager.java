package me.matl114.SlimefunUtils;

import com.google.gson.*;
import me.matl114.Access.BakedModelManagerAccess;
import me.matl114.Access.ItemRendererAccess;
import me.matl114.Access.ModelOverrideListAccess;
import me.matl114.ModConfig;
import me.matl114.SlimefunHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.*;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.registry.Registry;
import java.util.*;

public class SlimefunItemModelManager {
    private static HashMap<String,Identifier> LOADED_SLIMEFUNITEMS = new HashMap<>();
    private static HashMap<String,Integer> SLIMEFUNITEMS_CUSTOMMODELDATAS=new HashMap<>();
    private static HashMap<String,BakedModel> SLIMEFUNITEMS_MODELS = new HashMap<>();
    public static Gson gson =new Gson();
    public static JsonObject readJsonObject(Resource resource) {
        try {
            final InputStream inputStream = resource.getInputStream();
            return gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
        } catch(IOException e) {
            Debug.info(e);
            return new JsonObject();
        }
    }
    public static void loadResourcePacks(){
        // 注册自定义资源包
        //JsonUnbakedModel.deserialize()
    }
    public static void init(){
        SLIMEFUNITEMS_MODELS.clear();
        SLIMEFUNITEMS_CUSTOMMODELDATAS.clear();
        LOADED_SLIMEFUNITEMS.clear();
    }
    public static void loadCustomModelDatas(){
        try{
            final File configFile= ModConfig.loadOrUseInternal("slimefun-item-model.yml");
            Yaml yaml=new Yaml();
            try (FileReader inputStream = new FileReader(configFile)) {
                // 将 YAML 文件内容加载到 Map 中
                Map<String, Object> data = yaml.load(inputStream);
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    try{
                        int cmd=(Integer) entry.getValue();
                        SLIMEFUNITEMS_CUSTOMMODELDATAS.put(entry.getKey(),cmd);
                    }catch(ClassCastException e){
                        Debug.info("Custom Model data could not be loaded :",entry.getKey());
                    }
                }
                // 获取具体数据
            } catch (Exception e) {
                Debug.info("AN INTERNAL ERROR WHILE READING CONFIG ITEM-MODEL");
                Debug.info(e);
            }
            Debug.info("Slimefun Custom Model Data load successfully");

        }catch (Throwable e){
            Debug.info("error while loading CustomModelDatas");
            Debug.info(e);
        }
    }
    public static Collection<Identifier> walkThroughResourcePacks(ResourceManager resourceManager){
        Debug.info("on walkThroughResourcePacks");
        Collection<Identifier> id= new LinkedHashSet<>();
        List<ResourcePack> packs= resourceManager.streamResourcePacks().toList();
        for(ResourcePack pack : packs){
            //Debug.info("in resourcepack ",pack.getName());
            String name=pack.getName();
            if(name.equals("minecraft")||name.equals("realms")||name.startsWith("fabric-")||name.equals("fabric")){
                continue;
            }
            Set<String> namespacess= pack.getNamespaces(ResourceType.CLIENT_RESOURCES);
            BakedModelManagerAccess access=BakedModelManagerAccess.of(MinecraftClient.getInstance().getBakedModelManager());
            for(String namespace : namespacess){

                Debug.info("Force loading namespace ",namespace,"in pack ",pack.getName());
                //Debug.info("in namespace ",namespace);
                pack.findResources(ResourceType.CLIENT_RESOURCES,namespace,"models",(i,j)->{
                        String realNamespace=i.getNamespace();
                        String realPath=i.getPath().replaceFirst("^models/","").replaceAll(".json$","");
                        String[] splits=realPath.split("/");
                        String trueId=splits[splits.length-1];
                        Identifier shouldId=new Identifier(realNamespace,trueId);
                        Identifier shouldModelId="item".equals(splits[0])?new ModelIdentifier(realNamespace,realPath.replaceFirst("^item/",""),"inventory"):new Identifier(realNamespace,realPath);
                        if(Registries.ITEM.get(shouldId)== Items.AIR){
                            id.add(shouldModelId);
                        }

                }
                );
            }
        }
        //Debug.info(id);
//        for (String namespace : namespaces){
//            if(namespace.equals("minecraft")||namespace.equals("realms")||namespace.startsWith("fabric-")||namespace.equals("fabricloader")){
//                continue;
//            }
//            Debug.info("namespace",namespace);
//            Map<Identifier,List<Resource>> resource=resourceManager.findAllResources(namespace,i->true);
//
//            Debug.info("find resource",resource);
//            for(Map.Entry<Identifier,List<Resource>> entry : resource.entrySet()){
//                Debug.info(entry.getKey().toString(),entry.getValue().toString());
//
//            }
//        }
        return id;
    }
    public static int getCustomModelData(String id){
        return SLIMEFUNITEMS_CUSTOMMODELDATAS.getOrDefault(id,0);
    }
    public static Identifier getSfItemPath(String id){
        return LOADED_SLIMEFUNITEMS.get(id);
    }
}
