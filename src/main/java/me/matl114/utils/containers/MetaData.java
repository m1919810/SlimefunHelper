package me.matl114.utils.impl.containers;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class MetaData {
    Map<Object, Map<String, Object>> referenceMap = new WeakHashMap<>();
    public <W> void put(W val ,String key, Object value) {
        referenceMap.computeIfAbsent(val, (s)-> new ConcurrentHashMap<>()).put(key, value);
    }

    public <W,T> T get(W val, String key) {
        var re = referenceMap.get(val);
        if(re != null) {
            return (T)re.get(key);
        }else {
            return null;
        }
    }


}
