package me.matl114.managers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.matl114.SlimefunHelper;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.AttrKeyValue;
import me.matl114.utils.UtilClass.Displayable;
import net.minecraft.util.StringIdentifiable;
import org.lwjgl.system.NonnullDefault;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class
Config {
    private final File file;
    private Logger logger;
    private String header;
    //fixme: add schema and node structure
    protected Map<String,Object> fileMap;
    protected MapRef ref;
    private static Map<String,Object> transferBack(MapRef config){
        LinkedHashMap<String,Object> newConfig = new LinkedHashMap<>();
        for(Map.Entry<String,Ref<?>> entry : config.getValue().entrySet()){
            //MapRef recursive call this method for recursive transfer
            newConfig.put(entry.getKey(), entry.getValue().getAsPrimitive());
        }
        return newConfig;
    }

    private static Ref<?> wrapInstance(Object value){
        if(value == null){
            return null;
        }
        if(value instanceof Ref<?> ref){
            return ref;
        } else if(value instanceof Map map){
            return transferConfig(map);
        }
        else{

            //Enum should be written
            for (var typedBuilder : referenceBuilders){
                Ref<?> ref = typedBuilder.tryBuild(value);
                if(ref != null){
                    return ref;
                }
            }
            return null;
        }
    }

    private static MapRef transferConfig(Map<String,Object> config){
        MapRef newConfig = new MapRef();
        for(Map.Entry<String,Object> entry : config.entrySet()){
            if(entry.getValue() != null){
                Ref<?> wrapped = wrapInstance(entry.getValue());
                if(wrapped != null){
                    newConfig.putRaw(entry.getKey(), wrapped);
                }
            }
        }
        return newConfig;
    }

    private static void syncTo(Map<String,Ref<?>> oldConfig, Map<String,Ref<?>> newConfig){
        for(Map.Entry<String,Ref<?>> entry : newConfig.entrySet()){
            if(oldConfig.containsKey(entry.getKey())){
                Ref<?> oldValue = oldConfig.get(entry.getKey());
                Ref<?> newValue = entry.getValue();
                //syncTo is called recursively by MapRef.copyValueTo
                if(!newValue.copyValueTo(oldValue)){
                    //value ref  not compate, remove the old and put the new
                    oldConfig.remove(entry.getKey());
                    oldConfig.put(entry.getKey(), newValue);
                }

            }else{
                oldConfig.put(entry.getKey(), entry.getValue());
            }
        }
        var iter= oldConfig.entrySet().iterator();
        while(iter.hasNext()){
            Map.Entry<String, Ref<?>> entry = iter.next();
            String key = entry.getKey();
            if(!newConfig.containsKey(key)){
                iter.remove();
            }
        }
    }


    public static interface RefMap{
        public Ref<?> get(String... key);

        public <T> Ref<T> getOrCreate(Ref<T> val, String... obj);

        public IntRef getInt(String... path);

        public FlagRef getBoolean(String... path);

        public DoubleRef getDouble(String... path);

        public <T extends ConfigEnum> EnumRef<T> getEnum(String... path);

        public StringRef getString(String... path);

        public ListRef getList(String... path);

        public KeyBindRef getKeyBind(String... path);

        boolean setValue(Ref<?> value, String... path);
    }


    public static class MapRef extends Ref<Map<String, Ref<?>>> implements RefMap{
        Map<String, Ref<?>> map = new LinkedHashMap<>();
        boolean section = false;


        public void markAsSection(){
            section = true;
        }

        public void removeSection(){
            section = false;
        }

        @Override
        public Map<String, Ref<?>> getValue() {
            return map;
        }

        private void setValueRecursively(Map<String, Ref<?>> map0){
            syncTo(map, map0);
        }

        public void putRaw(String str, Ref ref){
            map.put(str, ref);
        }

        @Override
        public void setValue(Map<String, Ref<?>> value) {
            //todo: copy recursively
            if(validateUpdateValue(value)){
                setValueRecursively(value);
                callUpdate();
            }
        }

        public boolean setValueNoCopy(Map<String, Ref<?>> value){
            if(validateUpdateValue(value)){
                this.map = value;
                callUpdate();
                return true;
            }
            return false;
        }

        @Override
        public Object getAsPrimitive() {
            return transferBack(this);
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof MapRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof MapRef map){
                map.setValue(this.map);
                return true;
            }
            return false;
        }

        @Override
        protected AttrKeyValue<Map<String, Ref<?>>> _createKeyValue0(String key) {
            throw new IllegalStateException("Not impl yet");
        }
        @Override
        public boolean setValue(Ref<?> value, String... path){
            return setValue0(value, path, 0);
        }

        private boolean setValue0(Ref<?> value, String[] path, int index){
            if(path.length <= index){
                return false;
            }
            else if(path.length -1 == index){

                if(value != null){
                    if(map.get(path[index]) instanceof Ref<?> ref){
                        //check if it is the same save format
                        if (Objects.equals(ref.getAsPrimitive(), value.getAsPrimitive())) {
                            //if equals do not set
                            return false;
                        }
                        var map0 = new LinkedHashMap<>(map);

                        if(!value.copyValueTo(ref)){
                            //
                            map0.put(path[index], value);
                        }
                        return setValueNoCopy(map0);
                    }else{
                        var map0 = new LinkedHashMap<>(map);

                        //null
                        map0.put(path[index], value);
                        return setValueNoCopy(map0);
                    }
                }else{

                    boolean contains = map.containsKey(path[index]);
                    if(contains){
                        var map0 = new LinkedHashMap<>(map);
                        map0.remove(path[index]);
                        return setValueNoCopy(map0);
                    }
                    return false;
                }
            }else{
                if(map.containsKey(path[index]) && map.get(path[index]) instanceof MapRef subMap){
                    return subMap.setValue0(value, path, index + 1);
                }else{
                    MapRef mapRef2 = new MapRef();
                    var map0 = new LinkedHashMap<>(map);
                    map0.put(path[index], mapRef2);
                    mapRef2.setValue0(value, path, index + 1);
                    return setValueNoCopy(map0);
                }
            }
        }

        public Ref<?> get(@Nonnull String... path){
            return get(path, 0);
        }

        public Ref<?> get(@Nonnull String[] path, int index){
            if(path.length <= index){
                return null;
            }else if(path.length -1 == index){
                return map.get(path[index]);
            }else {
                if(map.get(path[index]) instanceof MapRef mapRef){
                    return mapRef.get(path, index + 1);
                }else {
                    return null;
                }
            }
        }

        public <T> Ref<T> getOrCreate(Ref<T> ref, String... path){
            return (Ref<T>) getOrCreate(ref, path, 0);
        }

        public Ref<?> getOrCreate(Ref<?> ref, String[] path, int index){
            if(path.length <= index){
                throw new UnsupportedOperationException("path length = 0");
            } else if(path.length -1 == index){
                var ref0 = map.get(path[index]);
                if(ref0 != null){
                    return ref0;
                }
                var map0 = new LinkedHashMap<>(map);
                map0.put(path[index], ref);
                if(setValueNoCopy(map0)){
                    return ref;
                }
                return null;
            }else {
                if(map.get(path[index]) instanceof MapRef mapRef){
                    return mapRef.getOrCreate(ref, path, index + 1);
                }else {
                    var map0 = new LinkedHashMap<>(map);
                    var mapRef = new MapRef();
                    map0.put(path[index], mapRef);
                    if( mapRef.setValue0(ref, path, index + 1) && setValueNoCopy(map0)){
                        return ref;
                    }else{
                        return null;
                    }
                }
            }
        }

        public Set<String> getKeys(){
            return map.keySet();
        }

        public Set<String> getPaths(){
            return getPaths("");
        }

        public Set<String> getPaths(String prefix){
            Set<String> set = new LinkedHashSet<>();
            walkPaths(set, prefix);
            return set;
        }
        // prefix should contains the dot
        public void walkPaths(Set<String> str, String prefix){
            if(section){
                str.add(prefix);
            }else{
                for(Map.Entry<String, Ref<?>> entry : map.entrySet()){
                    String nextP = prefix  + entry.getKey();
                    if(entry.getValue() instanceof MapRef map){
                        map.walkPaths(str, nextP + ".");
                    }else{
                        str.add(nextP);
                    }
                }
            }
        }

        public boolean containsPath(String[] path){
            return get(path) != null;
        }

        @Override
        public IntRef getInt(String... path) {
            return null;
        }

        @Override
        public FlagRef getBoolean(String... path) {
            return null;
        }

        @Override
        public DoubleRef getDouble(String... path) {
            return null;
        }

        @Override
        public <T extends ConfigEnum> EnumRef<T> getEnum(String... path) {
            return null;
        }

        @Override
        public StringRef getString(String... path) {
            return null;
        }

        @Override
        public ListRef getList(String... path) {
            return null;
        }

        @Override
        public KeyBindRef getKeyBind(String... path) {
            return null;
        }


    }

    @Getter
    private static final Set<Config> configs = new LinkedHashSet<>();
    private static final Set<Config> allConfigInternal = new LinkedHashSet<>();

    public void registerGlobal(){
        configs.add(this);
    }
    public static void reloadAll(){
        allConfigInternal.forEach(Config::reload);
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
    public static abstract class Ref<T>{
        private final List<Consumer<T>> updated=new ArrayList<>();
        private final List<Predicate<T>> validators=new ArrayList<>();
        public abstract T getValue();
        public abstract void setValue(T value);
        public void addUpdateListener(Consumer<T> updateListener){
            if(updateListener == null)return;
            this.updated.add(updateListener);
        }

        public void addUpdateListenerWithUpdate(Consumer<T> updateListener){
            if(updateListener == null)return;
            this.updated.add(updateListener);
            try{
                updateListener.accept(getValue());
            }catch (Throwable e){

            }
        }

        public void removeUpdateListener(Predicate<Consumer<T>> removeListener){
            this.updated.removeIf(removeListener);
        }

        public void addValidator(Predicate<T> validator){
            this.validators.add(validator);
        }
        public boolean validateUpdateValue(T val){
            try{
                for(Predicate<T> updateListener : validators){
                    if(!updateListener.test(val)){
                        return false;
                    }
                }
                return true;
            }catch (Throwable e){
                return false;
            }
        }
        public void callUpdate(){
            T val = getValue();
            try{
                updated.forEach(i -> i.accept(val));
            }catch (Throwable e){
            }
        }
        // this method return object that is acceptable for yaml save and load
        public abstract Object getAsPrimitive();
        // isSameTypeWith should return the same value as copyValueTo, but they don't do copy
        public abstract <W> boolean isSameTypeWith(Ref<W> ref);
        //copy this value to the argument
        public abstract <W> boolean copyValueTo(Ref<W> otherRef);

        public final AttrKeyValue<T> createKeyValue(String key){
            AttrKeyValue<T> keyValue = _createKeyValue0(key);
            keyValue.getValidators().addAll(validators);
            return keyValue;
        }

        protected abstract AttrKeyValue<T> _createKeyValue0(String key);
    }
    @AllArgsConstructor
    public static class FlagRef extends Ref<Boolean>{
        boolean flag;
        public FlagRef(Object newFlag){
            this((boolean)newFlag);
        }

        public static FlagRef fromString(String value){
            if("true".equals(value)){
                return new FlagRef(true);
            }else if("false".equals(value)){
                return new FlagRef(false);
            }else return null;
        }

        @Nonnull
        @Override
        public Boolean getValue() {
            return get();
        }

        public boolean get(){
            return flag;
        }

        @Override
        public void setValue(Boolean value) {
            set(value);
        }

        @Override
        public Object getAsPrimitive() {
            return flag? "true": "false";
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof FlagRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof FlagRef flagRef){
                flagRef.set(this.flag);
                return true;
            }else {
                return false;
            }
        }

        @Override
        public AttrKeyValue<Boolean> _createKeyValue0(String key) {
            return AttrKeyValue.bool(key, this.flag);
        }

        public void set(boolean val){
            if(validateUpdateValue(val)){
                this.flag = val;
                callUpdate();
            }
        }
    }
    @AllArgsConstructor
    public static class IntRef extends Ref<Integer>{
        int value;
        public IntRef(Object ref){
            this((int) ref);
        }

        public static IntRef fromString(String value){
            try{
                int val = Integer.parseInt(value);
                return new IntRef(val);
            }catch (NumberFormatException numberFormatException){
                return null;
            }
        }

        @Override
        public Integer getValue() {
            return get();
        }

        public int get(){
            return value;
        }

        @Override
        public void setValue(Integer value) {
            set(value);
        }

        @Override
        public Object getAsPrimitive() {
            return this.value;
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof IntRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof IntRef integer){
                integer.set(this.value);
                return true;
            }else return false;
        }

        @Override
        public AttrKeyValue<Integer> _createKeyValue0(String key) {
            return AttrKeyValue.integer(key, this.value);
        }

        public void set(int value){
            if(validateUpdateValue(value)){
                this.value = value;
                callUpdate();
            }

        }
    }
    @AllArgsConstructor
    public static class DoubleRef extends Ref<Double>{
        double value;

        public static DoubleRef of(Object va){
            return new DoubleRef(((Number)va).doubleValue());
        }

        public DoubleRef(Double doubleValue){
            this(doubleValue.doubleValue());
        }
        public DoubleRef(Float floatValue){
            this(floatValue.doubleValue());
        }
        @Override
        public Double getValue() {
            return get();
        }

        public double get(){
            return value;
        }

        @Override
        public void setValue(Double value) {
            set(value);
        }

        @Override
        public Object getAsPrimitive() {
            return value;
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof DoubleRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof DoubleRef ref){
                ref.set(this.value);
                return true;
            }else return false;
        }

        @Override
        public AttrKeyValue<Double> _createKeyValue0(String key) {
            return AttrKeyValue.doub(key, this.value);
        }

        public void set(double va){
            if(validateUpdateValue(va)){
                this.value = va;
                callUpdate();
            }
        }
    }
    @AllArgsConstructor
    public static abstract class ObjectRef<T> extends Ref<T>{
        private T object;
        @Override
        public final T getValue() {
            return get();
        }

        public T get(){
            return object;
        }

        @Override
        public final void setValue(T value) {
            set(value);
        }

        @Override
        public abstract Object getAsPrimitive();

        @Override
        public abstract  <W> boolean copyValueTo(Ref<W> otherRef);
        @Override
        public abstract AttrKeyValue<T> _createKeyValue0(String key);

        protected abstract T validateAndCast(Object val);

        public void set(T val){
            T cas = validateAndCast(val);
            if(validateUpdateValue(cas)){
                this.object = cas;
                callUpdate();
            }

        }

        public static class JustOnlyObjectRef extends ObjectRef<Object>{

            public JustOnlyObjectRef(Object object) {
                super(object);
            }

            public  <W> boolean copyValueTo(Ref<W> otherRef) {
                if(otherRef.getClass() == JustOnlyObjectRef.class){
                    ((JustOnlyObjectRef) otherRef).set(this.get());
                    return true;
                }else {
                    return false;
                }
            }

            @Override
            public AttrKeyValue<Object> _createKeyValue0(String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected Object validateAndCast(Object val) {
                return val;
            }

            public Object getAsPrimitive(){
                return this.get().toString();
            }

            @Override
            public <W> boolean isSameTypeWith(Ref<W> ref) {
                return ref.getClass() == JustOnlyObjectRef.class;
            }
        }

    }

    public static class StringRef extends ObjectRef<String>{
        public StringRef(String value){
            super(value);
        }
        public Object getAsPrimitive(){
            return get();
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof StringRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof StringRef stringRef){
                stringRef.set(this.get());
                return true;
            }
            return false;
        }

        @Override
        public AttrKeyValue<String> _createKeyValue0(String key) {
            return AttrKeyValue.str(key, this.get());
        }

        @Override
        protected String validateAndCast(Object val) {
            return (String) val;
        }
    }

    public static class KeyBindRef extends ObjectRef<MultiKeyBind>{

        public KeyBindRef(MultiKeyBind object) {
            super(object);
        }

        @Override
        public Object getAsPrimitive() {
            return get().asString();
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof KeyBindRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof KeyBindRef stringRef){
                stringRef.set(this.get());
                return true;
            }
            return false;
        }

        @Override
        public AttrKeyValue<MultiKeyBind> _createKeyValue0(String key) {
            //todo
            return AttrKeyValue.keyBind(key, this.get());
        }

        @Override
        protected MultiKeyBind validateAndCast(Object val) {
            return (MultiKeyBind) val;
        }

        public static KeyBindRef fromString(String val){
            if(val.startsWith("hotkey:")){
                try{
                    return new KeyBindRef(new MultiKeyBind(val));
                }catch (Throwable e){
                }
            }
            return null;
        }
    }

    public static interface ConfigEnum extends StringIdentifiable, Displayable {
        public static Map<String, Map<String, ConfigEnum>> registeredConfigs = new HashMap<>();
        static void register(Class<? extends Enum> configEnum){
            Map<String, ConfigEnum> maps = new LinkedHashMap<>();
            for (var e  : configEnum.getEnumConstants()){
                maps.put(e.name(), (ConfigEnum) e);
            }
            registeredConfigs.put(configEnum.getSimpleName().toLowerCase(Locale.ROOT), maps);
        }
        static void ensureRegistered(Class<? extends Enum> configEnum){
            if(!registeredConfigs.containsKey(configEnum.getSimpleName().toLowerCase(Locale.ROOT))){
                register(configEnum);
            }
        }
//        public Text getDisplay();
        default Enum cast(){
            return (Enum) this;
        }
        default String getConfigEnumType(){
            return this.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }
        default String asString(){
            return "enum:" + getConfigEnumType() + ":" + cast().name();
        }
        default Map<String, ConfigEnum> getMap(){
            return registeredConfigs.get(this.getConfigEnumType());
        }
    }
    //todo: add custom serializable with custom configuring widget
    //todo: add custom hotkey manager with a custom config
    //todo: add custom bindings to custom hotkey manager , use hotkeyEvent to trigger toggle
    //todo: add toggle hotkeys to FlagRef
    //todo: turn some hotkeys function to FlagRef
    //todo: add CustomBindingsConfigurateScreen and CustomBindingsSelectScreen with a EDIT Button
    public static interface CustomSerializableConfig{

    }



    public static class EnumRef<T extends ConfigEnum> extends ObjectRef<T>{
        public final String enumType;
        public String enumValue;
        public boolean resolved;
        public EnumRef(ConfigEnum enumR){
            super((T) enumR);
            ConfigEnum.ensureRegistered(enumR.cast().getClass());

            this.enumType = enumR.getConfigEnumType();
            this.enumValue = enumR.cast().name();
            this.resolved = true;
        }
        public EnumRef(String value){
            super(null);
            //value should be like enum:configEnumsthclaass_name:value
            String[] splite = value.split(":");
            Preconditions.checkArgument(splite.length == 3 && Objects.equals("enum", splite[0]));
            //todo:
            this.enumType = splite[1];
            this.enumValue = splite[2];
            tryResolve();
        }

        private void tryResolve(){
            if(this.resolved)return;
            var re = ConfigEnum.registeredConfigs.get(enumType);
            if(re == null) {
                this.resolved = false;
                return;
            }
            var val = re.get(enumValue);
            Preconditions.checkNotNull(val, "Unregistered enum value %s in enum type %s with %s".formatted(enumValue, enumType, re.toString()));
            this.resolved = true;
            this.set((T) val);
        }

        public void setEnumType(Class<? extends Enum> clazz){
            if(!Objects.equals(enumType, clazz.getSimpleName().toLowerCase(Locale.ROOT))){
                throw new IllegalArgumentException("Enum type mismatch the class name: " +enumType + " and " + clazz);
            }
            if(!resolved){
                ConfigEnum.ensureRegistered(clazz);
                tryResolve();
            }

        }

        @Override
        public void set(T val) {
            super.set(val);
            enumValue = this.get().cast().name();
        }

        @Override
        protected T validateAndCast(Object val) {
            if(!resolved){
                setEnumType((Class<? extends Enum>) val.getClass());
            }
            T configEnum = (T) val;

            Preconditions.checkArgument(Objects.equals(enumType, configEnum.getConfigEnumType()), "Enum type mismatch !");
            return configEnum;
        }

        public static EnumRef<ConfigEnum> fromString(String value){
            if(value.startsWith("enum:")){
                try{
                    return new EnumRef<>(value);
                }catch (Throwable e){
                    Debug.info("Parse config as Enum Selection failed: ", value, ", Error Message: ", e.getMessage());
                }
            }
            return null;
        }


        public Object getAsPrimitive(){
            return "enum:" + enumType +":" + enumValue;
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof EnumRef what && Objects.equals(what.enumType, enumType);
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof EnumRef<?> what && Objects.equals(what.enumType, this.enumType)){
                if(!this.resolved){
                    tryResolve();
                }
                if(this.resolved){
                    ((EnumRef<T>) otherRef).set(this.get());
                }else{
                    ((EnumRef<T>) otherRef).enumValue = this.enumValue;
                }

                return true;

            }
            return false;
        }

        @Override
        public T get() {
            if(resolved){
                return super.get();
            }else{
                tryResolve();
                T val = super.get();
                if(val != null){
                    return val;
                }else{
                    throw new IllegalStateException("Access to a config enum instance before it is registered");
                }
            }
        }

        @Override
        public AttrKeyValue<T> _createKeyValue0(String key) {
            if(resolved){
                return (AttrKeyValue<T>) AttrKeyValue.enumMap(key, this.getValue(), this.getValue().getMap());
            }else{
                return AttrKeyValue.enumMap(key, null, Map.of());
            }
        }
    }
    //TODO: RegistryRef
    //TODO: RegistrySetRef
    //TODO: ListRef

    public static class ListRef extends ObjectRef<List<String>>{
        @Getter
        public List<Predicate<String>> elementValidator = new ArrayList<>();

        public ListRef(List<String> object) {
            super(object);
            addValidator(this::validateInternal);
        }
        public boolean validateElement(String val){
            for(Predicate<String> validator : elementValidator){
                if(!validator.test(val)){
                    return false;
                }
            }
            return true;
        }
        private boolean validateInternal(List<String> v){
            return v.stream().allMatch(this::validateElement);
        }

        @Override
        public Object getAsPrimitive() {
            return get().stream().map(Object::toString).toList();
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref instanceof ListRef;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof ListRef listRef){
                listRef.set(new ArrayList<>(this.get()));
                return true;

            }
            return false;
        }

        @Override
        public AttrKeyValue<List<String>> _createKeyValue0(String key) {
            return AttrKeyValue.list(key, this.get());
        }

        @Override
        protected List<String> validateAndCast(Object val) {
            return (List<String>) val;
        }
    }

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
    private static final List<TypedReferenceBuilder<?>> referenceBuilders ;
    @AllArgsConstructor
    public static class TypedReferenceBuilder<T> {
        public Class<T> baseClass;
        public List<Function<T, Ref<?>>> builders;
        public Ref<?> tryBuild(Object value){
            if(baseClass.isAssignableFrom(value.getClass())){
                //is instance
                T val = (T) value;
                Ref<?> ref = null;
                for (var builder: builders){
                    if((ref = builder.apply(val)) != null){
                        break;
                    }
                }
                return ref;
            }
            return null;
        }
    }

    static{
        referenceBuilders = ImmutableList.<TypedReferenceBuilder<?>>builder()
            .add(new TypedReferenceBuilder<>(Boolean.class, List.of(FlagRef::new)))
            .add(new TypedReferenceBuilder<>(Integer.class, List.of(IntRef::new)))
            .add(new TypedReferenceBuilder<>(Float.class, List.of(DoubleRef::of)))
            .add(new TypedReferenceBuilder<>(Double.class, List.of(DoubleRef::of)))
            .add(new TypedReferenceBuilder<>(ConfigEnum.class, List.of(EnumRef::new)))
            .add(new TypedReferenceBuilder<>(MultiKeyBind.class, List.of(KeyBindRef::new)))
            .add(new TypedReferenceBuilder<>(List.class, List.of(ListRef::new)))
            .add(new TypedReferenceBuilder<>(
                String.class,
                ImmutableList.<Function<String, Ref<?>>>builder()
                    .add(EnumRef::fromString)
                    .add(KeyBindRef::fromString)
                    .add(FlagRef::fromString)
                    .add(IntRef::fromString)
                    .add(StringRef::new)
                    .build())
            )
            //TODO: add List
            .add(new TypedReferenceBuilder<Object>(Object.class, List.of(
                ObjectRef.JustOnlyObjectRef::new
            )))
            .build();

    }





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
        this.ref = transferConfig(fileConfig);
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
        Ref refo = wrapInstance(value);
        boolean update = this.ref.setValue(refo, path);
        if(update){
            save();
        }
        return update;
    }
    public void setValueNoNew(Object value, @Nonnull String... path){
        setValue(value, path);
    }

    public Ref<?> get(@Nonnull String... path) {
        return this.ref.get(path);
    }


    public Config defaultVal(Object defaultValue,String ...path ){
        getOrCreate(Objects.requireNonNull(wrapInstance(defaultValue)),path);
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
        if(result == defaultValue){
            if(autoSave){
                save();
            }
            return defaultValue;
        }else if(result == null){
            throw new IllegalArgumentException("create fail ref validation: " + Arrays.stream(path).toList());
        }


        return result;
    }
    private boolean markForSave = false;
    public Config save() {
        markForSave = true;
        return this;
    }

    public void save(@Nonnull File file) {
        try {
            if(!file.exists()){
                createFile();
            }
            Map savedData=transferBack(this.ref);

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
            transferConfig(ConfigLoader.loadYamlConfig(this.file))
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
        final Class<T> clazz;
        String[] path;
        Config.Ref<T> ref;
        private Config.Ref<T> getRef(){
            if(ref == null){
                Object obj = root.get(path);
                if(obj instanceof Ref<?>){
                    ref = (Config.Ref<T>)obj;
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
            var instance = wrapInstance(val);
            ref = (Ref<T>) root.getOrCreate(instance, path);
            if(ref == instance){
                rootConfig.save();
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



        public <W extends Config.Ref<T>> SettingBuilder<T> apply(Consumer<W> va){
            va.accept((W)Objects.requireNonNull(getRef()));
            return this;
        }

        public  <W extends Config.Ref<T>> W build(){
            return (W) Objects.requireNonNull(getRef());
        }

    }

}
