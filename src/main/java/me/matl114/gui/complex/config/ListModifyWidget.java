package me.matl114.gui.complex.config;

import java.util.List;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.utils.ChatUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ListModifyWidget extends ScrollableListWidget {
    ListEntryWidgetController controller;
    boolean appendAdd = true;

    public ListModifyWidget appendAddButton(boolean bl) {
        this.appendAdd = bl;
        return this;
    }

    public ListModifyWidget(ListEntryWidgetController controller, int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
        this.controller = controller;
        this.controller.markDirty(true);
    }

    protected void refreshList() {
        clearScrollingWidget();
        int size = controller.size();
        for (int i = 0; i < size; ++i) {
            SubScreenWidget widget = wrapWidget(controller.getEntryWidget(i), i, 0, 0);
            addScrollingWidget(widget);
        }
        if (appendAdd) {
            addScrollingWidget(getListEndAdd(0, 0));
        }
    }

    @Override
    public void render0(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        if (this.controller.dirty()) {
            refreshList();
            this.controller.markDirty(false);
        }
        super.render0(context, mouseX, mouseY, delta, disableSelect);
    }

    private static final Identifier SHIFT_UP_TEXTURE_SPRITE = Constants.SHIFT_UP_SPRITE;
    private static final Identifier SHIFT_DOWN_TEXTURE_SPRITE = Constants.SHIFT_DOWN_SPRITE;
    private static final Identifier DEL_TEXTURE_SPRITE = Constants.REMOVE_SPRITE;
    private static final Identifier NEW_TEXTURE_SPRITE = Constants.ADD_SPRITE;

    private static List<Text> insertTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.list-modify-widget.insert.tooltips", "");
    }

    protected <T extends Element & Drawable & Selectable> SubScreenWidget wrapWidget(
            T widget, int listIndex, int startX, int startY) {
        int height = controller.height();
        int width = controller.width();
        DrawableWidget wrap1 = widget instanceof DrawableWidget www
                ? www
                : new ContentDelegateWidget<>(0, 0, width, height).setContentDelegate(widget);
        int curHeight = startY + height * listIndex;
        int buttonSize = Math.min(20, height);
        int buttonMiddle = 0;
        return new SubScreenWidget(startX, curHeight, width, height)
                .addDrawableChild(wrap1)
                .addDrawableChild(ExecutableWidget.instance(width + 1, 1 + buttonMiddle, buttonSize - 2, buttonSize - 2)
                        .setElementHandler(IconElement.fixedGui(SHIFT_UP_TEXTURE_SPRITE, ButtonAction.run(() -> {
                                    this.controller.shiftUp(listIndex);
                                }))
                                .setActive(listIndex != 0)
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.list-modify-widget.shift-up.tooltips", "")))))
                .addDrawableChild(ExecutableWidget.instance(
                                width + buttonSize + 1, 1 + buttonMiddle, buttonSize - 2, buttonSize - 2)
                        .setElementHandler(IconElement.fixedGui(SHIFT_DOWN_TEXTURE_SPRITE, ButtonAction.run(() -> {
                                    this.controller.shiftDown(listIndex);
                                }))
                                .setActive(listIndex != controller.size() - 1)
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.list-modify-widget.shift-down.tooltips", "")))))
                .addDrawableChild(ExecutableWidget.instance(
                                width + 2 * buttonSize + 1, 1 + buttonMiddle, buttonSize - 2, buttonSize - 2)
                        .setElementHandler(IconElement.fixedGui(DEL_TEXTURE_SPRITE, ButtonAction.run(() -> {
                                    this.controller.del(listIndex);
                                }))
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.list-modify-widget.delete.tooltips", "")))))
                .addDrawableChild(ExecutableWidget.instance(
                                width + 3 * buttonSize + 1, 1 + buttonMiddle, buttonSize - 2, buttonSize - 2)
                        .setElementHandler(IconElement.fixedGui(NEW_TEXTURE_SPRITE, ButtonAction.run(() -> {
                                    this.controller.insert(listIndex);
                                }))
                                .withTooltips(TooltipHandler.of(ListModifyWidget.insertTooltips()))));
    }

    protected ExecutableWidget getListEndAdd(int startX, int startY) {
        int height = controller.height();
        int width = controller.width();
        int buttonSize = Math.min(20, height);
        return ExecutableWidget.instance(
                        startX + width / 2 - buttonSize / 2 + 2 * buttonSize,
                        startY + height * controller.size(),
                        buttonSize,
                        buttonSize)
                .setElementHandler(IconElement.fixedGui(NEW_TEXTURE_SPRITE, ButtonAction.run(() -> {
                            this.controller.insert(-1);
                        }))
                        .withTooltips(TooltipHandler.of(ListModifyWidget.insertTooltips())));
    }
}
