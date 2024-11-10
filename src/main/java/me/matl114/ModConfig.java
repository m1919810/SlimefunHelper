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
        return configFile;
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
                enableSlimefunCmdOverride=getOrSetDefault(modConfig,"enable-slimefun-cmd-override",true);
                enableItemModelOvevrride=getOrSetDefault(modConfig,"enable-item-model-override",true);
                enableStorageItemDisplay=getOrSetDefault(modConfig,"enable-storageitem-display",true);
                enableToolTipsDisplay=getOrSetDefault(modConfig,"enable-tooltips-display",true);
            }
            hotkey_load:{
                Map<String,Object> modConfig=(Map<String, Object>) data.get("hotkeys");
                if(modConfig==null)break hotkey_load;
                slimefunIdCopyHotkey=getOrSetDefault(modConfig,"slimefunid-copy","LEFT_CONTROL,C,BUTTON_1");
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
}
