package me.matl114.renders.implement;

import me.matl114.renders.RenderMain;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
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
    public static void init(){

    }
    static{
        RenderMain.registerModelOverridePredicate((item)->{
            ItemEnchantmentsComponent list = ItemStackUtils.getStoredEnchantment(item);
            if(list != null && !list.isEmpty()){
                var optional =  list.getEnchantmentEntries().stream()
                    .findFirst();
                if(optional.isPresent()){
                    var entry = optional.get();
                    Enchantment enchantment = entry.getKey().value();
                    Optional<RegistryKey<Enchantment>> identifier = entry.getKey().getKey();
                    if(enchantment != null && identifier.isPresent()){
                        Identifier identifier2 = identifier.get().getValue();
                        int maxValue = enchantment.getMaxLevel();
                        int level = entry.getIntValue();
                        if(level == 0)return Optional.empty();

                        return Optional.of( level == 1 ? new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()) :(level == maxValue? new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()+MAX_VALUE):(level > maxValue? new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()+OVER_MAX_VALUE) : new Identifier(NAMESPACE, MODEL_PATH+ identifier2.getPath()+ "_"+ level)) )).map(RenderMain::wrapAsModel);
                    }
                }
            }
            return Optional.empty();
        });
    }
}
