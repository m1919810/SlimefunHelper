package me.matl114.versioned.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import me.matl114.versioned.impl.ItemUtils_v1_21_11;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.Nullable;

public interface VItem {
    public static final VItem INSTANCE = new ItemUtils_v1_21_11();

    public static VItem getInstance() {
        return INSTANCE;
    }

    public boolean canGlide(ItemStack stack);

    public boolean isSpear(ItemStack stack);

    public boolean isWeapon(ItemStack stack);

    public boolean isTool(ItemStack stack);

    public boolean isNotAttackingTool(ItemStack stack);

    public boolean isShield(ItemStack stack);

    public boolean isAxe(ItemStack stack);

    public boolean isEatable(ItemStack stack);

    public ItemStack fromNbt(NbtCompound tag);
    // now we save DataVersion field
    public NbtCompound toNbt(ItemStack tag);

    public CustomModelDataComponent createModelData(int cmd);

    public Map<ComponentType<?>, Codec<?>> getVersionCompatCodecs();

    default Codec<ItemStack> getVersionedCodec() {
        return ITEM_STACK_CODEC;
    }

    public static record ComponentChangesType(@Nullable ComponentType<?> type, boolean removed) {
        public static final Codec<ComponentChangesType> CODEC;

        @Nonnull
        public Codec<?> getValueCodec() {
            if (type == null) return Codec.EMPTY.codec();
            if (removed) return Codec.EMPTY.codec();
            else {
                var versioned = VItem.getInstance().getVersionCompatCodecs().get(type);
                return versioned == null ? type.getCodecOrThrow() : versioned;
            }
        }

        static {
            CODEC = Codec.STRING.flatXmap(
                    (id) -> {
                        boolean bl = id.startsWith("!");
                        if (bl) {
                            id = id.substring("!".length());
                        }

                        Identifier identifier = Identifier.tryParse(id);
                        ComponentType<?> componentType = Registries.DATA_COMPONENT_TYPE.get(identifier);
                        if (componentType == null) {
                            // for version compat
                            return DataResult.success(new ComponentChangesType(null, false));
                        } else {
                            return componentType.shouldSkipSerialization()
                                    ? DataResult.error(() -> {
                                        return "'" + String.valueOf(identifier) + "' is not a persistent component";
                                    })
                                    : DataResult.success(new ComponentChangesType(componentType, bl));
                        }
                    },
                    (type) -> {
                        ComponentType<?> componentType = type.type();
                        if (componentType == null) {
                            return DataResult.error(() -> "Null component type");
                        }
                        Identifier identifier = Registries.DATA_COMPONENT_TYPE.getId(componentType);
                        return identifier == null
                                ? DataResult.error(() -> {
                                    return "Unregistered component: " + String.valueOf(componentType);
                                })
                                : DataResult.success(
                                        type.removed() ? "!" + String.valueOf(identifier) : identifier.toString());
                    });
        }
    }

    Codec<ComponentChanges> COMPONENT_CHANGES_CODEC = Codec.dispatchedMap(
                    ComponentChangesType.CODEC, ComponentChangesType::getValueCodec)
            .xmap(
                    (changes) -> {
                        if (changes.isEmpty()) {
                            return ComponentChanges.EMPTY;
                        } else {
                            Reference2ObjectMap<ComponentType<?>, Optional<?>> reference2ObjectMap =
                                    new Reference2ObjectArrayMap<>(changes.size());
                            var var2 = changes.entrySet().iterator();

                            while (var2.hasNext()) {
                                Map.Entry<ComponentChangesType, ?> entry = var2.next();
                                ComponentChangesType type = entry.getKey();
                                // should be only used here
                                if (type.type() != null) {
                                    if (type.removed()) {
                                        reference2ObjectMap.put(type.type(), Optional.empty());
                                    } else {
                                        reference2ObjectMap.put(type.type(), Optional.of(entry.getValue()));
                                    }
                                }
                            }

                            return new ComponentChanges(reference2ObjectMap);
                        }
                    },
                    (changes) -> {
                        Reference2ObjectMap<ComponentChangesType, ?> reference2ObjectMap =
                                new Reference2ObjectArrayMap<>(changes.size());
                        var var2 = changes.entrySet().iterator();

                        while (var2.hasNext()) {
                            Map.Entry<ComponentType<?>, Optional<?>> entry = var2.next();
                            ComponentType<?> componentType = entry.getKey();
                            if (!componentType.shouldSkipSerialization()) {
                                Optional<?> optional = entry.getValue();
                                if (optional.isPresent()) {
                                    ((Map) reference2ObjectMap)
                                            .put(new ComponentChangesType(componentType, false), optional.get());
                                } else {
                                    ((Map) reference2ObjectMap)
                                            .put(new ComponentChangesType(componentType, true), Unit.INSTANCE);
                                }
                            }
                        }

                        return (Map) reference2ObjectMap;
                    });

    MapCodec<ItemStack> ITEM_STACK_MAP_CODEC = MapCodec.recursive("ItemStack", (codec) -> {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(
                            Item.ENTRY_CODEC.fieldOf("id").forGetter(ItemStack::getRegistryEntry),
                            Codec.INT.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                            VItem.COMPONENT_CHANGES_CODEC
                                    .optionalFieldOf("components", ComponentChanges.EMPTY)
                                    .forGetter((stack) -> {
                                        return stack.components.getChanges();
                                    }))
                    .apply(instance, ItemStack::new);
        });
    });

    Codec<ItemStack> ITEM_STACK_CODEC = Codec.lazyInitialized(ITEM_STACK_MAP_CODEC::codec);
}
