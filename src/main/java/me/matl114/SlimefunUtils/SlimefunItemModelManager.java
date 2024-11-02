package me.matl114.SlimefunUtils;

import com.google.gson.*;
import me.matl114.Access.BakedModelManagerAccess;
import me.matl114.SlimefunHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.mixin.client.model.loading.ModelLoaderBakerImplMixin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.core.config.yaml.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

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
    //public static UnbakedModel readModelFromJson()
    public static BakedModel loadModel(Identifier id){
       //ModelLoader loader;
        //loader.
        return MinecraftClient.getInstance().getBakedModelManager().getModel(id);
    }
    public static BakedModel getModel(Identifier id){
        return null;
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
            final File configFile = FabricLoader.getInstance().getConfigDir().resolve("slimefun-item-model.yml").toFile();
            if(!configFile.exists()){
                try{
                    if(!configFile.toPath().getParent().toFile().exists()) {
                        Files.createDirectories(configFile.toPath().getParent());
                    }
                    Files.copy(SlimefunHelper.getInstance().getClass().getResourceAsStream("/slimefun-item-model.yml"), configFile.toPath());
                }catch (Throwable e){
                    Debug.info("AN INTERNAL ERROR WHILE LOADING DEFAULT CONFIG");
                    Debug.info(e);
                    return;
                }
            }
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
    public static int getCustomModelData(String id){
        return SLIMEFUNITEMS_CUSTOMMODELDATAS.getOrDefault(id,0);
    }
    public static Identifier getSfItemPath(String id){
        return LOADED_SLIMEFUNITEMS.get(id);
    }
}
