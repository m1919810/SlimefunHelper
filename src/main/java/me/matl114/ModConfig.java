package me.matl114;

import lombok.Getter;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ModConfig {

    public static File loadOrUseInternal(String configName){
        final File configFile = FabricLoader.getInstance().getConfigDir().resolve(configName).toFile();
        if(!configFile.exists()){
            try{
                if(!configFile.getParentFile().exists()) {
                    Files.createDirectories(configFile.toPath().getParent());
                }
                Files.copy(SlimefunHelper.getInstance().getClass().getResourceAsStream("/"+configName), configFile.toPath());
            }catch (Throwable e){
                Debug.info("AN INTERNAL ERROR WHILE LOADING DEFAULT CONFIG");
                Debug.info(e);
                return null;
            }
        }
        //sync with internal
        Yaml yaml = new Yaml();
        HashMap<String,Object> config=new HashMap<>();
        HashMap<String,Object> defaults;
        try(FileReader readerConfig=new FileReader(configFile)){
            config = yaml.load(readerConfig);
            config=(config==null?new HashMap<>():config);
            defaults =yaml.load(SlimefunHelper.getInstance().getClass().getResourceAsStream("/"+configName));
            defaults=(defaults==null?new HashMap<>():defaults);
            syncKeys(config,defaults);
        }catch (Throwable e){
            Debug.info("AN INTERNAL ERROR WHILE LOADING DEFAULT CONFIG");
            e.printStackTrace();
        }
        try(FileWriter writer=new FileWriter(configFile)){
            yaml.dump(config,writer);
        }catch (Throwable e){
            Debug.info("AN INTERNAL ERROR WHILE WRITING CONFIG");
            Debug.info(e);
        }
        return configFile;
    }
    public static void syncKeys(HashMap config,HashMap defaults){
        for(Object key:defaults.keySet()){
            if(config.containsKey(key)){
                Object value = config.get(key);
                Object defaultValue = defaults.get(key);
                if(value instanceof HashMap mp1 && defaultValue instanceof HashMap mp2){
                    syncKeys(mp1,mp2);
                }
            }else {
                config.put(key, defaults.get(key));
            }
        }
    }
    @Getter
    private static boolean enableBlockModelProtect=true;
    @Getter
    private static boolean enableSlimefunCmdOverride=true;
    @Getter
    private static boolean enableItemModelOvevrride=true;
    @Getter
    private static boolean enableStorageItemDisplay=true;
    @Getter
    private static boolean enableToolTipsDisplay=true;
    @Getter
    private static String slimefunIdCopyHotkey="LEFT_CONTROL,C,BUTTON_1";

    private static HashMap<String,String> toggleHotKeys=new HashMap<>();
    public static String getToggleHotkeys(String key){
        return toggleHotKeys.get(key);
    }

    public static void reloadModConfig(){
        Debug.info("Reloading Mod Config");
        File configFile=loadOrUseInternal("slimefunhelper-config.yml");
        Yaml yaml = new Yaml();
        Map<String, Object> data=new HashMap<>();
        try(FileReader reader = new FileReader(configFile)){
            data=yaml.load(reader);
            option_load:{
                Map<String,Object> modConfig=(Map<String, Object>) data.get("options");
                if(modConfig==null)break option_load;
                enableBlockModelProtect=getOrSetDefault(modConfig,"enable-model-protect",true);
                Debug.info("toggle model protect ",Boolean.toString(enableBlockModelProtect));
                enableSlimefunCmdOverride=getOrSetDefault(modConfig,"enable-slimefun-cmd-override",true);
                Debug.info("toggle slimefun cmd override ",Boolean.toString(enableSlimefunCmdOverride));
                enableItemModelOvevrride=getOrSetDefault(modConfig,"enable-item-model-override",true);
                Debug.info("toggle item model override ",Boolean.toString(enableItemModelOvevrride));
                enableStorageItemDisplay=getOrSetDefault(modConfig,"enable-storageitem-display",true);
                Debug.info("toggle storage display ",Boolean.toString(enableStorageItemDisplay));
                enableToolTipsDisplay=getOrSetDefault(modConfig,"enable-tooltips-display",true);
                Debug.info("toggle tooltips display ",Boolean.toString(enableToolTipsDisplay));
            }
            hotkey_load:{
                Map<String,Object> modConfig=(Map<String, Object>) data.get("hotkeys");
                if(modConfig==null)break hotkey_load;
                slimefunIdCopyHotkey=getOrSetDefault(modConfig,"slimefunid-copy","LEFT_CONTROL,C,BUTTON_1");
                Debug.info("load keybind slimefunid-copy :",slimefunIdCopyHotkey);
            }
            hotkey_toggle:{
                Map<String,Object> modConfig=(Map<String, Object>) data.get("hotkeys-toggle");
                if(modConfig==null)break hotkey_toggle;
                for(Map.Entry<String,Object> entry:modConfig.entrySet()){
                    toggleHotKeys.put(entry.getKey(),entry.getValue().toString());
                }
            }

        }catch (Throwable e){
            Debug.info("AN INTERNAL ERROR WHILE RELOADING CONFIG");
            Debug.info(e);
        }
        try(FileWriter writer=new FileWriter(configFile)){
            yaml.dump(data,writer);
        }catch (Throwable e){
            Debug.info("AN INTERNAL ERROR WHILE WRITING CONFIG");
            Debug.info(e);
        }
    }
    public static <T extends Object> T getOrSetDefault(Map<String,Object> config,String key,T defaultValue){

        Object value=config.get(key);
        try{
            if(value!=null)
             return (T)value;
        }catch (Throwable e){
        }
        config.put(key,defaultValue);
        return defaultValue;
    }
    //为什么我要把接下来做的东西放在这？
    //Todo 挖矿助手
    //@ServerPlayNetworkHandler
    //@ServerPlayerInteractionManager
    //@ClientPlayerInteractionManager
    //Todo 便捷移动
    //todo 便捷多方块 自带连点

}
