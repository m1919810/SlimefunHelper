package me.matl114.hacks.modules.inv;

import java.util.function.Consumer;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.complex.itemEdit.ItemEditScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ItemEditor extends BaseModule {
    public final ModulePath itemEditor = makePath(Configs.INV_CONFIG, "item-editor");

    public ItemEditor() {}

    public final KeyBindRef keyBind = hotkey(
                    Configs.INV_CONFIG, itemEditor.add("open-editor").toPath())
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_I))
            .registerHotkey(HotKeyUtils.asHandler(this::openEditor))
            .build();

    public boolean openEditor() {
        if (mc.player != null) {
            openEditorForPlayer(mc.player);
            return true;
        } else return false;
    }

    public void openEditorForPlayer(ClientPlayerEntity entity) {
        ItemStack stack = ScreenUtils.getSelectingOrHandItem();
        if (stack != null) {
            openEditScreen(stack, null);
        } else {
            Debug.chat(Text.literal("你必须选择一个物品以打开").formatted(Formatting.RED));
        }
    }

    @ApiMethod
    public void openEditScreen(ItemStack item, Consumer<ItemStack> callback) {
        if (item.isEmpty()) {
            Debug.chat(Text.literal("你不能打开空物品的编辑器!"));
            return;
        }
        ScreenAccess.of(new ItemEditScreen(Text.empty(), item, callback)).openFromCurrent();
    }
}
