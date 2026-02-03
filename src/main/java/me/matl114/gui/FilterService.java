package me.matl114.gui;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import me.matl114.gui.basic.*;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;

public class FilterService {
    public static String currentUserInput = "";
    public static BiPredicate<String, RecipeEntry> RECIPE_FILTER = (str, i)->{
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

    private static final List<Text> RESET_FILTER_TOOLTIPS = List.of(Text.literal("点击重置过滤器"));
    protected static Identifier RESET_FILTER_TEXTURE = new Identifier("minecraft","container/beacon/cancel");
    public static SubScreenWidget createFilter(Runnable updateListener, int x, int y, int dx, int dy){
        var textField = McWidgetHelpers.createTextFieldEditBox(  dy, 0, dx - dy, dy ,(t, r)->{
            if(!Objects.equals(FilterService.currentUserInput, r)){
                FilterService.currentUserInput = r;
                updateListener.run();
            }
        }, FilterService.currentUserInput);
        var textFieldCleanerBackground = DisplayWidget.instance(0,0,dy,dy)
            .setRenderHandler(
                new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.empty())
                    .withTooltips(TooltipHandler.of(RESET_FILTER_TOOLTIPS))
            );
        var textFieldCleaner = ExecutableWidget.instance(0,0, dy, dy)
            .setElementHandler(
                IconElement.fixedGui(RESET_FILTER_TEXTURE, ButtonAction.run(()->{
                        if(textField.getDelegate() != null){
                            textField.getDelegate().setText("");
                        }
                    }))
                    .withTooltips(TooltipHandler.of(RESET_FILTER_TOOLTIPS))
            );
        return new SubScreenWidget(x, y, dx, dy)
            .addDrawableChild(textField)
            .addDrawableChild(textFieldCleanerBackground)
            .addDrawableChild(textFieldCleaner);
    }
}
