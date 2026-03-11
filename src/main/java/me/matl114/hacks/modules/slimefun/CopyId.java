package me.matl114.hacks.modules.slimefun;

import static me.matl114.utils.ItemStackUtils.getSfId;

import java.util.Locale;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.Point;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

public class CopyId extends BaseModule {
    private static final String[] COPY_ID = new String[] {"slimefun-settings", "slimefunid-copy"};

    public CopyId() {}

    public KeyBindRef keyBind = hotkey(Configs.SLIMEFUN_CONFIG, COPY_ID)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_C, KeyCode.MOUSE_BUTTON_1))
            .registerHotkey(HotKeyUtils.asHandler(this::copySfIdInHand))
            .build();

    public boolean copySfIdInHand() {
        var client = MinecraftClient.getInstance();
        var player = MinecraftClient.getInstance().player;
        if (player == null || client == null) return false;
        ItemStack heldItem = null;
        if (client.currentScreen instanceof HandledScreen<?> s) {
            Point mouseCoord = ScreenUtils.getMouseCoord(client);
            Slot slot = HandledScreenAccess.of(s).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
            if (slot != null) {
                heldItem = slot.getStack();
            }
        } else {
            heldItem = player.getStackInHand(Hand.MAIN_HAND);
        }
        if (heldItem != null) {
            String sfid = getSfId(heldItem);

            if (sfid != null) {
                client.keyboard.setClipboard(sfid);
                Debug.chat(Text.literal("成功将Slimefun ID拷贝至你的剪切板和公共参数! 值: ")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(sfid).formatted(Formatting.WHITE)));

                return true;
            } else {
                String id = Registries.ITEM.getId(heldItem.getItem()).getPath().toUpperCase(Locale.ROOT);
                client.keyboard.setClipboard(id);
                Debug.chat(Text.literal("该物品不是Slimefun物品,拷贝原版ID!")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(id).formatted(Formatting.WHITE)));
                return true;
            }
        }
        return false;
    }
}
