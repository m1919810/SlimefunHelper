package me.matl114.managers;

import com.google.common.base.Charsets;
import me.matl114.SlimefunHelper;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
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

    public static String loadExternalJson(String name){
        final File cfg = FabricLoader.getInstance().getConfigDir().resolve(name).toFile();
        if(!cfg.exists()){
            try{
                if(!cfg.getParentFile().exists()) {
                    Files.createDirectories(cfg.toPath().getParent());
                }
                Files.createFile(cfg.toPath());
                Files.writeString(cfg.toPath(), "{}");
            }catch (Throwable e){
                Debug.info("创建新json文件失败: 文件:", cfg, "错误:");
                Debug.info(e);
                return "{}";
            }
        }
        try{
            return Files.readString(cfg.toPath(), StandardCharsets.UTF_8);
        }catch (Throwable e){
            Debug.info("读取json文件失败: 文件:", cfg, "错误:");
            Debug.info(e);
            return "{}";
        }
    }
    public static void saveToFile(String name, String data) throws IOException {
        final File cfg = FabricLoader.getInstance().getConfigDir().resolve(name).toFile();
        if(!cfg.exists()) {
            if (!cfg.getParentFile().exists()) {
                Files.createDirectories(cfg.toPath().getParent());
            }
            Files.createFile(cfg.toPath());
        }
        Files.writeString(cfg.toPath(), data);
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
