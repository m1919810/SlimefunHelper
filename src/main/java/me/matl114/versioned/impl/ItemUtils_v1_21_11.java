package me.matl114.versioned.impl;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.DataVersion;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.JsonHelper;

public class ItemUtils_v1_21_11 implements VItem {
    @Override
    public boolean canGlide(ItemStack stack) {
        return stack.contains(DataComponentTypes.GLIDER);
    }

    @Override
    public boolean isSpear(ItemStack stack) {
        if (stack.contains(DataComponentTypes.KINETIC_WEAPON)) {
            return true;
        } else {
            // 1.21.11 Netherite Spear
            Item item = stack.getItem();
            if (item.getRegistryEntry().isIn(ItemTags.SWORDS)) {
                Text name = stack.getCustomName();
                if (name != null) {
                    String str = name.getString();
                    if (str.contains("1.21.11") && str.contains("Spear")) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        return stack.contains(DataComponentTypes.WEAPON);
    }

    @Override
    public boolean isTool(ItemStack stack) {
        return stack.contains(DataComponentTypes.TOOL);
    }

    @Override
    public boolean isNotAttackingTool(ItemStack stack) {
        if (isTool(stack)) {
            if (stack.contains(DataComponentTypes.WEAPON)) {
                var weapon = stack.get(DataComponentTypes.WEAPON);
                if (weapon.itemDamagePerAttack() > 1) {
                    // only axe
                    return !stack.getItem().getRegistryEntry().isIn(ItemTags.AXES);
                    // return !stack.getItem().toString().contains("_axe");
                }
                return false;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean isShield(ItemStack stack) {
        return stack.contains(DataComponentTypes.BLOCKS_ATTACKS);
    }

    @Override
    public boolean isAxe(ItemStack stack) {
        return stack.getItem().getRegistryEntry().isIn(ItemTags.AXES);
    }

    @Override
    public boolean isEatable(ItemStack stack) {
        return stack.contains(DataComponentTypes.CONSUMABLE);
    }

    @Override
    public ItemStack fromNbt(NbtCompound tag) {
        return tag.isEmpty()
                ? ItemStack.EMPTY
                : ItemStack.CODEC
                        .decode(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), tag)
                        .getOrThrow()
                        .getFirst();
    }

    @Override
    public NbtCompound toNbt(ItemStack tag) {
        NbtCompound tagCompound = toNbt0(tag);
        tagCompound.putInt(DataVersion.DATA_VERSION_FLAG, DataVersion.getDataVersion());
        return tagCompound;
    }

    private NbtCompound toNbt0(ItemStack tag) {
        return tag.isEmpty()
                ? new NbtCompound()
                : (NbtCompound) ItemStack.CODEC
                        .encodeStart(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), tag)
                        .getOrThrow();
    }

    @Override
    public CustomModelDataComponent createModelData(int cmd) {
        return new CustomModelDataComponent(List.of((float) cmd), List.of(), List.of(), List.of());
    }

    private final Map<ComponentType<?>, Codec<?>> versionCompatCodecs;
    private final Codec<Text> TEXT_CODEC;

    public static Codec<Text> codec(int maxSerializedLength) {
        final Codec<String> codec = Codec.string(0, maxSerializedLength);
        return new Codec<Text>() {
            public <T> DataResult<Pair<Text, T>> decode(DynamicOps<T> ops, T input) {
                DynamicOps<JsonElement> dynamicOps = toJsonOps(ops);
                return codec.decode(ops, input).flatMap((pair) -> {
                    try {
                        JsonElement jsonElement = JsonParser.parseString((String) pair.getFirst());
                        return TextCodecs.CODEC.parse(dynamicOps, jsonElement).map((text) -> {
                            return Pair.of(text, pair.getSecond());
                        });
                    } catch (JsonParseException var3) {
                        JsonParseException jsonParseException = var3;
                        Objects.requireNonNull(jsonParseException);
                        return DataResult.error(jsonParseException::getMessage);
                    }
                });
            }

            public <T> DataResult<T> encode(Text text, DynamicOps<T> dynamicOps, T object) {
                DynamicOps<JsonElement> dynamicOps2 = toJsonOps(dynamicOps);
                return TextCodecs.CODEC.encodeStart(dynamicOps2, text).flatMap((json) -> {
                    try {
                        return codec.encodeStart(dynamicOps, JsonHelper.toSortedString(json));
                    } catch (IllegalArgumentException var4) {
                        IllegalArgumentException illegalArgumentException = var4;
                        Objects.requireNonNull(illegalArgumentException);
                        return DataResult.error(illegalArgumentException::getMessage);
                    }
                });
            }

            private static <T> DynamicOps<JsonElement> toJsonOps(DynamicOps<T> ops) {
                if (ops instanceof RegistryOps<T> registryOps) {
                    return registryOps.withDelegate(JsonOps.INSTANCE);
                } else {
                    return JsonOps.INSTANCE;
                }
            }
        };
    }

    {
        Codec<Text> STRINGIFY_CODEC = codec(Integer.MAX_VALUE);

        TEXT_CODEC = Codec.of(TextCodecs.CODEC, Codec.withAlternative(STRINGIFY_CODEC, TextCodecs.CODEC));
    }

    {
        var builder = ImmutableMap.<ComponentType<?>, Codec<?>>builder();
        builder.put(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                Codec.withAlternative(
                        CustomModelDataComponent.CODEC,
                        Codec.INT.xmap(
                                i -> new CustomModelDataComponent(List.of((float) i), List.of(), List.of(), List.of()),
                                v -> v.floats().stream()
                                        .findFirst()
                                        .map(Number::intValue)
                                        .orElse(0))));
        builder.put(DataComponentTypes.CUSTOM_NAME, TEXT_CODEC);
        builder.put(DataComponentTypes.ITEM_NAME, TEXT_CODEC);
        builder.put(
                DataComponentTypes.LORE,
                TEXT_CODEC.sizeLimitedListOf(256).xmap(LoreComponent::new, LoreComponent::lines));
        builder.put(
                DataComponentTypes.ENCHANTMENTS,
                Codec.withAlternative(
                        ItemEnchantmentsComponent.CODEC,
                        ItemEnchantmentsComponent.CODEC.fieldOf("levels").codec()));
        builder.put(
                DataComponentTypes.STORED_ENCHANTMENTS,
                Codec.withAlternative(
                        ItemEnchantmentsComponent.CODEC,
                        ItemEnchantmentsComponent.CODEC.fieldOf("levels").codec()));
        builder.put(
                DataComponentTypes.DYED_COLOR,
                Codec.withAlternative(
                        DyedColorComponent.CODEC,
                        DyedColorComponent.CODEC.fieldOf("rgb").codec()));
        builder.put(
                DataComponentTypes.CAN_BREAK,
                Codec.withAlternative(
                        BlockPredicatesComponent.CODEC,
                        BlockPredicatesComponent.CODEC.fieldOf("predicates").codec()));
        builder.put(
                DataComponentTypes.CAN_PLACE_ON,
                Codec.withAlternative(
                        BlockPredicatesComponent.CODEC,
                        BlockPredicatesComponent.CODEC.fieldOf("predicates").codec()));
        var attributeCodec = AttributeModifiersComponent.Entry.CODEC
                .listOf()
                .xmap(AttributeModifiersComponent::new, AttributeModifiersComponent::modifiers);
        builder.put(
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                Codec.withAlternative(
                        attributeCodec, attributeCodec.fieldOf("modifiers").codec()));
        versionCompatCodecs = builder.build();
    }

    @Override
    public Map<ComponentType<?>, Codec<?>> getVersionCompatCodecs() {
        return versionCompatCodecs;
    }
}
