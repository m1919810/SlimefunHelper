package me.matl114.managers.config;

import com.mojang.serialization.Codec;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.AttrKeyValues;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class NBTType<T> implements WrapperFactory<NbtElement, T> {
    public NBTType(String clazz, Codec<T> codec, AttrKeyValue.CustomWidgetFactory<T> customWidgetFactory, T empty) {
        this(clazz, codec, customWidgetFactory, null, empty);
        this.stringifyFactory = createDefaultFactory(this);
    }

    public NBTType(
            Class<T> clazz,
            Codec<T> codec,
            AttrKeyValue.CustomWidgetFactory<T> customWidgetFactory,
            WrapperFactory<String, T> stringifyFactory,
            T empty) {
        this(clazz.getSimpleName().toLowerCase(Locale.ROOT), codec, customWidgetFactory, stringifyFactory, empty);
    }

    public NBTType(Class<T> clazz, Codec<T> codec, AttrKeyValue.CustomWidgetFactory<T> customWidgetFactory, T empty) {
        this(clazz.getSimpleName().toLowerCase(Locale.ROOT), codec, customWidgetFactory, empty);
    }

    final String typeName;
    final Codec<T> typeCodec;
    AttrKeyValue.CustomWidgetFactory<T> customWidgetFactory;
    WrapperFactory<String, T> stringifyFactory;
    final T empty;

    public T parse(NbtElement element) {
        return typeCodec.decode(NbtOps.INSTANCE, element).getOrThrow().getFirst();
    }

    public T createEmpty() {
        // 逆天
        return typeCodec
                .decode(
                        NbtOps.INSTANCE,
                        typeCodec.encodeStart(NbtOps.INSTANCE, empty).getOrThrow())
                .getOrThrow()
                .getFirst();
    }

    public NbtElement toNbt(T val) {
        return typeCodec.encodeStart(NbtOps.INSTANCE, val).getOrThrow();
    }

    public AttrKeyValue<T> createAttrKeyValue(String key, T value) {
        return new BaseAttrKeyValue<T>(
                key,
                value,
                customWidgetFactory,
                AttrKeyValues.NBT_FACTORY.concat(WrapperFactory.of(this::parse, this::toNbt)));
    }

    public DrawableWidget generateValueWidget(AttrKeyValue<T> value, int x, int y, int inputDx, int dy) {
        if (customWidgetFactory != null) {
            return customWidgetFactory.generateWidget(value, x, y, inputDx, dy);
        } else {
            throw new IllegalStateException("Can not find factory");
        }
    }

    @Override
    public T create(NbtElement va) {
        return parse(va);
    }

    @Override
    public NbtElement get(T va) {
        return toNbt(va);
    }

    public static <T> WrapperFactory<String, T> createDefaultFactory(NBTType<T> type) {
        return AttrKeyValues.NBT_FACTORY.concat(type);
    }

    public static <T> Class<T> parameter(Class<?> wClass) {
        return (Class<T>) wClass;
    }

    public <W> NBTType<W> cast() {
        return (NBTType<W>) this;
    }

    public String toString() {
        return "NBTType[" + typeName + "]";
    }
}
