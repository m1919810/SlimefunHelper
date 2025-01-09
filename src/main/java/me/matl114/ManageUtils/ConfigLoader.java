package me.matl114.ManageUtils;

import com.google.common.base.Charsets;
import it.unimi.dsi.fastutil.Hash;
import me.matl114.SlimefunHelper;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;

public class ConfigLoader {

    public static Config SERVER_CONFIG;
    public static void copyFile(File file, String internalFileName) {
        if(!file.exists()){
            try{
                if(!file.getParentFile().exists()) {
                    Files.createDirectories(file.toPath().getParent());
                }
                Files.copy(SlimefunHelper.getInstance().getClass().getResourceAsStream("/"+internalFileName), file.toPath());
            }catch (Throwable e){
                Debug.info("创建配置文件时找不到相关默认配置文件,即将生成空文件");
                try{
                    Files.createDirectories(file.toPath().getParent());
                    Files.createFile(file.toPath());
                }catch (IOException e1){
                    Debug.info("创建空配置文件失败!");
                }

            }
        }

    }
    public static Config loadInternalConfig(String internalFileName,String customName){
        
        try{
            return new Config(customName,null,loadYamlConfig((Reader)( new InputStreamReader(SlimefunHelper.getInstance().getClass().getResourceAsStream("/"+ internalFileName ), Charsets.UTF_8))));
        }catch (Throwable e){
            Debug.info("failed to load internal config " + internalFileName + ".yml, Error: " + e.getMessage());
            return null;
        }
    }
    public static Config loadExternalConfig(String name,String customName){
        final File cfgFile =FabricLoader.getInstance().getConfigDir().resolve(name).toFile();
        String fileName=cfgFile.getName();
        copyFile(cfgFile, fileName);
        return new Config(customName,cfgFile);
    }
    public static HashMap<String, Object> loadYamlConfig(File file){
        try{
            return loadYamlConfig(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        }catch (Throwable e){
            Debug.info("failed to load yaml config " + file.getName() + ".yml, Error: " + e.getMessage());
            throw new RuntimeException();
        }
    }
    public static HashMap<String, Object> loadYamlConfig(Reader reader){
        Yaml yaml = new Yaml();
        HashMap obj= yaml.load(reader);
        return obj==null?new HashMap():obj;
    }


}
