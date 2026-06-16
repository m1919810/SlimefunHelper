package me.matl114.managers.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import me.matl114.utils.Debug;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

public interface NBTParsable<T extends NBTParsable<T>> extends AutoRegisterType {
    public static Map<String, NBTType<?>> registeredParsableTypes = new HashMap<>();

    default void registerNBTType() {
        registerNBTType(type());
    }

    public static void registerNBTType(Class<?> clazz) {
        String name = clazz.getSimpleName().toLowerCase(Locale.ROOT);
        if (!registeredParsableTypes.containsKey(name)) {}
    }

    public static void onLoad(Class<?> c) {
        if (NBTParsable.class.isAssignableFrom(c)) {
            if (!registeredParsableTypes.containsKey(c.getSimpleName().toLowerCase(Locale.ROOT))) {
                try {
                    Field fieldLookup = c.getField("TYPE");
                    Preconditions.checkArgument(NBTType.class.isAssignableFrom(fieldLookup.getType()));
                    NBTType<?> type = (NBTType<?>) fieldLookup.get(null);
                    registerNBTType(type);
                } catch (Throwable e) {
                    Debug.info("Auto register fail for type " + c.getSimpleName()
                            + ", because static NBTType TYPE field not found");
                }
            }
        }
    }

    public static void registerNBTType(NBTType<?> type) {
        if (!registeredParsableTypes.containsKey(type.typeName.toLowerCase(Locale.ROOT))) {
            registeredParsableTypes.put(type.typeName, type);
        }
    }

    default Codec<T> codec() {
        return type().typeCodec();
    }

    public NBTType<T> type();

    default T cast() {
        return (T) this;
    }

    default NbtElement toNbt() {
        return codec().encodeStart(NbtOps.INSTANCE, cast()).getOrThrow();
    }

    default String getTypeName() {
        return type().typeName;
    }

    default boolean isSameType(NBTParsable<?> type) {
        return type.getClass() == this.getClass();
    }
}
