package me.matl114;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.HashMap;
import me.matl114.managers.Configs;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

public class ModConfig {

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

    public static void reloadModConfig() {
        Debug.info("Reloading Mod Config");
        Configs.loadConfigs();
    }
}
