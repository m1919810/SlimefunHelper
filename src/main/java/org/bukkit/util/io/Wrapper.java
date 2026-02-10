package org.bukkit.util.io;

import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.Map;
import me.matl114.bukkit.BukkitSerializationMock;
import me.matl114.bukkit.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * for network communication(parse server itemStack byteStream storaged in pdc)
 * @param <T>
 */
public final class Wrapper<T extends Map<String, ?> & Serializable> implements Serializable {
    private static final long serialVersionUID = -986209235411767547L;

    public final T map;

    public static Wrapper<ImmutableMap<String, ?>> newWrapper(@NotNull ConfigurationSerializable obj) {
        return new Wrapper<ImmutableMap<String, ?>>(ImmutableMap.<String, Object>builder()
                .put(BukkitSerializationMock.SERIALIZED_TYPE_KEY, BukkitSerializationMock.getAlias(obj.getClass()))
                .putAll(obj.serialize())
                .build());
    }

    private Wrapper(@NotNull T map) {
        this.map = map;
    }
}
