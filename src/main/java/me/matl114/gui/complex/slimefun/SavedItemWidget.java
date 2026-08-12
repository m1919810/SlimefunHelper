package me.matl114.gui.complex.slimefun;

import java.util.function.Consumer;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.PlateElement;
import me.matl114.gui.elements.SlotElement;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.ChatUtils;
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
                                TextProvider.of(Text.translatable("widget.gui.saved-item-widget.open-editor")),
                                ButtonAction.run(() -> InvTasks.openEditScreen(itemStack, null)))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.saved-item-widget.open-editor.tooltips", ""))))
                .addToSub(this);

        ExecutableWidget.instance(75, 36, 25, 16)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.translatable("widget.gui.saved-item-widget.creative-give")),
                                ButtonAction.run(() -> {
                                    if (MinecraftClient.getInstance().player != null
                                            && MinecraftClient.getInstance()
                                                    .interactionManager
                                                    .getCurrentGameMode()
                                                    .isCreative()) {
                                        InvTasks.creativeAddItem(this.itemStack, 64);
                                    } else {
                                        Debug.chat(Text.translatable("widget.gui.saved-item-widget.creative-give.error")
                                                .formatted(Formatting.YELLOW));
                                    }
                                }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.saved-item-widget.creative-give.tooltips", ""))))
                .addToSub(this);

        ExecutableWidget.instance(105, 12, 25, 16)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.translatable("widget.gui.saved-item-widget.delete-item")),
                                ButtonAction.run(() -> InvTasks.getSaveItem().removeSavedItem(this.itemStack)))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.saved-item-widget.delete-item.tooltips", ""))))
                .addToSub(this);
        ExecutableWidget.instance(105, 36, 25, 16)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.translatable("widget.gui.saved-item-widget.copy-command")),
                                ButtonAction.run(() -> {
                                    InvTasks.copyGiveCommand(this.itemStack);
                                }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.saved-item-widget.copy-command.tooltips", ""))))
                .addToSub(this);
    }
}
