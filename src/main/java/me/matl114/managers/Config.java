package me.matl114.managers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Key;
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
import lombok.val;
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
    protected HashMap<String,Object> fileConfig;

    public static interface Node<T> {
        public abstract boolean isLeaf();
        public abstract Map<String, Node> getChildren();
        public abstract Ref<T> getLeaf();
        public abstract Predicate<T> getValidator();
    }


    @Getter
    private static final HashSet<Config> configs = new LinkedHashSet<>();
    private static final HashSet<Config> allConfigInternal = new LinkedHashSet<>();

    public void registerGlobal(){
        configs.add(this);
    }
    public static void reloadAll(){
        allConfigInternal.forEach(Config::reload);
    }
    @Setter
    @Getter
    private String configName;
    public static abstract class Ref<T>{
        private final List<Consumer<T>> updated=new ArrayList<>();
        private final List<Predicate<T>> validators=new ArrayList<>();
        public abstract T getValue();
        public abstract void setValue(T value);
        public void addUpdateListener(Consumer<T> updateListener){
            this.updated.add(updateListener);
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

        public abstract Object getAsPrimitive();

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
        protected T object;
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
                    this.object = ((ObjectRef<W>) otherRef).object;
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
                return this.object.toString();
            }
        }

    }

    public static class StringRef extends ObjectRef<String>{
        public StringRef(String value){
            super(value);
        }
        public Object getAsPrimitive(){
            return object;
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof StringRef stringRef){
                this.object = stringRef.object;
                return true;
            }
            return false;
        }

        @Override
        public AttrKeyValue<String> _createKeyValue0(String key) {
            return AttrKeyValue.str(key, this.object);
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
            return object.asString();
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof KeyBindRef stringRef){
                this.object = stringRef.object;
                return true;
            }
            return false;
        }

        @Override
        public AttrKeyValue<MultiKeyBind> _createKeyValue0(String key) {
            //todo
            return AttrKeyValue.keyBind(key, this.object);
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




    public static class EnumRef<T extends ConfigEnum> extends ObjectRef<T>{
        public String enumType;
        public EnumRef(ConfigEnum enumR){
            this(enumR.asString());
        }
        public EnumRef(String value){
            super(null);
            //value should be like enum:configEnumsthclaass_name:value
            String[] splite = value.split(":");
            Preconditions.checkArgument(splite.length == 3 && Objects.equals("enum", splite[0]));
            String enumType = splite[1];
            var re = ConfigEnum.registeredConfigs.get(enumType);
            Preconditions.checkNotNull(re, "Unregistered enum type %s".formatted(enumType));
            this.enumType = enumType;
            var enumValue = re.get(splite[2]);
            Preconditions.checkNotNull(enumValue, "Unregistered enum value %s in enum type %s with %s".formatted(splite[2], enumType, re.toString()));
            this.object = (T) enumValue;
        }

        @Override
        protected T validateAndCast(Object val) {
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
            return get().asString();
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof EnumRef<?> what && what.enumType == this.enumType){
                ((EnumRef<T>) otherRef).set(this.object);
                return true;

            }
            return false;
        }

        @Override
        public AttrKeyValue<T> _createKeyValue0(String key) {
            return (AttrKeyValue<T>) AttrKeyValue.enumMap(key, this.getValue(), this.getValue().getMap());
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
            return object.stream().map(Object::toString).toList();
        }

        @Override
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if(otherRef instanceof ListRef listRef){
                listRef.set(new ArrayList<>(this.object));
                return true;

            }
            return false;
        }

        @Override
        public AttrKeyValue<List<String>> _createKeyValue0(String key) {
            return AttrKeyValue.list(key, this.object);
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
        public <W> boolean copyValueTo(Ref<W> otherRef) {
            return false;
        }

        @Override
        public AttrKeyValue<T> _createKeyValue0(String key) {
            throw new UnsupportedOperationException();
        }
    }
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

    private static Ref<?> wrapInstance(Object value){
        if(value instanceof Ref<?> ref){
            return ref;
        }else{

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

    private static HashMap<String,Object> transferConfig(Map<String,Object> config){
        HashMap<String,Object> newConfig = new LinkedHashMap<>();
        for(Map.Entry<String,Object> entry : config.entrySet()){
            if(entry.getValue() instanceof Map map) {
                newConfig.put(entry.getKey(), transferConfig(map));
            }else {
                if(entry.getValue() != null){
                    Ref<?> wrapped = wrapInstance(entry.getValue());
                    if(wrapped != null){
                        newConfig.put(entry.getKey(), wrapped);
                    }
                }

            }


//            if(entry.getValue() instanceof Integer integer){
//                newConfig.put(entry.getKey(), new AtomicInteger(integer));
//            }else if(entry.getValue() instanceof Boolean bool){
//                newConfig.put(entry.getKey(), new AtomicBoolean(bool));
//            }else if(entry.getValue() instanceof Float f){
//                newConfig.put(entry.getKey(),new AtomicDouble(f));
//            }else if(entry.getValue() instanceof Double d){
//                newConfig.put(entry.getKey(),new AtomicDouble(d));
//            }
//            else if(entry.getValue() instanceof String string){
//                if("true".equals(string)){
//                    newConfig.put(entry.getKey(), new AtomicBoolean(true));
//                }else if("false".equals(string)){
//                    newConfig.put(entry.getKey(), new AtomicBoolean(false));
//                }else if(string.startsWith("enum:")){
//                    //parse enum
//                    newConfig.put(entry.getKey(), new EnumRef(string));
//                }
//                else {
//                    try{
//                        int value=Integer.parseInt(string);
//                        newConfig.put(entry.getKey(), new AtomicInteger(value));
//                    }catch (Throwable e){
//                        newConfig.put(entry.getKey(), new StringRef(string));
//                    }
//                }
//            }else{
//                if(entry.getValue()!=null){
//                newConfig.put(entry.getKey(), new AtomicReference<Object>(entry.getValue()));
//                }
//            }
        }
        return newConfig;
    }
    private static HashMap<String,Object> transferBack(HashMap<String,Object> config){
        HashMap<String,Object> newConfig = new HashMap<>();
        for(Map.Entry<String,Object> entry : config.entrySet()){
            if(entry.getValue() instanceof HashMap map){
                newConfig.put(entry.getKey(), transferBack(map));
            }else if(entry.getValue() instanceof Ref<?> ref){
                newConfig.put(entry.getKey(), ref.getAsPrimitive());
                //newConfig.put(entry.getKey(), integer.get());
            }else{
                newConfig.put(entry.getKey(), entry.getValue());
            }
        }
        return newConfig;
    }
    private static void syncTo(HashMap<String,Object> oldConfig, HashMap<String,Object> newConfig){
        for(Map.Entry<String,Object> entry : newConfig.entrySet()){
            if(oldConfig.containsKey(entry.getKey())){
                Object oldValue = oldConfig.get(entry.getKey());
                Object newValue = entry.getValue();
                if(oldValue instanceof HashMap map && newValue instanceof HashMap map2){
                    syncTo(map,map2);
                }else if(oldValue instanceof Ref<?> ref1 && newValue instanceof Ref<?> ref2){
                    if(!ref2.copyValueTo(ref1)){
                        ((Ref)ref1).setValue((Object) ref2.getValue());
                    }
                   // ref1.copyValueTo(ref2);
                }else if(oldValue instanceof Ref ref1){
                    ref1.setValue(newValue);
                }
            }else{
                oldConfig.put(entry.getKey(), entry.getValue());
            }
        }
        var iter=oldConfig.entrySet().iterator();
        while(iter.hasNext()){
            Map.Entry<String,Object> entry = iter.next();
            String key = entry.getKey();
            if(!newConfig.containsKey(key)){
                iter.remove();
            }
        }
    }




    private final boolean autoSave=false;
//    public Config autoSave(boolean save){
//        autoSave=save;
//        return this;
//    }
    public Config(String name,@Nonnull File file, @Nonnull HashMap<String,Object> fileConfig) {
        this.configName=name;
        this.logger = Logger.getLogger(SlimefunHelper.MOD_ID);
        this.file = file;
        this.fileConfig = new HashMap<String,Object>();
        syncTo(this.fileConfig, transferConfig(fileConfig));
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
            this.setValue( (Object)null,key);
        }

    }
    private boolean setValueInternal(Object node,Object value){
        if(node instanceof Ref<?> refNode){
            if(value instanceof Ref<?> refVal){
                return refVal.copyValueTo(refNode);
            }
            ((Ref)refNode).setValue(value);
            return true;
        }
        return false;
//        if(value instanceof Integer int1 && node instanceof AtomicInteger int2){
//            int2.set(int1);
//        }else if(value instanceof AtomicInteger int1 &&  node instanceof AtomicInteger int2){
//            int2.set(int1.get());
//        }else if(value instanceof Boolean int1 && node instanceof AtomicBoolean int2){
//            int2.set(int1);
//        }else if(value instanceof AtomicBoolean int1 &&  node instanceof AtomicBoolean int2){
//            int2.set(int1.get());
//        }else if(value instanceof Double int1&& node instanceof AtomicDouble int2){
//            int2.set(int1);
//        }else if(value instanceof Float int1 &&  node instanceof AtomicDouble int2){
//            int2.set(int1);
//        }
//        else if(value instanceof AtomicDouble int1&& node instanceof AtomicDouble int2){
//            int2.set(int1.get());
//        }
//        else if(value instanceof String int1 && node instanceof StringRef int2){
//            int2.setValue(int1);
//        }else if(value instanceof StringRef int1 &&  node instanceof StringRef int2){
//            int2.setValue(int1.getValue());
//        }else if(value instanceof ConfigEnum enum1 && node instanceof EnumRef enumRef){
//            enumRef.setValue(enum1);
//        }else if(value instanceof EnumRef ref1 && node instanceof EnumRef ref2){
//            ref2.setValue(ref1.getValue());
//        }
//        else if(value instanceof AtomicReference int1 && node instanceof AtomicReference int2){
//            int2.set(int1.get());
//        }else if(node instanceof AtomicReference in2){
//            in2.set(value);
//        }else {
//            return false;
//        }
//        return true;
    }
//    private Object wrapperInstance(Object val){
//        if(val instanceof Boolean b){
//            return new AtomicBoolean(b);
//        }else if(val instanceof Integer i){
//            return new AtomicInteger(i);
//
//        }else if(val instanceof Double d){
//            return new AtomicDouble(d);
//        }else if(val instanceof Float d){
//            return new AtomicDouble(d);
//        }
//        else if(val instanceof String i){
//            return new StringRef(i);
//        }else if(val instanceof ConfigEnum configEnum){
//            return new EnumRef<>(configEnum);
//        }
//        else if(val.getClass().getSimpleName().startsWith("Atomic") || Ref.class.isAssignableFrom( val.getClass())){
//            return val;
//        }else {
//            return new AtomicReference<>(val);
//        }
//    }


    public void setValue(Object value, @Nonnull String... path) {
        Object node=this.fileConfig;
        HashMap parent=null;
        for(int i=0;i<path.length;i++){
            String p=path[i];
            if(node instanceof HashMap map){
                if(map.containsKey(p)){
                    parent=map;
                    node = map.get(p);
                    continue;
                }
            }else{
                node=new HashMap<>();
            }
            parent=(HashMap)node;
            node=(i==path.length-1)?null: new HashMap<>();
            parent.put(p,node);
        }
        if(node==null){
            parent.put(path[path.length-1],value);
        }else {
            if(!setValueInternal(node,value)){
                parent.put(path[path.length-1], wrapInstance( value));
            }
        }
        if(autoSave){
            save();
        }
    }
    public void setValueNoNew(Object value, @Nonnull String... path){
        Object node=this.fileConfig;
        HashMap parent=null;
        for(int i=0;i<path.length;i++){
            String p=path[i];
            if(node instanceof HashMap map){
                if(map.containsKey(p)){
                    parent=map;
                    node = map.get(p);
                    continue;
                }
            }else{
                node=new HashMap<>();
            }
            parent=(HashMap)node;
            node=(i==path.length-1)?null: new HashMap<>();
            parent.put(p,node);
        }
        if(node==null){
            parent.put(path[path.length-1],value);
        }else {
            setValueInternal(node,value);
        }
        if(autoSave){
            save();
        }
    }

    public Object get(@Nonnull String... path) {
        Object node=this.fileConfig;
        for(String p:path){
            if(node instanceof HashMap map){
                if(map.containsKey(p)){
                    node = map.get(p);
                    continue;
                }
            }
            return null;
        }
        return node;
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
    public Object getOrCreate(Ref defaultValue, @Nonnull String... path) {
        Object node=this.fileConfig;
        boolean changed=false;
        for(int i=0; i< path.length ;i++){
            String p=path[i];
            if(i == path.length - 1){
                var next = ((HashMap)node).get(p);
                //of course it is not a leaf
                if(next == null || next instanceof HashMap notALeaf ){
                    var newLeaf = defaultValue;
                    ((HashMap) node).put(p, newLeaf);
                    node = newLeaf;
                    changed=true;
                }else{
                    //fix: Ref should be same class as default value
                    if(next.getClass() != defaultValue.getClass()){
                        next = defaultValue;
                        ((HashMap) node).put(p, next);
                    }
                    node = next;
                }
            }else{
                var next = ((HashMap)node).get(p);
                if(next instanceof HashMap map0){
                    node = map0;
                }else{
                    var newMap = new LinkedHashMap<>();
                    ((HashMap)node).put(p, newMap);
                    node = newMap;
                    changed=true;
                }
            }


        }
        if(changed){
            save();
        }
        return node;
    }
    public Config save() {
        if(this.file!=null){

            this.save(this.file);
        }
        return this;
    }

    public void save(@Nonnull File file) {
        try {
            if(!file.exists()){
                createFile();
            }
            DumperOptions options = new DumperOptions();
            options.setIndent(2);  // 设置缩进为 2 空格
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);  // 使用块风格
            options.setPrettyFlow(true);  // 启用漂亮的流式显示
            Yaml yaml=new Yaml(options);
            HashMap savedData=transferBack(this.fileConfig);
            yaml.dump(savedData, new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        } catch (IOException var3) {
            IOException e = var3;
            this.logger.log(Level.SEVERE, "Exception while saving a Config file", e);
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
        return this.fileConfig.keySet();
    }

    @Nonnull
    public Set<String> getKeys(@Nonnull String... path) {
        Object node = this.fileConfig;

        for(String p:path){
            if(node instanceof HashMap map){
                if(map.containsKey(p)){
                    node = map.get(p);
                    continue;
                }
            }
            return Set.of();
        }
        if(node instanceof HashMap map){
            return map.keySet();
        }else return Set.of();
    }

    public void reload() {
        if(this.file!=null){

                //重载新配置文件
            syncTo(this.fileConfig,transferConfig(ConfigLoader.loadYamlConfig(this.file)));

        }
    }
    public HashSet<String> getPaths(){
        HashSet<String> paths=new LinkedHashSet<>();
        for(Map.Entry<String,Object> entry:this.fileConfig.entrySet()){
            if(entry.getValue() instanceof HashMap map2){
                HashSet<String> p=getPaths(map2,entry.getKey());
                paths.addAll(p);
            }else{
                paths.add(entry.getKey());
            }
        }
        return paths;
    }
    public static String[] cutToPath(String rawPath){
        return rawPath.split("\\.");
    }
    public static HashSet<String> getPaths(HashMap<String,Object> map,String parent){
        HashSet<String> paths=new LinkedHashSet<>();
        for(Map.Entry<String,Object> entry:map.entrySet()){
            if(entry.getValue() instanceof HashMap map2){
                HashSet<String> p=getPaths(map2,entry.getKey());
                paths.addAll(p.stream().map(str->(String)parent+"."+str).collect(Collectors.toSet()));
            }else{
                paths.add(parent+"."+entry.getKey());
            }
        }
        return paths;
    }
}
