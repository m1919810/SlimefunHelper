package me.matl114.managers.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
import me.matl114.utils.config.AttrKeyValue;
import org.lwjgl.system.NonnullDefault;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class
Config implements RefMap{
    private final File file;
    private Logger logger;
    private String header;
    //fixme: add schema and node structure
    protected Map<String,Object> fileMap;
    protected MapRef ref;












    @Getter
    private static final Set<Config> configs = new LinkedHashSet<>();
    private static final Set<Config> allConfigInternal = new LinkedHashSet<>();

    public void registerGlobal(){
        configs.add(this);
    }
    public static void reloadAll(){
        allConfigInternal.forEach(v -> {
            //only reload the not dirty configs
            // and now trigger save
            if(v.markForSave){
                v.save(v.file);
            }else{
                // may be ...
                v.reload();
            }
        });
    }
    public static void launchSaveTasks(){
        ScheduleService.launchAsyncDelayedTask(Config::configSaveTasks, 1000);
    }
    public static void configSaveTasks(){
        for (var config : allConfigInternal){
            if(config.file != null && config.markForSave){
                config.save(config.file);
            }
        }
    }
    private static final long SAVE_TIME = 1000 * 15;
    static{
        ScheduleService.launchAsyncRepeatTask(Config::configSaveTasks, SAVE_TIME, SAVE_TIME);
    }
    @Setter
    @Getter
    private String configName;
    //todo: rewrite system, add something like MemorySection like part of the fucking here










    //todo: add custom serializable with custom configuring widget
//todo: add custom hotkey manager with a custom config
//todo: add custom bindings to custom hotkey manager , use hotkeyEvent to trigger toggle
//todo: add toggle hotkeys to FlagRef
//todo: turn some hotkeys function to FlagRef
//todo: add CustomBindingsConfigurateScreen and CustomBindingsSelectScreen with a EDIT Button
    public static interface CustomSerializableConfig{

    }



   
    
    @Deprecated
    public static class RegistryRef<T> extends Ref<T>{
        //todo not implement yet
        @Override
        public T getValue() {
            return null;
        }

        @Override
        public void setValue(T value) {

        }

        @Override
        public void addUpdateListener(Consumer<T> updateListener) {

        }

        @Override
        public Object getAsPrimitive(){
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
    //todo : CustomRef, using JsonObject as base, use Codec to build upper object, add custom keyValue impl with custom impl
    





    private final boolean autoSave= true;
//    public Config autoSave(boolean save){
//        autoSave=save;
//        return this;
//    }
    public Config(String name,@Nonnull File file, @Nonnull Map<String,Object> fileConfig) {
        this.configName=name;
        this.logger = Logger.getLogger(SlimefunHelper.MOD_ID);
        this.file = file;
        this.fileMap = new LinkedHashMap<>(fileConfig);
        this.ref = Refs.transferConfig(fileConfig);
        this.ref.setConfigReference(this);
        allConfigInternal.add(this);
    }


    public Config(String name,@Nonnull File file) {
        this(name,(File)file, ConfigLoader.loadYamlConfig(file));
    }

    @Nonnull
    public File getFile() {
        return this.file;
    }
    public void clear() {
        Iterator var1 = this.getKeys().iterator();
        while(var1.hasNext()) {
            String key = (String)var1.next();
            this.setValue( (Object)null, key);
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


    public boolean setValue(Object value, @Nonnull String... path) {
        Ref refo = Refs.wrapInstance(value);
        boolean update = this.setValue(refo, path);
        if(update){
            markForSave();
        }
        return update;
    }
    public void setValueNoNew(Object value, @Nonnull String... path){
        setValue(value, path);
    }

    public Ref<?> get(@Nonnull String... path) {
        var re = this.ref.get(path);
        if(re != null){
            re.setConfigReference(this);
        }
        return re;
    }


    public Config defaultVal(Object defaultValue,String ...path ){
        getOrCreate(Objects.requireNonNull(Refs.wrapInstance(defaultValue)),path);
        return this;
    }

    public <T> Config validator(Predicate<T> validator, String... path){
        Ref<T> ref = (Ref<T>) get(path);
        ref.addValidator(validator);
        return this;
    }


    @NonnullDefault
    public Ref getOrCreate(Ref defaultValue, @Nonnull String... path) {
        Ref result = this.ref.getOrCreate(defaultValue, path);
        if(result != null){
            result.setConfigReference(this);
        }
        if(result == defaultValue){
            if(autoSave){
                markForSave();
            }
            return defaultValue;
        }else if(result == null){
            throw new IllegalArgumentException("create fail ref validation: " + Arrays.stream(path).toList());
        }


        return result;
    }
    private boolean markForSave = false;
    public Config markForSave() {
        markForSave = true;
        return this;
    }

    public void save(@Nonnull File file) {
        try {
            if(!file.exists()){
                createFile();
            }
            Map savedData = (Map) this.ref.getAsPrimitive();

            DumperOptions options = new DumperOptions();
            options.setIndent(2);  // 设置缩进为 2 空格
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);  // 使用块风格
            options.setPrettyFlow(true);  // 启用漂亮的流式显示
            Yaml yaml=new Yaml(options);
            yaml.dump(savedData, new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        } catch (IOException var3) {
            IOException e = var3;
            this.logger.log(Level.SEVERE, "Exception while saving a Config file", e);
        } finally {
            markForSave = false;
        }

    }



    public boolean contains(@Nonnull String... path) {
        return get(path)!=null;
    }

    @Nullable
    public StringRef getString(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof StringRef ref ){
            return ref;
        }else{

            return null;
        }
    }

    public KeyBindRef getKeyBind(String... path){
        Object node=get(path);
        if(node instanceof KeyBindRef ref ){
            return ref;
        }else{

            return null;
        }
    }

    @Override
    public boolean setValue(Ref<?> value, String... path) {
        return this.ref.setValue(value, path);
    }

    public ListRef getList(String... path){
        Object node=get(path);
        if(node instanceof ListRef ref ){
            return ref;
        }else{

            return null;
        }
    }

    public <T extends ConfigEnum> EnumRef<T> getEnum(@Nonnull String... path){
        Object node = get(path);
        if(node instanceof EnumRef<?> enumRef){
            return (EnumRef<T>) enumRef;
        }else{
            return null;
        }
    }

    public IntRef getInt(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof IntRef ref ){
            return  ref;
        }else{
            return null;
        }
    }
    public DoubleRef getDouble(String... path){
        Object node=get(path);
        if(node instanceof DoubleRef ref){
            return ref;
        }else {
            return null;
        }
    }
    public FlagRef getBoolean(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof FlagRef ref ){
            return  ref;
        }else{
            return null;
        }
    }
    public ObjectRef getObject(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof ObjectRef ref ){
            return  ref;
        }else{
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
        if(subMap instanceof MapRef mapRef){
            return mapRef.getKeys();
        }else{
            return Set.of();
        }

    }

    public void reload() {
        if(this.file!=null){
            Refs.transferConfig(ConfigLoader.loadYamlConfig(this.file))
                .copyValueTo(this.ref);


        }
    }
    public Set<String> getPaths(){
        return this.ref.getPaths();
    }
    public static String[] cutToPath(String rawPath){
        return rawPath.split("\\.");
    }
    public static Set<String> getPaths(Map<String,Object> map,String parent){
        Set<String> paths=new LinkedHashSet<>();
        for(Map.Entry<String,Object> entry:map.entrySet()){
            if(entry.getValue() instanceof Map map2){
                Set<String> p=getPaths(map2,entry.getKey());
                paths.addAll(p.stream().map(str->(String)parent+"."+str).collect(Collectors.toSet()));
            }else{
                paths.add(parent+"."+entry.getKey());
            }
        }
        return paths;
    }

    public <T> SettingBuilder<T> builder(Class<T> clazz){
        return new SettingBuilder<>(asRef(), this, clazz);
    }

    public static void registerClassSupport(Class<?> clazz){
        if(ConfigEnum.class.isAssignableFrom(clazz) && Enum.class.isAssignableFrom(clazz)){
            ConfigEnum.ensureRegistered((Class<? extends Enum>) clazz);
        }
    }

    public MapRef asRef(){
        return this.ref;
    }

    public static class SettingBuilder<T>{
        final RefMap root;
        final Config rootConfig;
        public SettingBuilder(MapRef ref, Config rootConfig, Class<T> clazz){
            this.root = ref;
            this.clazz = clazz;
            this.rootConfig = rootConfig;
            registerClassSupport(clazz);
        }
        protected final Class<T> clazz;
        protected String[] path;
        protected Ref<T> ref;
        protected T defaultValue;
        protected Ref<T> getRef(){
            if(ref == null){
                Object obj = root.get(path);
                if(obj instanceof Ref<?>){
                    ref = (Ref<T>)obj;
                }else{
                    ref = null;
                }
            }
            return ref;
        }

        public SettingBuilder<T> path(String... path){
            this.path =path;
            return this;
        }

        public SettingBuilder<T> defaultValue(T val){
            this.defaultValue = val;
            var instance = Refs.wrapInstance(val);
            ref = (Ref<T>) root.getOrCreate(instance, path);
            if(ref == instance){
                rootConfig.markForSave();
            }
            return this;
        }

        public SettingBuilder<T> validator(Predicate<T> va){
            getRef().addValidator(va);
            return this;
        }

        public SettingBuilder<T> updateListener(Consumer<T> va){
            getRef().addUpdateListenerWithUpdate(va);
            return this;
        }

        public SettingBuilder<T> registerHotkey(SimpleHotKey.InputHandler handler){
            if(ref instanceof KeyBindRef keyBindRef){
                String pathHotkey = String.join(".", this.path);
                IHotKey hotKey = SimpleInputManager.getInstance().getHotkey(pathHotkey);
                if(hotKey != null){
                    //keep track, and
                    hotKey.setInputHandler(handler);
                    ((SettingBuilder<MultiKeyBind>)this).updateListener(hotKey::setKeyCodes);
                    return this;
                }else{
                    MultiKeyBind defaultKeyBind = this.defaultValue == null ? new MultiKeyBind(""): (MultiKeyBind)this.defaultValue;
                    IHotKey hotKey1 = new SimpleHotKey(path, defaultKeyBind);
                    hotKey1.setInputHandler(handler);
                    SimpleInputManager.getInstance().registerHotKeys(hotKey1);
                    // register here
                    ((SettingBuilder<MultiKeyBind>)this).updateListener(hotKey1::setKeyCodes);
                    return this;
                }
            }else{
                throw new IllegalArgumentException("Not a hotkey");
            }
        }



        public <W extends Ref<T>> SettingBuilder<T> apply(Consumer<W> va){
            va.accept((W)Objects.requireNonNull(getRef()));
            return this;
        }

        public  <W extends Ref<T>> W build(){
            var ref1 = (W) Objects.requireNonNull(getRef());
            ref1.setConfigReference(rootConfig);
            return ref1;
        }

    }

}
