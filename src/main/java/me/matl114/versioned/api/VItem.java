package me.matl114.versioned.api;

import com.mojang.serialization.Codec;
import java.util.Map;
import me.matl114.versioned.impl.ItemUtils_v1_21_11;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public interface VItem {
    public static final VItem INSTANCE = new ItemUtils_v1_21_11();

    public static VItem getInstance() {
        return INSTANCE;
    }

    public boolean canGlide(ItemStack stack);

    public boolean isSpear(ItemStack stack);

    public boolean isWeapon(ItemStack stack);

    public boolean isTool(ItemStack stack);

    public boolean isShield(ItemStack stack);

    public ItemStack fromNbt(NbtCompound tag);

    public NbtCompound toNbt(ItemStack tag);

    public CustomModelDataComponent createModelData(int cmd);

    public Map<ComponentType<?>, Codec<?>> getVersionCompatCodecs();
}
