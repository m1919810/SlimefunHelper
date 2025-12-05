package me.matl114.bukkitUtiils;

import com.google.common.base.Preconditions;
import me.matl114.utils.Debug;



import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public class BukkitSerializationMock {
    public static BukkitItemFactory ITEM_FACTORY_INSTANCE=new BukkitItemFactory();
    public static void init(){
        try{

            Debug.info(BukkitConfigDeserializor.deserializeItemFromStringTest(BukkitConfigDeserializor.TEST_CASE));

        }catch (Throwable e){
            throw new RuntimeException(e);
        }
        try{
            Debug.info(ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(ConfigurationSerializableDataType.TEST_CASE));
        }catch (Throwable e){
            throw new RuntimeException(e);
        }
    }
    public static void initTest(){

    }

    public static BukkitItemFactory getItemFactory() {
        return ITEM_FACTORY_INSTANCE;
    }

    public static final String SERIALIZED_TYPE_KEY = "==";
    private final Class<? extends ConfigurationSerializable> clazz;
    private static Map<String, Class<? extends ConfigurationSerializable>> aliases = new HashMap();
    static {
        registerClass(BukkitMetaItem.class,"ItemMeta");
        registerClass(BukkitMetaItem.class,"org.bukkit.craftbukkit.inventory.CraftMetaItem");
        registerClass(BukkitItemStack.class,"ItemStack");
        registerClass(BukkitItemStack.class,"org.bukkit.inventory.ItemStack");
        registerClass(BukkitOfflineplayer.class,"OfflinePlayer");
        registerClass(BukkitPlayerProfile.class,"PlayerProfile");
    }


    protected BukkitSerializationMock(@NotNull Class<? extends ConfigurationSerializable> clazz) {
        this.clazz = clazz;
    }

    @Nullable
    protected Method getMethod(@NotNull String name, boolean isStatic) {
        try {
            Method method = this.clazz.getDeclaredMethod(name, Map.class);
            if (!ConfigurationSerializable.class.isAssignableFrom(method.getReturnType())) {
                return null;
            } else {
                return Modifier.isStatic(method.getModifiers()) != isStatic ? null : method;
            }
        } catch (NoSuchMethodException var4) {
            return null;
        } catch (SecurityException var5) {
            return null;
        }
    }

    @Nullable
    protected Constructor<? extends ConfigurationSerializable> getConstructor() {
        try {
            return this.clazz.getConstructor(Map.class);
        } catch (NoSuchMethodException var2) {
            return null;
        } catch (SecurityException var3) {
            return null;
        }
    }

    @Nullable
    protected ConfigurationSerializable deserializeViaMethod(@NotNull Method method, @NotNull Map<String, ?> args) {
        try {
            ConfigurationSerializable result = (ConfigurationSerializable)method.invoke((Object)null, args);
            if (result != null) {
                return result;
            }

            Debug.info( "Could not call method '" + method.toString() + "' of " + this.clazz + " for deserialization: method returned null");
        } catch (Throwable var4) {
            Throwable ex = var4;
            Debug.info( "Could not call method '" + method.toString() + "' of " + this.clazz + " for deserialization", ex instanceof InvocationTargetException ? ex.getCause() : ex);
        }

        return null;
    }

    @Nullable
    protected ConfigurationSerializable deserializeViaCtor(@NotNull Constructor<? extends ConfigurationSerializable> ctor, @NotNull Map<String, ?> args) {
        try {
            return (ConfigurationSerializable)ctor.newInstance(args);
        } catch (Throwable var4) {
            Throwable ex = var4;
            Debug.info( "Could not call constructor '" + ctor.toString() + "' of " + this.clazz + " for deserialization", ex instanceof InvocationTargetException ? ex.getCause() : ex);
            return null;
        }
    }

    @Nullable
    public ConfigurationSerializable deserialize(@NotNull Map<String, ?> args) {
        Preconditions.checkArgument(args != null, "Args must not be null");
        ConfigurationSerializable result = null;
        Method method = null;
        if (result == null) {
            method = this.getMethod("deserialize", true);
            if (method != null) {
                result = this.deserializeViaMethod(method, args);
            }
        }

        if (result == null) {
            method = this.getMethod("valueOf", true);
            if (method != null) {
                result = this.deserializeViaMethod(method, args);
            }
        }

        if (result == null) {
            Constructor<? extends ConfigurationSerializable> constructor = this.getConstructor();
            if (constructor != null) {
                result = this.deserializeViaCtor(constructor, args);
            }
        }

        return result;
    }

    @Nullable
    public static ConfigurationSerializable deserializeObject(@NotNull Map<String, ?> args, @NotNull Class<? extends ConfigurationSerializable> clazz) {
        return (new BukkitSerializationMock(clazz)).deserialize(args);
    }
    public static final String UNKNOWN_SERIALIZATION_TYPE = "unknown-serialization-type";
    @Nullable
    public static Object deserializeObject(@NotNull Map<String, ?> args) {
        Class<? extends ConfigurationSerializable> clazz = null;
        if (args.containsKey("==")) {
            try {
                String alias = (String)args.get("==");
                if (alias == null) {
                    throw new IllegalArgumentException("Cannot have null alias");
                }

                clazz = getClassByAlias(alias);
                if (clazz == null) {
                    //do not throw Exception
                    //throw new IllegalArgumentException("Specified class does not exist ('" + alias + "')");

                    return new UnknownSerialization(args);
                }
            } catch (ClassCastException var3) {
                ClassCastException ex = var3;
                ex.fillInStackTrace();
                throw ex;
            }

            return (new BukkitSerializationMock(clazz)).deserialize(args);
        } else {
            throw new IllegalArgumentException("Args doesn't contain type key ('==')");
        }
    }
    public static class UnknownSerialization{
        public String type;
        public Map<String, ?> value ;
        public UnknownSerialization(Map value){
            this.value = new LinkedHashMap<>(value);
            type = (String) value.get("==");
            this.value.remove("==");
        }
    }

    public static void registerClass(@NotNull Class<? extends ConfigurationSerializable> clazz) {
        registerClass(clazz, getAlias(clazz));
        registerClass(clazz, clazz.getName());
    }

    public static void registerClass(@NotNull Class<? extends ConfigurationSerializable> clazz, @NotNull String alias) {
        aliases.put(alias, clazz);
    }

    public static void unregisterClass(@NotNull String alias) {
        aliases.remove(alias);
    }

    public static void unregisterClass(@NotNull Class<? extends ConfigurationSerializable> clazz) {
        while(aliases.values().remove(clazz)) {
        }

    }

    @Nullable
    public static Class<? extends ConfigurationSerializable> getClassByAlias(@NotNull String alias) {
        return (Class)aliases.get(alias);
    }

    @NotNull
    public static String getAlias(@NotNull Class<? extends ConfigurationSerializable> clazz) {
        return clazz.getName();
    }
}
