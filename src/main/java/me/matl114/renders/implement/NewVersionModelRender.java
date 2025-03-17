package me.matl114.renders.implement;

import me.matl114.renders.RenderMain;
import me.matl114.renders.SlimefunCustomModelManager;
import me.matl114.renders.SlimefunItemResourcePack;
import me.matl114.utils.Debug;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

public class NewVersionModelRender {
    public static void init(){

    }
    private static final Set<Item> NEWVERSION_ITEM_FORCE_DISPLAY = new HashSet<>(){{
        add(Items.ENCHANTED_BOOK);
    }};
    public static String PATH_OF_NEW_VERSION = "new-version";
    public static String NAMESPACE = "slimefunhelper";
    public static ItemStack ofNewVersion(ItemStack stack) {
        stack.getOrCreateNbt().putBoolean(PATH_OF_NEW_VERSION, true);
        return stack;
    }
    public static boolean isNewVersion(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean(PATH_OF_NEW_VERSION);
    }
    public static Identifier resolveNewModel(ItemStack stack) {
        Item item = stack.getItem();
        if( item == Items.ENCHANTED_BOOK ) {
            return new Identifier(NAMESPACE, "enchanted_book_");
        }
        return  NEW_VERSION_ITEMS.get(item);
    }
    public static final Map<Item,Identifier> NEW_VERSION_ITEMS = new HashMap<>();

    static {
        SlimefunCustomModelManager.registerResourceReloadTasks(()->{
            NEW_VERSION_ITEMS.clear();
            for(Item item : Registries.ITEM) {
                Identifier id = new Identifier(NAMESPACE,PATH_OF_NEW_VERSION + "/" + Registries.ITEM.getId(item).getPath());
                if(RenderMain.validateModel(id)){
                    NEW_VERSION_ITEMS.put(item, id);
                    Debug.info("Loading new-version model",id);
                }
            }
        });
        RenderMain.registerModelOverridePredicate(1005, (stack)->{
            if(NEWVERSION_ITEM_FORCE_DISPLAY.contains(stack.getItem()) || isNewVersion(stack)){
                return Optional.ofNullable(resolveNewModel(stack));
            }else {
                return Optional.empty();
            }
        });
    }
}
