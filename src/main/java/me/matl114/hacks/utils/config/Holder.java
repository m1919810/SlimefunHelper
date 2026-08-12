package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.RegistryAttrKeyValue;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public record Holder<T>(Registry<T> registry, @Nullable T entry, String asString) implements NBTParsable<Holder<T>> {
    public static <W> Class<Holder<W>> parameter() {
        return (Class) Holder.class;
    }

    public static final Identifier DEFAULT_EMPTY = new Identifier("minecraft", "default");
    public static final String DEFAULT_EMPTY_STRING = DEFAULT_EMPTY.toString();
    public static final String SPLITTER = "|";

    @Override
    public NBTType<Holder<T>> type() {
        return TYPE.cast();
    }

    public static <T> Holder<T> of(Registry<T> registry, T entry) {
        Identifier string = ((Registry<Registry>) (Registry) Registries.REGISTRIES).getId(registry);
        if (entry == null) {
            return new Holder<>(registry, null, string + SPLITTER + DEFAULT_EMPTY_STRING);
        } else {
            Identifier entryIdentifier = registry.getId(entry);
            if (entryIdentifier != null) {
                return new Holder<>(registry, entry, string + SPLITTER + entryIdentifier.toString());
            } else {
                throw new IllegalArgumentException("Unregistered Entry: " + entry);
            }
        }
    }

    public static <T> DataResult<Holder<T>> parse(String s) {
        String[] split = s.split("\\|");
        if (split.length == 2) {
            Identifier identifier = Identifier.tryParse(split[0]);
            if (identifier != null) {
                Registry<T> registry = (Registry<T>) Registries.REGISTRIES.get(identifier);
                if (registry != null) {
                    Identifier entryIdentifier = Identifier.tryParse(split[1]);
                    if (entryIdentifier != null) {
                        if (Objects.equals(entryIdentifier, DEFAULT_EMPTY)) {
                            return DataResult.success(new Holder<>(registry, null, s));
                        } else {
                            T val = registry.get(entryIdentifier);
                            if (val != null) {
                                return DataResult.success(new Holder<>(registry, val, s));
                            } else {
                                return DataResult.error(() -> "Can not find registry entry for " + entryIdentifier);
                            }
                        }
                    } else {
                        return DataResult.error(() -> "Invalid format");
                    }
                } else {
                    return DataResult.error(() -> "Can not find registry for " + identifier);
                }
            } else {
                return DataResult.error(() -> "Invalid format");
            }
        } else {
            return DataResult.error(() -> "Invalid format");
        }
    }

    private static <T> NBTType<Holder<T>> create() {
        return new NBTType<Holder<T>>(
                "holder",
                Codec.STRING.comapFlatMap(Holder::parse, Holder::asString),
                (w, x, y, dx, dy) -> {
                    Holder<T> holder = w.getOriginValue();
                    Registry<T> registry = holder.registry;
                    WrapperFactory<Optional<T>, Holder<T>> wrapperFactory = WrapperFactory.of(
                            s -> {
                                return Holder.of(registry, s.orElse(null));
                            },
                            t -> Optional.ofNullable(t.entry()));
                    WrapperFactory<String, T> stringifyFactory = RegistryAttrKeyValue.stringifyFactory(registry);
                    WrapperFactory<String, Optional<T>> stringifyFactoryWithDefault = WrapperFactory.of(
                            s -> {
                                if (Objects.equals(s, DEFAULT_EMPTY_STRING)) {
                                    return Optional.empty();
                                } else {
                                    return Optional.of(stringifyFactory.create(s));
                                }
                            },
                            t -> t.map(stringifyFactory::get).orElse(DEFAULT_EMPTY_STRING));
                    AttrKeyValue.CustomWidgetFactory<Optional<T>> widgetFactory = (s, x1, y1, dx1, dy1) -> {
                        return RegistryAttrKeyValue.generateTextInputWithRegistrySearch(registry, s, x1, y1, dx1, dy1);
                    };
                    return new TypeConvertAttrKeyValue<>(w, wrapperFactory, widgetFactory, stringifyFactoryWithDefault)
                            .generateValueWidget(x, y, dx, dy);
                },
                WrapperFactory.of(s -> Holder.<T>parse(s).getOrThrow(), Holder::asString),
                Holder.of((Registry<T>) Registries.ITEM, null));
    }

    public static final NBTType<Holder<Object>> TYPE = Holder.create();

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof Holder<?> holder
                && holder.registry == ((Holder<?>) holder).registry;
    }
}
