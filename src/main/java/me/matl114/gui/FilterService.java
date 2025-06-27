package me.matl114.gui;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import me.matl114.hackUtils.SlimefunTasks;
import net.minecraft.item.ItemStack;

import java.util.Locale;
import java.util.function.BiPredicate;

public class FilterService {
    public static String currentUserInput = "";
    public static BiPredicate<String, SlimefunTasks.RecipeEntry> RECIPE_FILTER = (str, i)->{
        if(str==null || str.isEmpty())return true;
        if(str.startsWith("@")){
            String str1 = str.substring(1);
            return i.id().toLowerCase(Locale.ROOT).contains(str1.toLowerCase(Locale.ROOT));
        }else {
            return nameMatch( i.output().getName().getString().replaceAll("§.", ""),str);
        }
    };
    public static BiPredicate<String, ItemStack> ITEM_FILTER = (str, i)->{
        if(str==null || str.isEmpty())return true;
        return nameMatch( i.getName().getString().replaceAll("§.", ""),str);

    };


    public static BiPredicate<String,String> RTYPE_ID_FILTER = (str,i)->i.contains(str);
    public static boolean nameMatch(String name, String filter){
        if(filter == null || filter.isEmpty())return true;
        filter = filter.toLowerCase(Locale.ROOT);
        name = name.toLowerCase(Locale.ROOT);
        if(name.contains(filter)){
            return true;
        }
        String pinyin1 = PinyinHelper.toPinyin(name, PinyinStyleEnum.INPUT, "").toLowerCase(Locale.ROOT);
        if(pinyin1.contains(filter)){
            return true;
        }
        pinyin1 = PinyinHelper.toPinyin(name, PinyinStyleEnum.FIRST_LETTER, "").toLowerCase(Locale.ROOT);
        return pinyin1.contains(filter);
    }
}
