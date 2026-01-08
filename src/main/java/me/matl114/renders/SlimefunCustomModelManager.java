package me.matl114.renders;

import com.google.gson.*;
import me.matl114.access.BakedModelManagerAccess;
import me.matl114.ModConfig;
import me.matl114.managers.Configs;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SlimefunCustomModelManager {
    private static final HashMap<String,Integer> SLIMEFUNITEMS_CUSTOMMODELDATAS=new HashMap<>();
    private static final HashMap<String,ModelIdentifier> CUSTOM_PATH_SLIMEFUN_MODEL = new HashMap<>();
    private static final String OUR_NAMESPACE = "slimefunhelper";
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
    public static void registerResourceReloadTasks(Runnable runnable){
        reloadTasks.add(runnable);
    }
    private static final List<Runnable> reloadTasks = new ArrayList<>();
    public static void init(){
        SLIMEFUNITEMS_CUSTOMMODELDATAS.clear();
        reloadTasks.forEach(Runnable::run);
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

    public static Collection<Identifier> walkThroughResourcePacks(ResourceManager resourceManager, boolean allLoad){
        Debug.info("on walkThroughResourcePacks");
        Collection<Identifier> id= new LinkedHashSet<>();
        List<ResourcePack> packs= resourceManager.streamResourcePacks().toList();
        List<String> modelPathPattern = Configs.MODEL_CONFIG.getList(Configs.AUTO_MODEL_PATTERN).get();
        String pattern = modelPathPattern.stream().map(i->"("+i+")").collect(Collectors.joining("|"));
        var predicate = Pattern.compile(pattern).asMatchPredicate();
        for(ResourcePack pack : packs){
            //Debug.info("in resourcepack ",pack.getName());
            Debug.info("check pack", pack);
            String name=pack.getId();
            if(name.equals("minecraft")||name.equals("realms")||name.startsWith("fabric-")||name.equals("fabric")){
                continue;
            }
            Debug.info("walk at", name);
            Set<String> namespacess= pack.getNamespaces(ResourceType.CLIENT_RESOURCES);
            BakedModelManagerAccess access=BakedModelManagerAccess.of(MinecraftClient.getInstance().getBakedModelManager());
            for(String namespace : namespacess){
                if(allLoad || OUR_NAMESPACE.equals(namespace)){
                    Debug.info("Force loading namespace ",namespace,"in pack ",pack.getId());
                    //Debug.info("in namespace ",namespace);
                    pack.findResources(ResourceType.CLIENT_RESOURCES,namespace,"models",(i,j)->{
                            ///Debug.info("finding resource ",i,j);
                            String realNamespace=i.getNamespace();
                            String realPath=i.getPath().replaceFirst("^models/","").replaceAll(".json$","");
                            String[] splits=realPath.split("/");
                            String trueId=splits[splits.length-1];
                            Identifier shouldId=new Identifier(realNamespace,trueId);
                            //Debug.info(shouldId);
                            Identifier fullPathId = new Identifier(realNamespace,realPath);
                            Identifier shouldModelId="item".equals(splits[0])?new Identifier(realNamespace,String.join("/",Arrays.copyOfRange(splits, 1, splits.length)))  :fullPathId;
                            ModelIdentifier wrappedId = RenderMain.wrapAsModel(fullPathId);

//                            if(OUR_NAMESPACE.equals(namespace)){
//                                Debug.info("try test slimefun item model",shouldModelId);
//                            }
                            if(predicate.test(shouldModelId.toString())){
                                //custom item
                                Debug.info("load custom slimefun item model:",shouldModelId);
                                CUSTOM_PATH_SLIMEFUN_MODEL.put(splits[splits.length-1].toUpperCase(Locale.ROOT), wrappedId);
                            }

                            if(Registries.ITEM.get(shouldId)== Items.AIR){

                               // Debug.info("input into registry");
                               // Debug.info("add into ", fullPathId);
                                id.add(fullPathId);
                            }
                        }
                    );
//                    pack.findResources(ResourceType.CLIENT_RESOURCES,namespace,"textures",(i,j)->{
//                            Debug.info("finding more textures ",i,j);
//                            String realNamespace=i.getNamespace();
//                            String realPath=i.getPath().replaceFirst("^textures/","").replaceAll(".json$","");
//                            String[] splits=realPath.split("/");
//                            String trueId=splits[splits.length-1];
//                            Identifier shouldId=new Identifier(realNamespace,trueId);
//                            Debug.info(shouldId);
//                            Identifier shouldModelId="item".equals(splits[0])?new ModelIdentifier(realNamespace,realPath.replaceFirst("^item/",""),"inventory"):new Identifier(realNamespace,realPath);
//                            if(Registries.ITEM.get(shouldId)== Items.AIR){
//                                Debug.info("input into registry");
//                                //id.add(shouldModelId);
//                            }
//
//                        }
//                    );

                }

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
    public static Collection<Identifier> loadOurselvesCustomModelTexture(ResourceManager manager){
        List<Identifier> textureIds = new ArrayList<>();
        Set<String> namespaces = new HashSet<>(Configs.MODEL_CONFIG.getList(Configs.CUSTOM_TEXTURE_PATTERN).get());
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
    public static int getOverridingModelData(ItemStack item){
        return -1;
    }

    public static int getCustomModelData(String id){
        return SLIMEFUNITEMS_CUSTOMMODELDATAS.getOrDefault(id,0);
    }

    static {
        RenderMain.registerModelOverridePredicate((stack)->{
            NbtCompound nbt= ItemStackUtils.getCustomDataReadOnly(stack);
            try{
                String model=null;
                if(nbt.contains("item_model")){
                    model=nbt.getString("item_model");
                }else if(nbt.contains("minecraft:item_model")){
                    model=nbt.getString("minecraft:item_model");
                }
                if(model!=null){
                    String[] namespaceCheck=model.split(":");
                    String namespace="minecraft";
                    String itemModel=namespaceCheck[namespaceCheck.length-1];
                    if(namespaceCheck.length>=2){
                        namespace=namespaceCheck[0];
                    }
                    return Optional.of( ModelIdentifier.ofInventoryVariant(new Identifier(namespace,itemModel)));

                }
            }catch(Throwable e){}
            try{
                String id = ItemStackUtils.getSfId(nbt);
                if(id!=null ){
                    return Optional.ofNullable(CUSTOM_PATH_SLIMEFUN_MODEL.get(id));
                }
            }catch (Throwable e){
            }

            return Optional.empty();
        });
    }
}
