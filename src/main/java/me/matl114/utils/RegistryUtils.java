package me.matl114.utils;

import static net.minecraft.registry.RegistryKeys.*;

import com.google.common.collect.ImmutableMap;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

public class RegistryUtils {
    public static <T> Set<T> parseWhiteList(Registry<T> registry, Pattern regex) {
        Set<T> newBlocks = new LinkedHashSet<>();
        try {
            Predicate<String> predicate = regex.asMatchPredicate();
            for (var blockIds : registry.getIds()) {
                if (predicate.test(blockIds.getPath())) {
                    newBlocks.add(registry.get(blockIds));
                }
            }
        } catch (Throwable e) {

        }
        return newBlocks;
    }

    public static <T> Set<T> parseWhiteList(Registry<T> registry, String regex) {
        return parseWhiteList(registry, Pattern.compile(regex));
    }

    public static <T> Set<RegistryEntry<T>> parseEntryWhiteList(Registry<T> registry, String regex) {
        Set<RegistryEntry<T>> newBlocks = new LinkedHashSet<>();
        try {
            Predicate<String> predicate = Pattern.compile(regex).asMatchPredicate();
            for (var blockIds : registry.getIds()) {
                if (predicate.test(blockIds.getPath())) {
                    RegistryEntry<T> reg = registry.getEntry(blockIds).orElse(null);
                    if (reg != null) {
                        newBlocks.add(reg);
                    }
                }
            }
        } catch (Throwable e) {

        }
        return newBlocks;
    }

    private static Class<?> fromType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        } else if (type instanceof TypeVariable<?> tv) {
            // 理论上不会出现，但可回退到第一个上界
            return fromType(tv.getBounds()[0]);
        } else if (type instanceof WildcardType wt) {
            // 取上界（extends）或下界（super），一般取第一个上界
            return fromType(wt.getUpperBounds()[0]);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    public static final Map<RegistryKey<?>, Class<?>> REGISTRY_KEY_TO_ICON;

    public static <T> Class<T> getRegistryType(Registry<T> registry) {
        return getRegistryType((RegistryKey<Registry<T>>) registry.getKey());
    }

    public static <T> Class<T> getRegistryType(RegistryKey<Registry<T>> key) {
        return (Class<T>) REGISTRY_KEY_TO_ICON.get(key);
    }

    static {
        Map<RegistryKey<?>, Class<?>> clazzMap = new HashMap<>();
        try {
            for (Field field : RegistryKeys.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) continue;

                Type genericType = field.getGenericType();
                // 必须是 ParameterizedType 且 raw type 为 RegistryKey
                if (!(genericType instanceof ParameterizedType keyType)) continue;
                if (keyType.getRawType() != RegistryKey.class) continue;

                Type[] keyArgs = keyType.getActualTypeArguments();
                if (keyArgs.length != 1) continue;
                Type registryKeyArg = keyArgs[0];

                // 期望这个参数是 Registry<X> 类型
                if (!(registryKeyArg instanceof ParameterizedType registryType)) continue;
                if (registryType.getRawType() != Registry.class) continue;

                // 取出 Registry<X> 中的 X
                Type[] registryArgs = registryType.getActualTypeArguments();
                if (registryArgs.length != 1) continue;
                Type elementType = registryArgs[0];
                Class<?> clazz = fromType(elementType);
                clazzMap.put((RegistryKey<?>) field.get(null), clazz);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        clazzMap.put(ITEM, Item.class);
        clazzMap.put(BLOCK, Block.class);
        clazzMap.put(ATTRIBUTE, EntityAttribute.class);
        clazzMap.put(ENCHANTMENT, Enchantment.class);
        clazzMap.put(BLOCK_ENTITY_TYPE, BlockEntityType.class);
        clazzMap.put(ENTITY_TYPE, EntityType.class);
        clazzMap.put(STATUS_EFFECT, StatusEffect.class);
        REGISTRY_KEY_TO_ICON = ImmutableMap.copyOf(clazzMap);
    }
}
