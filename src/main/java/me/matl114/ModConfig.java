package me.matl114;

import lombok.Getter;
import me.matl114.managers.Configs;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        HashMap<String,Object> defaults=null;
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
    public static InputStream loadInternal(String configName){
        return SlimefunHelper.getInstance().getClass().getResourceAsStream("/"+configName);
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
    private static Pattern slimefunModelPathPattern ;

    @Getter
    private static HashMap<String,String> toggleHotKeys=new HashMap<>();
    public static String getToggleHotkeys(String key){
        return toggleHotKeys.get(key);
    }
    @Getter
    private static HashMap<String, String> funcHotKeys=new HashMap<>();
    public static String getFuncHotKeys(String key){
        return funcHotKeys.get(key);
    }
    public static String getHotkeys(String key){
        return key.startsWith("hotkeys-toggle.") ? getToggleHotkeys( key.substring("hotkeys-toggle.".length()) ): (key.startsWith("hotkeys.")? getFuncHotKeys(key.substring("hotkeys.".length()) ): null);
    }
    public static void reloadModConfig(){
        Debug.info("Reloading Mod Config");
        InputStream configFile=loadInternal("slimefunhelper-config.yml");
        Yaml yaml = new Yaml();
        Map<String, Object> data=new HashMap<>();
        data=yaml.load(configFile);
        hotkey_load:{
            Map<String,Object> modConfig=(Map<String, Object>) data.get("hotkeys");
            if(modConfig==null)break hotkey_load;
            for(Map.Entry<String,Object> entry:modConfig.entrySet()){
                funcHotKeys.put(entry.getKey(),entry.getValue().toString());
            }
        }
        hotkey_toggle:{
            Map<String,Object> modConfig=(Map<String, Object>) data.get("hotkeys-toggle");
            if(modConfig==null)break hotkey_toggle;
            for(Map.Entry<String,Object> entry:modConfig.entrySet()){
                toggleHotKeys.put(entry.getKey(),entry.getValue().toString());
            }
        }
//
//            configsValues:{
//                Map<String,Object> modConfig=(Map<String, Object>) data.get("configValue");
//                if(modConfig==null)break configsValues;
//                for(Map.Entry<String,Object> entry:modConfig.entrySet()){
//                    int value=Utils.parseIntOrDefault(entry.getValue().toString(),0);
//                    if(configValues.containsKey(entry.getKey())){
//                        configValues.get(entry.getKey()).set(value);
//                    }else {
//                        configValues.put(entry.getKey(),new AtomicInteger() );
//                    }
//                }
//            }
//            configsOptions:{
//                Map<String,Object> modConfig=(Map<String, Object>) data.get("configOption");
//                if(modConfig==null)break configsOptions;
//                for(Map.Entry<String,Object> entry:modConfig.entrySet()){
//                    boolean value=Boolean.parseBoolean(entry.getValue().toString());
//                    if(configOptions.containsKey(entry.getKey())){
//                        configOptions.get(entry.getKey()).set(value);
//                    }else {
//                        configOptions.put(entry.getKey(),new AtomicBoolean(value) );
//                    }
//                }
//            }
        Configs.loadConfigs();
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
    //@ServerPlayNetworkHandler
    //@ServerPlayerInteractionManager
    //@ClientPlayerInteractionManager

}
