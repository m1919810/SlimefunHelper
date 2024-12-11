package me.matl114.ManageUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.Hash;
import lombok.Getter;
import lombok.Setter;
import me.matl114.SlimefunHelper;
import org.lwjgl.system.NonnullDefault;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class Config {
    private final File file;
    private Logger logger;
    private String header;
    protected HashMap<String,Object> fileConfig;
    @Setter
    @Getter
    private static HashSet<Config> configs = new HashSet<>();
    public static void reloadAll(){
        configs.forEach(Config::reload);
    }
    @Setter
    @Getter
    private String configName;
    public static class StringRef{
        private String value;
        public StringRef(String value){
            this.value = value;
        }
        public String get(){
            return value;
        }
        public void set(String value){
            this.value = value;
        }
        public String toString(){
            return value;
        }
    }
    private static HashMap<String,Object> transferConfig(HashMap<String,Object> config){
        HashMap<String,Object> newConfig = new HashMap<>();
        for(Map.Entry<String,Object> entry : config.entrySet()){
            if(entry.getValue() instanceof HashMap map) {
                newConfig.put(entry.getKey(), transferConfig(map));
            }else if(entry.getValue() instanceof Integer integer){
                newConfig.put(entry.getKey(), new AtomicInteger(integer));
            }else if(entry.getValue() instanceof Boolean bool){
                newConfig.put(entry.getKey(), new AtomicBoolean(bool));
            }else if(entry.getValue() instanceof String string){
                if("true".equals(string)){
                    newConfig.put(entry.getKey(), new AtomicBoolean(true));
                }else if("false".equals(string)){
                    newConfig.put(entry.getKey(), new AtomicBoolean(false));
                }else {
                    try{
                        int value=Integer.parseInt(string);
                        newConfig.put(entry.getKey(), new AtomicInteger(value));
                    }catch (Throwable e){
                        newConfig.put(entry.getKey(), new StringRef(string));
                    }
                }
            }else{
                if(entry.getValue()!=null){
                newConfig.put(entry.getKey(), new AtomicReference<Object>(entry.getValue()));
                }
            }
        }
        return newConfig;
    }
    private static HashMap<String,Object> transferBack(HashMap<String,Object> config){
        HashMap<String,Object> newConfig = new HashMap<>();
        for(Map.Entry<String,Object> entry : config.entrySet()){
            if(entry.getValue() instanceof HashMap map){
                newConfig.put(entry.getKey(), transferBack(map));
            }else if(entry.getValue() instanceof AtomicInteger integer){
                newConfig.put(entry.getKey(), integer.get());
            }else if(entry.getValue() instanceof AtomicBoolean bool){
                newConfig.put(entry.getKey(),  bool.get());
            }else if(entry.getValue() instanceof StringRef string){
                newConfig.put(entry.getKey(), string.get());
            }else if(entry.getValue() instanceof AtomicReference obj){
                newConfig.put(entry.getKey(), obj.get());
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
                }else if(oldValue instanceof AtomicInteger int1&& newValue instanceof AtomicInteger int2){
                    int1.set(int2.get());
                }else if(oldValue instanceof AtomicBoolean bool1&& newValue instanceof AtomicBoolean bool2){
                    bool1.set(bool2.get());
                }else if(oldValue instanceof StringRef r1 && newValue instanceof StringRef r2){
                    r1.set(r2.get());
                }else if(oldValue instanceof AtomicReference int1&& newValue instanceof AtomicReference int2){
                    int1.set(int2.get());
                }else{
                    oldConfig.put(entry.getKey(), newValue);
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
    public static Object saveFrom(String string){
        if("true".equals(string)){
            return true;
        }else if("false".equals(string)){
            return false;
        }else {
            try{
                int value=Integer.parseInt(string);
                return value;
            }catch (Throwable e){
                return string;
            }
        }
    }
    public static String toString(Object obj){
        return obj == null ? "null" : obj.toString();
    }


    private boolean autoSave=false;
    public Config autoSave(boolean save){
        autoSave=save;
        return this;
    }
    public Config(String name,@Nonnull File file, @Nonnull HashMap<String,Object> fileConfig) {
        this.configName=name;
        this.logger = Logger.getLogger(SlimefunHelper.MOD_ID);
        this.file = file;
        this.fileConfig = new HashMap<String,Object>();
        syncTo(this.fileConfig,transferConfig(fileConfig));
        configs.add(this);
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
        if(value instanceof Integer int1 && node instanceof AtomicInteger int2){
            int2.set(int1);
        }else if(value instanceof AtomicInteger int1 &&  node instanceof AtomicInteger int2){
            int2.set(int1.get());
        }else if(value instanceof Boolean int1 && node instanceof AtomicBoolean int2){
            int2.set(int1);
        }else if(value instanceof AtomicBoolean int1 &&  node instanceof AtomicBoolean int2){
            int2.set(int1.get());
        }
        else if(value instanceof String int1 && node instanceof StringRef int2){
            int2.set(int1);
        }else if(value instanceof StringRef int1 &&  node instanceof StringRef int2){
            int2.set(int1.get());
        }else if(value instanceof AtomicReference int1 && node instanceof AtomicReference int2){
            int2.set(int1.get());
        }else if(node instanceof AtomicReference in2){
            in2.set(value);
        }else {
            return false;
        }
        return true;
    }
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
                parent.put(path[path.length-1],value);
            }
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
    private Object wrapper(Object val){
        if(val instanceof Boolean b){
            return new AtomicBoolean(b);
        }else if(val instanceof Integer i){
            return new AtomicInteger(i);

        }else if(val instanceof String i){
            return new StringRef(i);
        }else if(val.getClass().getSimpleName().startsWith("Atomic")||val.getClass()==StringRef.class){
            return val;
        }else {
            return new AtomicReference<>(val);
        }
    }

    public Config defaultVal(Object defaultValue,String ...path ){
        getOrCreate(()->wrapper(defaultValue),path);
        return this;
    }
    @NonnullDefault
    public Object getOrCreate(Supplier<Object> defaultValue, @Nonnull String... path) {
        Object node=this.fileConfig;
        HashMap parent=null;
        boolean changed=false;
        for(int i=0;i<path.length;i++){
            String p=path[i];
            if(node instanceof HashMap map){
                if(map.get(p)!=null){
                    parent=map;
                    node = map.get(p);

                    continue;
                }
            }else{
                node=new HashMap<>();
            }
            parent=(HashMap)node;
            node=(i==path.length-1)?defaultValue.get(): new HashMap<>();
            parent.put(p,node);

            changed=true;
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
            yaml.dump(savedData,new FileWriter(file));
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

    public AtomicInteger getInt(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof AtomicInteger ref ){
            return  ref;
        }else{
            return null;
        }
    }

    public AtomicBoolean getBoolean(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof AtomicBoolean ref ){
            return  ref;
        }else{
            return null;
        }
    }
    public AtomicReference getObject(@Nonnull String... path) {
        Object node=get(path);
        if(node instanceof AtomicReference ref ){
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
        HashSet<String> paths=new HashSet<>();
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
        HashSet<String> paths=new HashSet<>();
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
