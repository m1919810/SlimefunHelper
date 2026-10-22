package me.matl114.managers.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import me.matl114.managers.*;
import me.matl114.utils.FileUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class ConfigImpl extends AbstractConfig implements RefMap, Config {
    private final File file;

    protected Map<String, Object> fileMap;

    public ConfigImpl(String name, @Nonnull File file, @Nonnull Map<String, Object> fileConfig) {
        super(name, fileConfig);
        this.file = file;
        this.fileMap = new LinkedHashMap<>(fileConfig);
    }

    public ConfigImpl(String name, @Nonnull File file) {
        this(name, (File) file, ConfigLoader.loadYamlConfig(file));
    }

    public synchronized void save() {
        File file = this.file;
        if (file == null) return;
        File absoluteFile = file.getAbsoluteFile();
        File parentDir = absoluteFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
            logger.log(
                    Level.SEVERE,
                    "Exception while saving a Config file: failed to create parent directories for {0}",
                    absoluteFile);
            return;
        }
        Map savedData = (Map) this.ref.getAsPrimitive();

        DumperOptions options = new DumperOptions();
        options.setIndent(2); // 设置缩进为 2 空格
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // 使用块风格
        options.setPrettyFlow(true); // 启用漂亮的流式显示
        Yaml yaml = new Yaml(options);
        File tempFile = new File(absoluteFile.getPath() + ".tmp");
        boolean saved = false;
        try (FileOutputStream fout = new FileOutputStream(tempFile);
                OutputStreamWriter owrite = new OutputStreamWriter(fout, StandardCharsets.UTF_8)) {
            yaml.dump(savedData, owrite);
            owrite.flush();
            fout.getFD().sync();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception while saving a Config file", e);
        }

        if (!tempFile.exists()) {
            return;
        }

        try {
            FileUtils.saveTempFile(tempFile, file);
            saved = true;
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "Exception while replacing a Config file", e);
        } finally {
            if (saved) {
                markForSave = false;
            }
        }
    }

    public void reload() {
        if (this.file != null && this.file.isFile()) {
            ref.copyValueFrom(Refs.transferConfig(ConfigLoader.loadYamlConfig(this.file)));
        }
    }
}
