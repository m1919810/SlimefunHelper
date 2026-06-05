package me.matl114.managers;

import com.google.common.base.Preconditions;
import me.matl114.SlimefunHelper;
import me.matl114.managers.config.Config;
import me.matl114.managers.file.FileStorage;
import me.matl114.managers.file.NBTFileStorageImpl;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager {
    public static FileManager getInstance(){
        return INSTANCE;
    }
    public static final String FILE_SAVE_PATH = SlimefunHelper.MOD_ID;
    public static final String INTERNAL_SAVE_PATH = "internal";
    public static final String CONFIG_SAVE_PATH = "config";
    public static final File FOLDER = FabricLoader.getInstance().getGameDir().resolve(FILE_SAVE_PATH).toFile();
    public static final File INTERNAL_FOLDER = new File(FOLDER, INTERNAL_SAVE_PATH);
    public static final File CONFIG_SAVE_FOLDER = new File(FOLDER, CONFIG_SAVE_PATH);
    public static final Map<File, FileStorage> trackedFileStorages = new ConcurrentHashMap<>();

    protected static final FileManager INSTANCE = new FileManager();
    private FileManager(){
        // create files
        if(!FOLDER.exists() || !FOLDER.isDirectory()){
            Preconditions.checkArgument(FOLDER.mkdirs(), "File create failure");
        }
        if(!INTERNAL_FOLDER.exists() || !INTERNAL_FOLDER.isDirectory()){
            Preconditions.checkArgument(INTERNAL_FOLDER.mkdirs(), "File create failure");
        }
        ScheduleService.launchAsyncRepeatTask(this::onScheduleSave, 15 * 1000, 15 * 1000);
    }


    private  void onScheduleSave(){
        for (Map.Entry<File, FileStorage> entry : trackedFileStorages.entrySet()) {
            FileStorage storage = entry.getValue();
            if (storage.isDirty()) {
                storage.write();
                storage.markDirty(false);
            }
        }
    }




    public FileStorage getStorage(File file, boolean reload){
        FileStorage storage;
        storage = trackedFileStorages.get(file);
        if(storage != null){
            if(reload){
                storage.read();
            }
            return storage;
        }
        storage = createFileStorage(file);
        trackedFileStorages.put(file, storage);
        return storage;
    }

    public FileStorage getStorage(File flie){
        return getStorage(flie, false);
    }

    public FileStorage getInternalStorage(String filePath){
        return getStorage(new File(INTERNAL_FOLDER, filePath), false);
    }


    public void reloadAll(){
        for (var re : new ArrayList<> (trackedFileStorages.keySet())) {
            getStorage(re, true);
        }
    }


    private FileStorage createFileStorage(File file){
        String name = file.getName();
        // 简单根据扩展名判断是否为 NBT 文件（支持 .nbt, .dat）
        if (name.endsWith(".nbt") || name.endsWith(".dat")) {
            // 假设 NBTFileStorageImpl 构造函数接受 File
            return new NBTFileStorageImpl(file);
        } else {
            throw new UnsupportedOperationException("Unsupported file type: " + name + " (only .nbt/.dat supported for now)");
        }
    }



















    public static File loadOrUseInternal(String configName) {
        final File configFile =
            FabricLoader.getInstance().getConfigDir().resolve(configName).toFile();
        if (!configFile.exists()) {
            try {
                if (!configFile.getParentFile().exists()) {
                    Files.createDirectories(configFile.toPath().getParent());
                }
                Files.copy(
                    SlimefunHelper.getInstance().getClass().getResourceAsStream("/" + configName),
                    configFile.toPath());
            } catch (Throwable e) {
                Debug.info("AN INTERNAL ERROR WHILE LOADING DEFAULT CONFIG");
                Debug.info(e);
                return null;
            }
        }
        // sync with internal
        Yaml yaml = new Yaml();
        HashMap<String, Object> config = new HashMap<>();
        HashMap<String, Object> defaults = null;
        try (FileReader readerConfig = new FileReader(configFile)) {
            config = yaml.load(readerConfig);
            config = (config == null ? new HashMap<>() : config);
            defaults = yaml.load(SlimefunHelper.getInstance().getClass().getResourceAsStream("/" + configName));
            defaults = (defaults == null ? new HashMap<>() : defaults);
            syncKeys(config, defaults);
        } catch (Throwable e) {
            Debug.info("AN INTERNAL ERROR WHILE LOADING DEFAULT CONFIG");
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            yaml.dump(config, writer);
        } catch (Throwable e) {
            Debug.info("AN INTERNAL ERROR WHILE WRITING CONFIG");
            Debug.info(e);
        }
        return configFile;
    }

    public static void syncKeys(HashMap config, HashMap defaults) {
        for (Object key : defaults.keySet()) {
            if (config.containsKey(key)) {
                Object value = config.get(key);
                Object defaultValue = defaults.get(key);
                if (value instanceof HashMap mp1 && defaultValue instanceof HashMap mp2) {
                    syncKeys(mp1, mp2);
                }
            } else {
                config.put(key, defaults.get(key));
            }
        }
    }


}
