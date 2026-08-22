package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import lombok.With;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.RegistryAttrKeyValue;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

@With
public record WeakHolder<T>(Identifier registry, Identifier location) implements NBTParsable<WeakHolder<T>> {
    public static final Identifier DEFAULT_KEY = new Identifier("minecraft", "default");

    public static <W> Class<WeakHolder<W>> parameter() {
        return (Class) WeakHolder.class;
    }

    public WeakHolder(RegistryKey<T> registryKey) {
        this(registryKey.getRegistry(), registryKey.getValue());
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static NBTType<WeakHolder> TYPE = new NBTType<>(
            "weakholder",
            Codec.STRING.comapFlatMap(WeakHolder::parse, WeakHolder::asString),
            (w, x, y, dx, dy) -> {
                Identifier registry = w.getOriginValue().registry();
                var handler = mc.getNetworkHandler();
                Optional<Registry<Object>> optionalLookup;
                if (handler != null && handler.getRegistryManager() != null) {
                    var registryLookup = handler.getRegistryManager();
                    optionalLookup = registryLookup.getOptional(RegistryKey.ofRegistry(registry));
                } else {
                    optionalLookup = Optional.empty();
                }
                var wrapper = new TypeConvertAttrKeyValue<>(
                        w,
                        WrapperFactory.of(s -> new WeakHolder<>(registry, s), WeakHolder::location),
                        NBTTypes.IDENTIFIER_TYPE);
                if (optionalLookup.isPresent()) {
                    Registry<Object> lookupValue = optionalLookup.get();
                    return RegistryAttrKeyValue.generateTextInputWithRegistrySearch(lookupValue, wrapper, x, y, dx, dy);
                } else {
                    return wrapper.generateValueWidget(x, y, dx, dy);
                }
            },
            new WeakHolder(Enchantments.AQUA_AFFINITY));

    public static <T> DataResult<WeakHolder<T>> parse(String s) {
        String[] split = s.split("\\|");
        if (split.length == 2) {
            Identifier identifier = Identifier.tryParse(split[0]);
            Identifier location = Identifier.tryParse(split[1]);
            if (identifier != null && location != null) {
                return DataResult.success(new WeakHolder<>(identifier, location));
            } else {
                return DataResult.error(() -> "Invalid format");
            }
        } else {
            return DataResult.error(() -> "Invalid format");
        }
    }

    public String asString() {
        return registry + "|" + location;
    }

    @Override
    public NBTType<WeakHolder<T>> type() {
        return TYPE.cast();
    }

    public RegistryKey<T> toRegistryKey() {
        return RegistryKey.of(RegistryKey.ofRegistry(registry), location);
    }

    public Optional<RegistryEntry<T>> getEntry() {
        RegistryKey<T> key = toRegistryKey();
        return ItemStackUtils.registry()
                .get(toRegistryKey().getRegistryRef())
                .getEntry(key)
                .map(s -> s);
    }
}
