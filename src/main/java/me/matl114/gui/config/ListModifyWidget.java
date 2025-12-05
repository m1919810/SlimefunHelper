package me.matl114.gui.config;

import me.matl114.gui.basic.*;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class ListModifyWidget extends ScrollableListWidget {
    ListEntryWidgetController controller;
    public ListModifyWidget(ListEntryWidgetController controller, int x, int y, int dx, int dy){
        super(x, y, dx, dy);
        this.controller = controller;
        refreshList();
    }
    protected void refreshList(){
        clearScrollingWidget();
        int size = controller.size();
        for (int i=0; i<size; ++i){
            SubScreenWidget widget = wrapWidget(controller.getEntryWidget(i), i, 0,0);
            addScrollingWidget(widget);
        }
        addScrollingWidget(getListEndAdd(0,0));
    }
    private static final Identifier SHIFT_UP_TEXTURE = new Identifier("slimefunhelper", "textures/gui/move_up.png");
    private static final Identifier SHIFT_DOWN_TEXTURE = new Identifier("slimefunhelper", "textures/gui/move_down.png");
    private static final Identifier DEL_TEXTURE = new Identifier("slimefunhelper", "textures/gui/remove.png");
    private static final Identifier NEW_TEXTURE = new Identifier("slimefunhelper", "textures/gui/add.png");
    private static final List<Text> SHIFT_UP_TOOLTIPS = List.of(Text.literal("上移"));
    private static final List<Text> SHIFT_DOWN_TOOLTIPS = List.of(Text.literal("下移"));
    private static final List<Text> DEL_TOOLTIPS = List.of(Text.literal("删除"));
    private static final List<Text> NEW_TOOLTIPS = List.of(Text.literal("插入"));
    protected <T extends Element & Drawable & Selectable> SubScreenWidget wrapWidget(T widget, int listIndex, int startX, int startY){
        int height = controller.height();
        int width = controller.width();
        DrawableWidget wrap1 = widget instanceof DrawableWidget www?www: new ContentDelegateWidget<>(0,0,width,height).setContentDelegate(widget);
        int curHeight = startY + height * listIndex;
        int buttonSize = Math.min(20, height);
        return new SubScreenWidget(
            startX , curHeight, width, height
        )
            .addDrawableChild(wrap1)
            .addDrawableChild(
                ExecutableWidget.instance(width + 1, 1, buttonSize -2, buttonSize -2)
                    .setElementHandler(
                        IconElement.fixed(SHIFT_UP_TEXTURE,ButtonAction.run(()->{
                            if(this.controller.shiftUp(listIndex)){
                                refreshList();
                            }
                        }))
                            .setActive(listIndex != 0)
                            .withTooltips(TooltipHandler.of(SHIFT_UP_TOOLTIPS))
                    )
            )
            .addDrawableChild(
                ExecutableWidget.instance(width + buttonSize +1, 1, buttonSize -2, buttonSize -2)
                    .setElementHandler(
                        IconElement.fixed(SHIFT_DOWN_TEXTURE,ButtonAction.run(()->{
                                if(this.controller.shiftDown(listIndex)){
                                    refreshList();
                                }
                            }))
                            .setActive(listIndex != controller.size() - 1)
                            .withTooltips(TooltipHandler.of(SHIFT_DOWN_TOOLTIPS))
                    )
            )
            .addDrawableChild(
                ExecutableWidget.instance( width + 2* buttonSize +1, 1, buttonSize -2, buttonSize -2)
                    .setElementHandler(
                        IconElement.fixed(DEL_TEXTURE,ButtonAction.run(()->{
                                if(this.controller.del(listIndex)){
                                    refreshList();
                                }
                            }))
                            .withTooltips(TooltipHandler.of(DEL_TOOLTIPS))
                    )
            )
            .addDrawableChild(
                ExecutableWidget.instance(width + 3* buttonSize +1, 1, buttonSize -2, buttonSize -2)
                    .setElementHandler(
                        IconElement.fixed(NEW_TEXTURE,ButtonAction.run(()->{
                                if(this.controller.insert(listIndex)){
                                    refreshList();
                                }
                            }))
                            .withTooltips(TooltipHandler.of(NEW_TOOLTIPS))
                    )
            );
    }
    protected ExecutableWidget getListEndAdd(int startX, int startY){
        int height = controller.height();
        int width = controller.width();
        int buttonSize = Math.min(20, height);
        return ExecutableWidget.instance(startX + width/2 - buttonSize/2, startY + height * controller.size(), buttonSize, buttonSize)
            .setElementHandler(
                IconElement.fixed(NEW_TEXTURE, ButtonAction.run(()->{
                    if(this.controller.insert(-1)){
                        refreshList();
                    }
                }))
                    .withTooltips(TooltipHandler.of(NEW_TOOLTIPS))
            );
    }
}
