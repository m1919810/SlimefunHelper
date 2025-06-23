package me.matl114.hackUtils;

import me.matl114.access.ScreenAccess;
import me.matl114.gui.itemEdit.ItemEditScreen;
import me.matl114.utils.Debug;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ItemEditTasks {
    public static void openEditor(ClientPlayerEntity entity){
        openEditScreen(entity.getMainHandStack(), null);
    }
    public static void openEditScreen(ItemStack item, Consumer<ItemStack> callback){
        if(item.isEmpty()){
            Debug.chat(Text.literal("你不能打开空物品的编辑器!"));return;
        }
        ScreenAccess.of(new ItemEditScreen(Text.empty(), item, callback)).openFromCurrent();
    }
}
