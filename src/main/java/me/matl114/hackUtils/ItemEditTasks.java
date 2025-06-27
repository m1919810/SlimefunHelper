package me.matl114.hackUtils;

import me.matl114.access.ScreenAccess;
import me.matl114.gui.itemEdit.ItemEditScreen;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

public class ItemEditTasks {
    public static void openEditor(){
        if(MinecraftClient.getInstance().player != null){
            openEditor(MinecraftClient.getInstance().player);
        }
    }
    public static void openEditor(ClientPlayerEntity entity){
        ItemStack stack = ScreenUtils.getSelectingItemOrHand();
        if (stack != null){
            openEditScreen(stack, null);
        }else {
            Debug.chat(Text.literal("你必须选择一个物品以打开").formatted(Formatting.RED));
        }

    }
    public static void openEditScreen(ItemStack item, Consumer<ItemStack> callback){
        if(item.isEmpty()){
            Debug.chat(Text.literal("你不能打开空物品的编辑器!"));return;
        }
        ScreenAccess.of(new ItemEditScreen(Text.empty(), item, callback)).openFromCurrent();
    }
}
