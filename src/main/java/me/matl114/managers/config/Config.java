package me.matl114.managers.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Lifecycle;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import me.matl114.SlimefunHelper;
import me.matl114.managers.*;
import me.matl114.managers.input.IHotKey;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.managers.input.SimpleHotKey;
import me.matl114.managers.input.SimpleInputManager;
import me.matl114.utils.ReflectUtils;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.util.Identifier;
import org.lwjgl.system.NonnullDefault;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class Config implements RefMap {
    private final File file;
    private Logger logger;
    private String header;
    protected Map<String, Object> fileMap;
    protected MapRef ref;
    protected LinkedHashSet<String> buildOrder = new LinkedHashSet<>();

    @Getter
    private static final Set<Config> configs = new LinkedHashSet<>();

    public static final SimpleRegistry<Config> REGISTRY = new SimpleRegistry<>(
            RegistryKey.ofRegistry(Identifier.of("slimefunhelper", "configs")), Lifecycle.stable());
    private static final Set<Config> allConfigInternal = new LinkedHashSet<>();

    @Getter
    RegistryKey<Config> registryKey;

    public void registerGlobal() {
        configs.add(this);
        if (registryKey == null) {
            RegistryKey<Config> registryKey = RegistryKey.of(
                    REGISTRY.getKey(),
                    Identifier.of(
                            "slimefunhelper",
                            configName.toLowerCase(Locale.ROOT).replace(" ", "_")));
            this.registryKey = registryKey;
            REGISTRY.add(this.registryKey, this, RegistryEntryInfo.DEFAULT);
        }
    }

    public String getTranslationKey() {
        return "config.index." + this.registryKey.getValue().getPath();
    }

    public static void reloadAll() {
        allConfigInternal.forEach(v -> {
            // only reload the not dirty configs
            // and now trigger save
            if (v.markForSave) {
                v.save(v.file);
            } else {
                // may be ...
                v.reload();
            }
        });
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Config::configSaveTasks, "Config-Shutdown-Save"));
    }

    public static void launchSaveTasks() {
        ScheduleService.launchAsyncDelayedTask(Config::configSaveTasks, 1000);
    }

    public static void configSaveTasks() {
        for (var config : allConfigInternal) {
            if (config.file != null && config.markForSave) {
                config.save(config.file);
            }
        }
    }

    private static final long SAVE_TIME = 1000 * 15;

    static {
        ScheduleService.launchAsyncRepeatTask(Config::configSaveTasks, SAVE_TIME, SAVE_TIME);
    }

    @Setter
    @Getter
    private String configName;

    // todo: add custom hotkey manager with a custom config
    // todo: add custom bindings to custom hotkey manager , use hotkeyEvent to trigger toggle
    // todo: add CustomBindingsConfigurateScreen and CustomBindingsSelectScreen with a EDIT Button
    public static interface CustomSerializableConfig {}

    @Deprecated
    public static class RegistryRef<T> extends Ref<T> {
        // todo not implement yet
        @Override
        public T getValue() {
            return null;
        }

        @Override
        public void setValue(T value) {}

        @Override
        public void addUpdateListener(Consumer<T> updateListener) {}

        @Override
        public Object getAsPrimitive() {
            return "";
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return false;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            return false;
        }

        @Override
        public AttrKeyValue<T> _createKeyValue0(String key) {
            throw new UnsupportedOperationException();
        }
    }
    // todo : CustomRef, using JsonObject as base, use Codec to build upper object, add custom keyValue impl with custom

    //// TODO: RegistryRef
    //// TODO: RegistrySetRef
    //// TODO: ListRef
    // impl

    private final boolean autoSave = true;
    //    public Config autoSave(boolean save){
    //        autoSave=save;
    //        return this;
    //    }
    public Config(String name, @Nonnull File file, @Nonnull Map<String, Object> fileConfig) {
        this.configName = name;
        this.logger = Logger.getLogger(SlimefunHelper.MOD_ID);
        this.file = file;
        this.fileMap = new LinkedHashMap<>(fileConfig);
        this.ref = Refs.transferConfig(fileConfig);
        this.ref.setConfigReference(this);
        allConfigInternal.add(this);
    }

    public Config(String name, @Nonnull File file) {
        this(name, (File) file, ConfigLoader.loadYamlConfig(file));
    }

    @Nonnull
    public File getFile() {
        return this.file;
    }

    public void clear() {
        Iterator var1 = this.getKeys().iterator();
        while (var1.hasNext()) {
            String key = (String) var1.next();
            this.setValue((Object) null, key);
        }
    }
    //    private static boolean setValueInternal(Object node,Object value) {
    //        if (node instanceof Ref<?> refNode) {
    //            if (value instanceof Ref<?> refVal) {
    //                return refVal.copyValueTo(refNode);
    //            }
    //            ((Ref) refNode).setValue(value);
    //            return true;
    //        }
    //        return false;
    //    }

    private boolean setValue(Object value, @Nonnull String... path) {
        Ref refo = Refs.wrapInstance(value);
        boolean update = this.setValue(refo, path);
        if (update) {
            markForSave();
        }
        return update;
    }

    public void setValueNoNew(Object value, @Nonnull String... path) {
        setValue(value, path);
    }

    public Ref<?> get(@Nonnull String... path) {
        var re = this.ref.get(path);
        if (re != null) {
            re.setConfigReference(this);
        }
        return re;
    }

    public <T> Config validator(Predicate<T> validator, String... path) {
        Ref<T> ref = (Ref<T>) get(path);
        ref.addValidator(validator);
        return this;
    }

    @NonnullDefault
    private Ref getOrCreate(Ref defaultValue, @Nonnull String... path) {
        Ref result = this.ref.getOrCreate(defaultValue, path);
        if (result != null) {
            result.setConfigReference(this);
        }
        if (result == defaultValue) {
            if (autoSave) {
                markForSave();
            }
            return defaultValue;
        } else if (result == null) {
            throw new IllegalArgumentException(
                    "create fail ref validation: " + Arrays.stream(path).toList());
        }

        return result;
    }

    private boolean markForSave = false;

    public Config markForSave() {
        markForSave = true;
        return this;
    }

    public void save(@Nonnull File file) {
        File absoluteFile = file.getAbsoluteFile();
        File parentDir = absoluteFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
            this.logger.log(
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
            this.logger.log(Level.SEVERE, "Exception while saving a Config file", e);
        }

        if (!tempFile.exists()) {
            return;
        }

        try {
            replaceFile(tempFile, absoluteFile);
            saved = true;
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "Exception while replacing a Config file", e);
        } finally {
            if (!saved && tempFile.exists() && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
            if (saved) {
                markForSave = false;
            }
        }
    }

    private void replaceFile(File tempFile, File targetFile) throws IOException {
        try {
            Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean contains(@Nonnull String... path) {
        return get(path) != null;
    }

    @Nullable
    public StringRef getString(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof StringRef ref) {
            return ref;
        } else {

            return null;
        }
    }

    public KeyBindRef getKeyBind(String... path) {
        Object node = get(path);
        if (node instanceof KeyBindRef ref) {
            return ref;
        } else {

            return null;
        }
    }

    private boolean setValue(Ref<?> value, String... path) {
        return this.ref.setValue(value, path);
    }

    public ListRef getList(String... path) {
        Object node = get(path);
        if (node instanceof ListRef ref) {
            return ref;
        } else {

            return null;
        }
    }

    public <T extends ConfigEnum> EnumRef<T> getEnum(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof EnumRef<?> enumRef) {
            return (EnumRef<T>) enumRef;
        } else {
            return null;
        }
    }

    public IntRef getInt(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof IntRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    public DoubleRef getDouble(String... path) {
        Object node = get(path);
        if (node instanceof DoubleRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    public FlagRef getBoolean(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof FlagRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    public ObjectRef getObject(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof ObjectRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    public boolean createFile() {
        try {
            return this.file.createNewFile();
        } catch (IOException var2) {
            IOException e = var2;
            this.logger.log(Level.SEVERE, "Exception while creating a Config file", e);
            return false;
        }
    }

    @Nonnull
    public Set<String> getKeys() {
        return ref.getKeys();
    }

    @Nonnull
    public Set<String> getKeys(@Nonnull String... path) {
        var subMap = ref.get(path);
        if (subMap instanceof MapRef mapRef) {
            return mapRef.getKeys();
        } else {
            return Set.of();
        }
    }

    public void reload() {
        if (this.file != null) {
            Refs.transferConfig(ConfigLoader.loadYamlConfig(this.file)).copyValueTo(this.ref);
        }
    }

    public Set<String> getPaths() {
        return this.ref.getPaths();
    }

    public Set<String> getVisiblePaths() {
        return buildOrder;
    }

    public static String[] cutToPath(String rawPath) {
        return rawPath.split("\\.");
    }

    public static Set<String> getPaths(Map<String, Object> map, String parent) {
        Set<String> paths = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map map2) {
                Set<String> p = getPaths(map2, entry.getKey());
                paths.addAll(p.stream().map(str -> (String) parent + "." + str).collect(Collectors.toSet()));
            } else {
                paths.add(parent + "." + entry.getKey());
            }
        }
        return paths;
    }

    public <T> SettingBuilder<T> builder(Class<T> clazz) {
        return new SettingBuilder<>(asRef(), this, clazz);
    }

    public static void registerClassSupport(Class<?> clazz) {
        if (AutoRegisterType.class.isAssignableFrom(clazz)) {
            if (!AutoRegisterType.registered.contains(clazz)) {
                try {
                    Method method = ReflectUtils.getMethodsRecursively(clazz, "onLoad", Class.class)
                            .getFirst();
                    method.invoke(null, clazz);
                    AutoRegisterType.registered.add((Class<? extends AutoRegisterType>) clazz);
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Target class which implement AutoRegisterType does not implement public static void onLoad(Class) method");
                }
            }
        }
    }

    public MapRef asRef() {
        return this.ref;
    }

    public static class SettingBuilder<T> {
        protected final RefMap root;
        protected final Config rootConfig;

        public SettingBuilder(MapRef ref, Config rootConfig, Class<T> clazz) {
            this.root = ref;
            this.clazz = clazz;
            this.rootConfig = rootConfig;
            registerClassSupport(clazz);
        }

        protected final Class<T> clazz;
        protected String[] path;
        protected Ref<T> ref;

        @Nullable
        protected Optional<T> defaultValue;

        Runnable postTask;

        protected Ref<T> getRef() {
            if (ref == null) {
                Object obj = root.get(path);
                if (obj instanceof Ref<?>) {
                    ref = (Ref<T>) obj;
                } else {
                    ref = null;
                }
            }
            return ref;
        }

        public SettingBuilder<T> path(String... path) {
            this.path = path;
            this.rootConfig.buildOrder.add(String.join(".", path));
            return this;
        }

        public SettingBuilder<T> defaultValue(T val) {
            this.defaultValue = Optional.ofNullable(val);
            if (ref == null) {
                var instance = Refs.wrapInstance(val);
                ref = (Ref<T>) rootConfig.getOrCreate(instance, path);
                if (ref == instance) {
                    rootConfig.markForSave();
                }
            }
            ref.setDefaultValue(val);
            return this;
        }

        public SettingBuilder<T> validator(Predicate<T> va) {
            // validate default value
            Preconditions.checkArgument(
                    va.test(this.defaultValue.orElse(null)),
                    "config default value validation failure: {0}",
                    String.join(".", this.path));
            // validate current value
            addPost(() -> {
                if (!va.test(getRef().getValue())) {
                    getRef().setValue(this.defaultValue.orElse(null));
                }
                getRef().addValidator(va);
            });
            return this;
        }

        public SettingBuilder<T> updateListener(Consumer<T> va) {
            addPost(() -> {
                getRef().addUpdateListenerWithUpdate(va);
            });
            return this;
        }

        public SettingBuilder<T> registerHotkey(SimpleHotKey.InputHandler handler) {
            if (ref instanceof KeyBindRef keyBindRef) {
                String pathHotkey = String.join(".", this.path);
                IHotKey hotKey = SimpleInputManager.getInstance().getHotkey(pathHotkey);
                if (hotKey instanceof SimpleHotKey simple) {
                    // keep track, and
                    simple.setInputHandler(handler);
                    ((SettingBuilder<MultiKeyBind>) this).updateListener(simple::setKeyCodes);
                    return this;
                } else {
                    MultiKeyBind defaultKeyBind = this.defaultValue == null
                            ? new MultiKeyBind("")
                            : (MultiKeyBind) this.defaultValue.orElse(null);
                    SimpleHotKey hotKey1 = new SimpleHotKey(path, defaultKeyBind);
                    hotKey1.setInputHandler(handler);
                    SimpleInputManager.getInstance().registerHotKeys(hotKey1);
                    // register here
                    ((SettingBuilder<MultiKeyBind>) this).updateListener(hotKey1::setKeyCodes);
                    return this;
                }
            } else {
                throw new IllegalArgumentException("Not a hotkey");
            }
        }

        public <W extends Ref<T>> SettingBuilder<T> apply(Consumer<W> va) {
            addPost(() -> {
                va.accept((W) Objects.requireNonNull(getRef()));
            });
            return this;
        }

        public <W extends Ref<T>> W build() {
            Objects.requireNonNull(defaultValue);
            var ref1 = (W) Objects.requireNonNull(getRef());
            ref1.setConfigReference(rootConfig);
            if (postTask != null) {
                postTask.run();
            }
            return ref1;
        }

        protected void addPost(Runnable runnable) {
            if (postTask == null) {
                postTask = runnable;
            } else {
                Runnable currentPost = postTask;
                postTask = () -> {
                    currentPost.run();
                    runnable.run();
                };
            }
        }
    }
}
