package me.matl114.versioned.impl;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;

public class ItemUtils_v1_21_11 implements VItem {
    @Override
    public boolean canGlide(ItemStack stack) {
        return stack.contains(DataComponentTypes.GLIDER);
    }

    @Override
    public boolean isSpear(ItemStack stack) {
        return stack.contains(DataComponentTypes.KINETIC_WEAPON);
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
    public boolean isShield(ItemStack stack) {
        return stack.contains(DataComponentTypes.BLOCKS_ATTACKS);
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

    @Override
    public Map<ComponentType<?>, Codec<?>> getVersionCompatCodecs() {
        return Map.of();
    }
}
