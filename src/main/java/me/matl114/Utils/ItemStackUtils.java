package me.matl114.Utils;

import me.matl114.Access.DrawContextAccess;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

public class ItemStackUtils {
    public static NbtCompound getDisplay(ItemStack stack){
        if(stack.hasNbt()){
            if(stack.getNbt().contains("display")){
                return stack.getNbt().getCompound("display");
            }
        }
        return null;
    }
    public static void setDisplay(ItemStack stack,NbtCompound display){
        stack.getOrCreateNbt().put("display", display);
    }

    public static NbtCompound getEnchantment(ItemStack stack){
        if(stack.hasNbt()){
            if(stack.getNbt().contains("Enchantments")){
                return stack.getNbt().getCompound("Enchantments");
            }
        }
        return null;
    }
    public static void setEnchantment(ItemStack stack, NbtList enchantments){
        stack.getOrCreateNbt().put("Enchantments", enchantments);
    }
    public static void setStoredEnchantment(ItemStack stack, NbtList enchantments){
        stack.getOrCreateNbt().put("StoredEnchantments", enchantments);
    }
    public static ItemStack getCleanedItem(ItemStack stack){
        return getCleanedItem(stack, true);
    }
    public static ItemStack getCleanedItem(ItemStack stack,boolean keepDur){
        return getCleanedItem(stack,keepDur,true);
    }
    public static ItemStack getCleanedItem(ItemStack stack,boolean keepDur,boolean keepEnchant){
        return getCleanedItem(stack,true,keepDur,keepEnchant);
    }
    public static ItemStack getCleanedItem(ItemStack stack,boolean keepNBT,boolean keepDur,boolean keepEnchant){
        return getCleanedItem(stack, -999, keepNBT, keepDur, keepEnchant);
    }
    public static ItemStack getCleanedItem(ItemStack stack,int setAmount,boolean keepNBT,boolean keepDur,boolean keepEnchant){
        ItemStack cleaned=stack.getItem().getDefaultStack();

        if(!keepNBT){
            if(setAmount!=-999){
                cleaned.setCount(setAmount);
            }
            return cleaned;
        }
        ItemStack stackCopy=stack.copy();
        if(setAmount!=-999){
            stack.setCount(setAmount);
        }
        if(!keepDur){
            stackCopy.setDamage(cleaned.getDamage());
        }
        if(!keepEnchant){
            stackCopy.removeSubNbt("Enchantments");
            stackCopy.removeSubNbt("StoredEnchantments");
        }

        return stackCopy;
    }
    public static boolean isSimilarItemStack(ItemStack stack1,ItemStack stack2){
        return ItemStack.canCombine(stack1,stack2);
    }
}
