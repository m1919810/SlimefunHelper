package me.matl114.BukkitUtiils;

import me.matl114.SlimefunUtils.Debug;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.io.BukkitObjectInputStream;


import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;

public class BukkitObjectInputStreamAccess extends BukkitObjectInputStream {
    protected BukkitObjectInputStreamAccess() throws IOException, SecurityException {
        super();
    }
    public BukkitObjectInputStreamAccess(InputStream in) throws IOException {
        super(in);
    }
    protected Object resolveObject(Object obj) throws IOException {
        Object a=null;
        try{
            Debug.info(obj);
            Debug.info(obj.getClass());
            Field map=obj.getClass().getDeclaredField("map");
            map.setAccessible(true);
            a= map.get(obj);
            Debug.info(a);
            Debug.info(a.getClass());
        }catch (Throwable e){
            Debug.info("no such field map");
            return super.resolveObject(obj);
        }
        try{
            Debug.info("get wrapper map");
            Debug.info(a);
            Debug.info(a.getClass());
            (obj = ConfigurationSerialization.deserializeObject((Map)a)).getClass();
            Debug.info("deserialized object");
        }catch (Throwable e){
            e.printStackTrace();
        }
        Debug.info("super.resolveObject");
        return super.resolveObject(obj);
    }
}
