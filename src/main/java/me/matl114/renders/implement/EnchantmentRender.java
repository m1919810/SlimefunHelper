package me.matl114.renders.implement;

import me.matl114.renders.RenderMain;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EnchantmentRender {
    public static final String NAMESPACE = "slimefunhelper";
    public static final String MODEL_PATH = "enchanted_book/";
    public static final String MAX_VALUE = "_max";
    public static final String OVER_MAX_VALUE = "_over";
    private static final Map<Identifier,Integer> ENCHANTMENT_MAX_VALUE = new HashMap<Identifier,Integer>();
    public static void init(){

    }
    static{
        for (Enchantment ench: Registries.ENCHANTMENT){
            Identifier id = Registries.ENCHANTMENT.getId(ench);
            ENCHANTMENT_MAX_VALUE.put(id, ench.getMaxLevel());
        }
        RenderMain.registerModelOverridePredicate((item)->{
            NbtList list = ItemStackUtils.getStoredEnchantment(item);
            if(list != null && !list.isEmpty()){
                NbtCompound nbtCompound = list.getCompound(0);
                Identifier identifier2 = EnchantmentHelper.getIdFromNbt(nbtCompound);
                if(identifier2 != null && ENCHANTMENT_MAX_VALUE.containsKey(identifier2)){
                    int maxValue = ENCHANTMENT_MAX_VALUE.get(identifier2);
                    int level = EnchantmentHelper.getLevelFromNbt(nbtCompound);
                    if(level == 0)return Optional.empty();
                    return Optional.of( level == 1 ? new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()) :(level == maxValue? new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()+MAX_VALUE):(level > maxValue? new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()+OVER_MAX_VALUE) : new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()+ "_"+ level)) ));
                }
            }
            return Optional.empty();
        });
    }
}
