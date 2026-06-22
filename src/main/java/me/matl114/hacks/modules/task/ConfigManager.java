package me.matl114.hacks.modules.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.*;
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
import me.matl114.utils.commands.CommandUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.TabResult;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class ConfigManager extends BaseModule {
    public ConfigManager() {
        super("ConfigManager");
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
        SimpleCommandArgs.Argument configNameArgument = SimpleCommandArgs.argumentBuilder()
                .name("config_name")
                .tabCompletor(TabResult.ofStreamSupplier(() -> Config.REGISTRY.stream()
                        .map(Config::getRegistryKey)
                        .filter(Objects::nonNull)
                        .map(registryKey -> registryKey.getValue().toString())))
                .build();

        SimpleCommandArgs.Argument configOrAllNameArgument = SimpleCommandArgs.argumentBuilder()
                .name("config_name")
                .defaultValue("all")
                .select("all")
                .tabCompletor(TabResult.ofStreamSupplier(() -> Config.REGISTRY.stream()
                        .map(Config::getRegistryKey)
                        .filter(Objects::nonNull)
                        .map(registryKey -> registryKey.getValue().toString())))
                .build();
        SimpleCommandArgs.Argument pathArgument = SimpleCommandArgs.argumentBuilder()
                .name("path")
                .tabCompletor(TabResult.ofDispatcher((sender, configName) -> {
                    Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
                    if (config == null) {
                        return Stream.empty();
                    }
                    return Stream.concat(config.getVisiblePaths().stream(), config.getPaths().stream())
                            .distinct()
                            .sorted();
                }))
                .build();
        SimpleCommandArgs.Argument pathPrefixArgument = SimpleCommandArgs.argumentBuilder()
                .name("path_prefix")
                .defaultValue("")
                .tabCompletor(TabResult.ofDispatcher((sender, configName) -> getPathPrefixSuggestions(configName)))
                .build();
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
                .helper("<path> <config_name=all> <path_prefix=\"\"> 保存当前配置快照")
                .arg(SimpleCommandArgs.argumentBuilder().name("path").build())
                .arg(configOrAllNameArgument)
                .arg(pathPrefixArgument)
                .post(e -> e.executor(CommandContext.run(this::onSave)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("load")
                .helper("<path> <config_name=all> <path_prefix=\"\"> 加载配置快照")
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("path")
                        .tabCompletor(TabResult.ofStreamSupplier(
                                CommandUtils.fileSupplier(FileManager.CONFIG_SAVE_FOLDER, (sx) -> {
                                    return sx.endsWith(".nbt") || sx.endsWith(".dat");
                                })))
                        .build())
                .arg(configOrAllNameArgument)
                .arg(pathPrefixArgument)
                .post(e -> e.executor(CommandContext.run(this::onLoad)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("set")
                .helper("<config_name> <path> <string> 设置配置项")
                .arg(configNameArgument)
                .arg(pathArgument)
                .arg(SimpleCommandArgs.argumentBuilder().name("string").build())
                .post(e -> e.executor(CommandContext.run(this::onSet)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("reset")
                .helper("<config_name> <path> 重置配置项为默认值")
                .arg(configNameArgument)
                .arg(pathArgument)
                .post(e -> e.executor(CommandContext.run(this::onReset)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("resetall")
                .helper("<config_name> <all> 重置某个配置文件为默认值")
                .arg(configOrAllNameArgument)
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("confirm")
                        .defaultValue("")
                        .select("--confirm")
                        .build())
                .post(e -> e.executor(CommandContext.run(this::onResetAll)))
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

    public void onSet(ArgumentInputStream args) {
        String configName = args.nextNonnullString();
        String rawPath = args.nextNonnullString();
        String value = args.nextNonnullString();

        Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
        if (config == null) {
            Debug.chat(Text.literal("未找到配置文件: " + configName).formatted(Formatting.RED));
            return;
        }

        Ref<?> ref = config.get(rawPath.split("\\."));
        if (ref == null) {
            Debug.chat(Text.literal("未找到配置项: " + configName + "." + rawPath).formatted(Formatting.RED));
            return;
        }

        AttrKeyValue<?> keyValue = ref.createKeyValue(rawPath);
        keyValue.valueChange(this, value);
        if (!keyValue.isValidate()) {
            Debug.chat(Text.literal("配置项格式不正确: " + configName + "." + rawPath).formatted(Formatting.RED));
            return;
        }
        Debug.chat(Text.literal("成功设置配置项: " + configName + "." + rawPath).formatted(Formatting.GREEN));
    }

    public void onReset(ArgumentInputStream args) {
        String configName = args.nextNonnullString();
        String rawPath = args.nextNonnullString();

        Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
        if (config == null) {
            Debug.chat(Text.literal("未找到配置文件: " + configName).formatted(Formatting.RED));
            return;
        }

        Ref<?> ref = config.get(rawPath.split("\\."));
        if (ref == null) {
            Debug.chat(Text.literal("未找到配置项: " + configName + "." + rawPath).formatted(Formatting.RED));
            return;
        }
        if (!ref.hasDefaultValue()) {
            Debug.chat(Text.literal("配置项没有默认值: " + configName + "." + rawPath).formatted(Formatting.RED));
            return;
        }

        ref.resetValue();
        Debug.chat(Text.literal("成功重置配置项: " + configName + "." + rawPath).formatted(Formatting.GREEN));
    }

    public void onResetAll(ArgumentInputStream args) {
        String configName = args.nextNonnullString();
        String confirm = args.nextNonnullString();
        if ("--confirm".equals(confirm)) {
            if ("all".equalsIgnoreCase(configName)) {
                for (var config : Config.REGISTRY) {
                    for (var path : config.getVisiblePaths()) {
                        var ref = config.get(path);
                        if (ref != null && ref.hasDefaultValue()) {
                            ref.resetValue();
                        }
                    }
                }
            } else {
                var config = Config.REGISTRY.get(Identifier.tryParse(configName));
                if (config == null) {
                    Debug.chat(Text.literal("未找到配置文件: " + configName).formatted(Formatting.RED));
                    return;
                }
                for (var path : config.getVisiblePaths()) {
                    var ref = config.get(path);
                    if (ref != null && ref.hasDefaultValue()) {
                        ref.resetValue();
                    }
                }
            }
        } else {
            Debug.chat(
                    ChatUtils.stringToText("&c该指令将会重置部分配置文件,是否确认? "),
                    Text.literal("[确认]")
                            .formatted(Formatting.RED)
                            .formatted(Formatting.BOLD)
                            .styled(style -> style.withClickEvent(
                                    ChatUtils.getSuggestCommand("/!!config resetall " + configName + " --confirm"))));
        }
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
        String allName = args.nextNonnullString();
        String pathPrefix = args.nextNonnullString().trim();
        ConfigSnapshot snapshot;
        if ("all".equalsIgnoreCase(allName)) {
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
                snapshotMap.put(identifier, filterSnapshotByPathPrefix(config, pathPrefix));
            }

            snapshot = new ConfigSnapshot(snapshotMap);
        } else {
            Config config = Config.REGISTRY.get(Identifier.tryParse(allName));
            if (config == null) {
                Debug.chat(Text.literal("未找到配置文件: " + allName).formatted(Formatting.RED));
                return;
            }
            Map<Identifier, MapRef> snapshotMap = new LinkedHashMap<>();
            Identifier identifier = config.getRegistryKey().getValue();
            snapshotMap.put(identifier, filterSnapshotByPathPrefix(config, pathPrefix));
            snapshot = new ConfigSnapshot(snapshotMap);
        }

        DataResult<me.matl114.managers.config.Ref<?>> encoded =
                ConfigSnapshot.CODEC.encodeStart(ConfigOp.INSTANCE, snapshot);
        if (encoded.isError()) {
            String message = encoded.error().map(DataResult.Error::message).orElse("未知编码错误");
            Debug.chat(Text.literal("保存配置快照失败: " + message).formatted(Formatting.RED));
            return;
        }

        try (FileStorage storage = FileManager.getInstance().getConfigStorage(fileName)) {
            storage.write(encoded.result().get(), ConfigOp.INSTANCE);
            storage.write();
            Debug.chat(Text.literal("成功保存配置快照: " + fileName + " ,点击本文本打开文件夹")
                    .formatted(Formatting.GREEN)
                    .styled(style -> style.withClickEvent(
                            ChatUtils.getOpenFile(storage.getFile().getParentFile()))));
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
        String name = args.nextNonnullString();
        String prefix = args.nextNonnullString();
        Config config = null;
        if (!"all".equalsIgnoreCase(name)) {
            config = Config.REGISTRY.get(Identifier.tryParse(name));
            if (config == null) {
                Debug.chat(Text.literal("未找到配置文件: " + name).formatted(Formatting.RED));
                return;
            }
        }

        try (FileStorage storage = FileManager.getInstance().getConfigStorage(fileName, true, false)) {
            if (storage == null) {
                Debug.chat(Text.literal("配置快照不存在: " + fileName).formatted(Formatting.RED));
                promptSnapshotFolderImport();
                return;
            }
            storage.read();
            Ref<?> rawSnapshot = storage.asReadOnly(ConfigOp.INSTANCE);
            DataResult<ConfigSnapshot> decoded = ConfigSnapshot.CODEC.parse(ConfigOp.INSTANCE, rawSnapshot);
            if (decoded.isError()) {
                String message = decoded.error().map(DataResult.Error::message).orElse("未知解码错误");
                Debug.chat(Text.literal("加载配置快照失败: " + message).formatted(Formatting.RED));
                return;
            }

            ConfigSnapshot snapshot = decoded.result().get();
            if (config == null) {
                for (Map.Entry<Identifier, MapRef> entry : snapshot.snapSnot().entrySet()) {
                    Config config2 = Config.REGISTRY.get(entry.getKey());
                    if (config2 == null) {
                        Debug.chat(Text.literal("跳过未注册配置: " + entry.getKey()).formatted(Formatting.YELLOW));
                        continue;
                    }
                    for (LeafEntry leaf : flattenMapRef(entry.getValue())) {
                        String pathStr = String.join(".", leaf.path());
                        if (pathStr.startsWith(prefix)) {
                            Ref<?> currentRef = config2.get(leaf.path());
                            if (currentRef == null) {
                                continue;
                            }
                            leaf.value().copyValueTo(currentRef);
                        }
                    }
                }
            } else {
                MapRef mapRef2 = snapshot.snapSnot().get(config.getRegistryKey().getValue());
                if (mapRef2 != null) {
                    for (LeafEntry leaf : flattenMapRef(mapRef2)) {
                        String pathStr = String.join(".", leaf.path());
                        if (pathStr.startsWith(prefix)) {
                            Ref<?> currentRef = config.get(leaf.path());
                            if (currentRef == null) {
                                continue;
                            }
                            leaf.value().copyValueTo(currentRef);
                        }
                    }
                }
            }

            Debug.chat(Text.literal("成功加载配置快照" + fileName).formatted(Formatting.GREEN));
        }
    }

    private Stream<String> getPathPrefixSuggestions(String configName) {
        if ("all".equalsIgnoreCase(configName)) {
            return Config.REGISTRY.stream()
                    .flatMap(config -> config.getVisiblePaths().stream())
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted();
        }
        Config config = Config.REGISTRY.get(Identifier.tryParse(configName));
        if (config == null) {
            return Stream.empty();
        }
        return config.getVisiblePaths().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted();
    }

    private MapRef filterSnapshotByPathPrefix(Config config, String pathPrefix) {
        if (pathPrefix.isEmpty()) {
            return config.asRef();
        }
        String normalizedPathPrefix =
                pathPrefix.endsWith(".") ? pathPrefix.substring(0, pathPrefix.length() - 1) : pathPrefix;
        MapRef filteredSnapshot = new MapRef();
        for (LeafEntry leaf : flattenMapRef(config.asRef())) {
            String leafPath = String.join(".", leaf.path());
            if (leafPath.equals(normalizedPathPrefix) || leafPath.startsWith(normalizedPathPrefix + ".")) {
                filteredSnapshot.setValue(leaf.value(), leaf.path());
            }
        }
        return filteredSnapshot;
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
