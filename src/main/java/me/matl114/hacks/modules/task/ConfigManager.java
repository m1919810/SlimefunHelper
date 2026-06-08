package me.matl114.hacks.modules.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import me.matl114.commands.MainCommand;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.TabResult;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class ConfigManager extends BaseModule {
    public ConfigManager() {
        super("Config");
    }

    private final ModulePath root = makePath(Configs.MISC_CONFIG, "config");
    private final ListRef privacyPathKeywords = builder(root.add("privacy-protection-path-keywords"), ListRef.TYPE)
            .defaultValue(List.of("chat", "http"))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapConfigCommand);
    }

    public void bootStrapConfigCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.subMainBuilder().name("config").build();
        main.subBuilder(SubCommand.taskBuilder())
                .name("open")
                .helper("打开配置文件界面")
                .post(e -> e.executor(CommandContext.run(this::onOpen)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("reload")
                .helper("重载配置文件")
                .post(e -> e.executor(CommandContext.run(this::onReload)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("save")
                .helper("<path> 保存当前配置快照")
                .arg(SimpleCommandArgs.argumentBuilder().name("path").build())
                .post(e -> e.executor(CommandContext.run(this::onSave)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("load")
                .helper("<path> 加载配置快照")
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("path")
                        .tabCompletor(TabResult.ofStreamSupplier(this::getSnapshotFileSuggestions))
                        .build())
                .post(e -> e.executor(CommandContext.run(this::onLoad)))
                .complete();
    }

    public void onOpen() {
        Tasks.scheduleDelayed(MainTasks::openConfigNewStyleScreen, 1);
        Debug.chat(Text.literal("成功打开配置文件界面").formatted(Formatting.GREEN));
    }

    public void onReload() {
        Tasks.scheduleDelayed(Config::reloadAll, 1);
        Debug.chat(Text.literal("成功重载配置文件").formatted(Formatting.GREEN));
    }

    public static final Codec<MapRef> CONFIG_CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> {
                Object value = dynamic.convert(ConfigOp.INSTANCE).getValue();
                if (value instanceof MapRef mapRef) {
                    return DataResult.success(mapRef);
                }
                return DataResult.error(() -> "Config payload is not a MapRef: " + value);
            },
            mapRef -> new Dynamic<>(ConfigOp.INSTANCE, mapRef));

    public static record ConfigSnapshot(Map<Identifier, MapRef> snapSnot) {
        public static final Codec<ConfigSnapshot> CODEC =
                Codec.unboundedMap(Identifier.CODEC, CONFIG_CODEC).xmap(ConfigSnapshot::new, ConfigSnapshot::snapSnot);
    }

    public void onSave(ArgumentInputStream args) {
        String rawPath = args.nextNonnullString();
        String fileName;
        try {
            fileName = normalizeSnapshotFileName(rawPath);
        } catch (IllegalArgumentException e) {
            Debug.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
            return;
        }

        List<String> privacyKeywords = privacyPathKeywords.get();

        Map<Identifier, MapRef> snapshotMap = new LinkedHashMap<>();
        for (Config config : Config.REGISTRY) {
            if (config.getRegistryKey() == null) {
                continue;
            }
            Identifier identifier = config.getRegistryKey().getValue();
            if (isPrivacyConfig(identifier, privacyKeywords)) {
                Debug.chat(Text.literal("保存时跳过配置: " + identifier + " 以避免隐私信息泄露(可在设置中调整关键词)")
                        .formatted(Formatting.YELLOW));
                continue;
            }
            snapshotMap.put(identifier, config.asRef());
        }

        ConfigSnapshot snapshot = new ConfigSnapshot(snapshotMap);
        DataResult<me.matl114.managers.config.Ref<?>> encoded =
                ConfigSnapshot.CODEC.encodeStart(ConfigOp.INSTANCE, snapshot);
        if (encoded.isError()) {
            String message = encoded.error().map(DataResult.Error::message).orElse("未知编码错误");
            Debug.chat(Text.literal("保存配置快照失败: " + message).formatted(Formatting.RED));
            return;
        }

        FileStorage storage = FileManager.getInstance().getConfigStorage(fileName);
        try {
            storage.write(encoded.result().get(), ConfigOp.INSTANCE);
            storage.write();
            Debug.chat(Text.literal("成功保存配置快照: " + fileName + " ,点击本文本打开文件夹")
                    .formatted(Formatting.GREEN)
                    .styled(style -> style.withClickEvent(
                            ChatUtils.getOpenFile(storage.getFile().getParentFile()))));
        } finally {
            storage.markDeprecated(true);
        }
    }

    public void onLoad(ArgumentInputStream args) {
        String rawPath = args.nextArg();
        if (rawPath == null) {
            promptSnapshotFolderImport();
            return;
        }
        String fileName;
        try {
            fileName = normalizeSnapshotFileName(rawPath);
        } catch (IllegalArgumentException e) {
            Debug.chat(Text.literal(e.getMessage()).formatted(Formatting.RED));
            promptSnapshotFolderImport();
            return;
        }

        FileStorage storage = FileManager.getInstance().getConfigStorage(fileName, false);
        if (storage == null) {
            Debug.chat(Text.literal("配置快照不存在: " + fileName).formatted(Formatting.RED));
            promptSnapshotFolderImport();
            return;
        }
        try {
            storage.read();
            Ref<?> rawSnapshot = storage.asReadOnly(ConfigOp.INSTANCE);
            DataResult<ConfigSnapshot> decoded = ConfigSnapshot.CODEC.parse(ConfigOp.INSTANCE, rawSnapshot);
            if (decoded.isError()) {
                String message = decoded.error().map(DataResult.Error::message).orElse("未知解码错误");
                Debug.chat(Text.literal("加载配置快照失败: " + message).formatted(Formatting.RED));
                return;
            }

            ConfigSnapshot snapshot = decoded.result().get();
            for (Map.Entry<Identifier, MapRef> entry : snapshot.snapSnot().entrySet()) {
                Config config = Config.REGISTRY.get(entry.getKey());
                if (config == null) {
                    Debug.chat(Text.literal("跳过未注册配置: " + entry.getKey()).formatted(Formatting.YELLOW));
                    continue;
                }
                for (LeafEntry leaf : flattenMapRef(entry.getValue())) {
                    Ref<?> currentRef = config.get(leaf.path());
                    if (currentRef == null) {
                        continue;
                    }
                    leaf.value().copyValueTo(currentRef);
                }
            }
            Debug.chat(Text.literal("成功加载配置快照" + fileName).formatted(Formatting.GREEN));
        } finally {
            storage.markDeprecated(true);
        }
    }

    private boolean isPrivacyConfig(Identifier identifier, List<String> privacyKeywords) {
        String path = identifier.getPath();
        return privacyKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .anyMatch(path::contains);
    }

    private void promptSnapshotFolderImport() {
        Debug.chat(Text.literal("请将保存的 config 文件拖到配置快照目录中，点击本文本打开文件夹")
                .formatted(Formatting.YELLOW)
                .styled(style -> style.withClickEvent(ChatUtils.getOpenFile(FileManager.CONFIG_SAVE_FOLDER))));
    }

    private Stream<String> getSnapshotFileSuggestions() {
        File[] files = FileManager.CONFIG_SAVE_FOLDER.listFiles(
                file -> file.isFile() && file.getName().endsWith(".nbt"));
        if (files == null || files.length == 0) {
            return Stream.empty();
        }
        return Arrays.stream(files).map(File::getName).sorted();
    }

    private static List<LeafEntry> flattenMapRef(MapRef mapRef) {
        List<LeafEntry> result = new ArrayList<>();
        flattenMapRef(result, new ArrayList<>(), mapRef);
        return result;
    }

    private static void flattenMapRef(List<LeafEntry> result, List<String> path, MapRef mapRef) {
        for (Map.Entry<String, Ref<?>> entry : mapRef.getValue().entrySet()) {
            path.add(entry.getKey());
            Ref<?> value = entry.getValue();
            if (value instanceof MapRef child) {
                flattenMapRef(result, path, child);
            } else {
                result.add(new LeafEntry(path.toArray(String[]::new), value));
            }
            path.remove(path.size() - 1);
        }
    }

    private record LeafEntry(String[] path, Ref<?> value) {}

    private static String normalizeSnapshotFileName(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("配置快照名称不能为空");
        }
        if (path.contains("/") || path.contains("\\")) {
            throw new IllegalArgumentException("配置快照名称不能包含路径分隔符");
        }

        int suffixIndex = path.lastIndexOf('.');
        String baseName = suffixIndex > 0 ? path.substring(0, suffixIndex) : path;
        if (baseName.isEmpty() || ".".equals(baseName) || "..".equals(baseName)) {
            throw new IllegalArgumentException("配置快照名称不是合法文件名");
        }

        for (int i = 0; i < baseName.length(); i++) {
            char ch = baseName.charAt(i);
            if (ch < 32 || "<>:\"/\\|?*".indexOf(ch) >= 0) {
                throw new IllegalArgumentException("配置快照名称不是合法文件名: " + rawPath);
            }
        }
        return baseName + ".nbt";
    }
}
