package me.matl114.versioned.impl;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.DataVersion;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Unit;

public class ItemUtils_v1_21_1 implements VItem {
    @Override
    public boolean canGlide(ItemStack stack) {
        return stack.contains(DataComponentTypes.GLIDER);
    }

    @Override
    public boolean isSpear(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        if (stack.getItem() instanceof MaceItem) {
            return true;
        } else if (stack.contains(DataComponentTypes.TOOL)) {
            Item tool = stack.getItem();
            if (tool instanceof AxeItem) {
                return true;
            } else if (tool instanceof MiningToolItem) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean isTool(ItemStack stack) {
        return stack.contains(DataComponentTypes.TOOL);
    }

    @Override
    public boolean isShield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem;
    }

    @Override
    public ItemStack fromNbt(NbtCompound tag) {
        return ItemStack.fromNbtOrEmpty(ItemStackUtils.registry(), tag);
    }

    @Override
    public NbtCompound toNbt(ItemStack tag) {
        NbtCompound tagCompound = toNbt0(tag);
        tagCompound.putInt(DataVersion.DATA_VERSION_FLAG, DataVersion.getDataVersion());
        return tagCompound;
    }

    private NbtCompound toNbt0(ItemStack tag) {
        return tag.isEmpty() ? new NbtCompound() : (NbtCompound) tag.toNbt(ItemStackUtils.registry());
    }

    @Override
    public CustomModelDataComponent createModelData(int cmd) {
        return new CustomModelDataComponent(List.of((float) cmd), List.of(), List.of(), List.of());
    }

    private static final Map<ComponentType<?>, Codec<?>> VERSIONED;

    static {
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
        builder.put(
                DataComponentTypes.UNBREAKABLE,
                Codec.withAlternative(
                        UnbreakableComponent.CODEC,
                        Unit.CODEC.xmap(s -> new UnbreakableComponent(true), b -> Unit.INSTANCE)));
        VERSIONED = builder.build();
    }

    @Override
    public Map<ComponentType<?>, Codec<?>> getVersionCompatCodecs() {
        return VERSIONED;
    }
}
