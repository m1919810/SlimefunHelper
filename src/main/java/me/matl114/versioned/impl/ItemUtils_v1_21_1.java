package me.matl114.versioned.impl;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
        return stack.getItem() instanceof ElytraItem;
    }

    @Override
    public boolean isSpear(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        if (stack.getItem() instanceof MaceItem) {
            return true;
        } else if (stack.getItem() instanceof ToolItem tool) {
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
        return stack.getItem() instanceof ToolItem;
    }

    @Override
    public boolean isNotAttackingTool(ItemStack stack) {
        return !isWeapon(stack);
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
        NbtCompound nbt = (NbtCompound) tag.encodeAllowEmpty(ItemStackUtils.registry());
        nbt.putInt(DataVersion.DATA_VERSION_FLAG, DataVersion.getDataVersion());
        return nbt;
    }

    @Override
    public CustomModelDataComponent createModelData(int cmd) {
        return new CustomModelDataComponent(cmd);
    }

    private static final Map<ComponentType<?>, Codec<?>> VERSIONED;

    static {
        var builder = ImmutableMap.<ComponentType<?>, Codec<?>>builder();
        builder.put(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                Codec.withAlternative(CustomModelDataComponent.CODEC, RecordCodecBuilder.create((instance) -> {
                    return instance.group(Codec.FLOAT
                                    .listOf()
                                    .optionalFieldOf("floats", List.of())
                                    .forGetter(s -> s.value() == 0 ? List.of() : List.of((float) s.value())))
                            .apply(
                                    instance,
                                    floats -> new CustomModelDataComponent(
                                            floats.isEmpty() ? 0 : (int) (float) floats.get(0)));
                })));
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
