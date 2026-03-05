package me.matl114.gui.slimefun;

import java.util.List;
import java.util.function.Consumer;
import me.matl114.gui.basic.*;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SavedItemWidget extends SubScreenWidget {
    ItemStack itemStack;
    Consumer<ItemStack> clickIcon;
    // change it later
    // 14， 11- 29 16 - 119 +4， 30 +4
    protected static final int DX = 144;
    protected static final int DY = 64;

    public SavedItemWidget(int x, int y, ItemStack itemStack, Consumer<ItemStack> callback) {
        super(x, y, DX, DY);
        this.itemStack = itemStack;
        this.clickIcon = callback == null ? (it) -> {} : callback;
        init0();
    }

    private static final List<Text> CREATIVEGIVE_TOOLTIPS =
            List.of(Text.literal("获得物品"), Text.literal(""), Text.literal("仅在创造模式可用"));
    private static final List<Text> COPYCOMMAND_TOOLTIPS = List.of(Text.literal("拷贝获取/give指令"));

    private static final List<Text> OPENEDITOR_TOOLTIPS = List.of(Text.literal("在物品编辑器中打开该物品"));
    private static final List<Text> DELITEM_TOOLTIPS = List.of(Text.literal("将该物品移除保存物品"));

    private final void init0() {
        DisplayWidget.instance(0, 0, DX, DY)
                .setRenderHandler(PlateElement.instance())
                .addToSub(this);
        ExecutableWidget.instance(15, 5, 54, 54)
                .setElementHandler(new SlotElement(this.itemStack)
                        .withInputHandler(InputHandler.run(() -> this.clickIcon.accept(this.itemStack))))
                .addToSub(this);
        ExecutableWidget.instance(75, 12, 25, 16)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("Editor")),
                                ButtonAction.run(() -> InvTasks.openEditScreen(itemStack, null)))
                        .withTooltips(TooltipHandler.of(OPENEDITOR_TOOLTIPS)))
                .addToSub(this);

        ExecutableWidget.instance(75, 36, 25, 16)
                .setElementHandler(new ButtonElement(TextProvider.of(Text.literal("Give")), ButtonAction.run(() -> {
                            if (MinecraftClient.getInstance().player != null
                                    && MinecraftClient.getInstance()
                                            .interactionManager
                                            .getCurrentGameMode()
                                            .isCreative()) {
                                InvTasks.creativeAddItem(this.itemStack, 64);
                            } else {
                                Debug.chat(Text.literal("当前并不处于创造模式,无法获取保存物品!").formatted(Formatting.YELLOW));
                            }
                        }))
                        .withTooltips(TooltipHandler.of(CREATIVEGIVE_TOOLTIPS)))
                .addToSub(this);

        ExecutableWidget.instance(105, 12, 25, 16)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("Remove")),
                                ButtonAction.run(() -> InvTasks.getSaveItem().removeSavedItem(this.itemStack)))
                        .withTooltips(TooltipHandler.of(DELITEM_TOOLTIPS)))
                .addToSub(this);
        ExecutableWidget.instance(105, 36, 25, 16)
                .setElementHandler(new ButtonElement(TextProvider.of(Text.literal("Command")), ButtonAction.run(() -> {
                            InvTasks.copyGiveCommand(this.itemStack);
                        }))
                        .withTooltips(TooltipHandler.of(COPYCOMMAND_TOOLTIPS)))
                .addToSub(this);
    }
}
