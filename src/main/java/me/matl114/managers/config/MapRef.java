package me.matl114.managers.config;

import me.matl114.utils.config.AttrKeyValue;

import javax.annotation.Nonnull;
import java.util.*;

public class MapRef extends Ref<Map<String, Ref<?>>> implements RefMap {
    public static final Class<Map<String, Ref<?>>> TYPE =  (Class)Map.class;
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

    private static Map<String,Object> transferBack(MapRef config){
        LinkedHashMap<String,Object> newConfig = new LinkedHashMap<>();
        for(Map.Entry<String,Ref<?>> entry : config.getValue().entrySet()){
            //MapRef recursive call this method for recursive transfer
            newConfig.put(entry.getKey(), entry.getValue().getAsPrimitive());
        }
        return newConfig;
    }

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

    public Ref<?> getOrCreate(@Nonnull Ref<?> ref, String[] path, int index){
        if(path.length <= index){
            throw new UnsupportedOperationException("path length = 0");
        } else if(path.length -1 == index){
            var ref0 = map.get(path[index]);
            if(ref0 != null && ref.isSameTypeWith(ref0)){
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
                if(mapRef.setValue0(ref, path, index + 1) && setValueNoCopy(map0)){
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
