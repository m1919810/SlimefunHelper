package me.matl114.gui.presets.lists;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import net.minecraft.util.Colors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class ListSelectWidget<W> extends ScrollableListWidget {
    protected W selected;
    List<W> list;
    int entryHeight;
    List<W> filterList;
    Function<W, RenderHandler> renderFactory;
    Predicate<W> filter;
    boolean modifiable = true;

    public ListSelectWidget(
            List<W> lst,
            Function<W, RenderHandler> renderFactory,
            Predicate<W> filter,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        super(x, y, dx, dy);
        this.list = lst;
        this.entryHeight = height;
        this.filter = filter;
        this.renderFactory = renderFactory;
        init();
    }

    public ListSelectWidget<W> filter(Predicate<W> fil) {
        if (this.filter != fil) {
            this.filter = fil;
            updateFilterList();
        }
        return this;
    }

    protected void refreshList() {
        clearScrollingWidget();
        int size = this.filterList.size();
        boolean matchSelect = false;
        for (var i = 0; i < size; ++i) {
            addScrollingWidget(generateEntry(i));
            if (this.filterList.get(i) == this.selected) {
                matchSelect = true;
            }
        }
        if (!matchSelect) this.selected = null;
    }

    protected DrawableWidget generateEntry(int index) {
        W triplet = this.filterList.get(index);
        ExecutableWidget shitWidget = ExecutableWidget.instance(0, this.entryHeight * index, this.dx, this.entryHeight);
        RenderHandler renderHandler = renderFactory.apply(triplet);
        renderHandler =
                renderHandler.combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                    if (triplet == selected) {
                        RenderHandler.drawHighLightBox(context, 0, 0, this.dx, this.entryHeight, Colors.WHITE);
                    }
                });
        InputHandler mouseHandler = InputHandler.run(() -> {
            if (modifiable) {
                this.selected = triplet;
            }
        });
        shitWidget.setMouseHandler(mouseHandler).setRenderHandler(renderHandler);
        return shitWidget;
    }
    //    protected void applyFilter(String filter){
    //        if(!Objects.equals(FilterService.currentUserInput, filter)){
    //            FilterService.currentUserInput = filter;
    //            updateFilterList();
    //        }
    //    }
    public void updateFilterList() {
        filterList = list.stream().filter(filter).toList();
        refreshList();
        //        if (FilterService.currentUserInput == null || FilterService.currentUserInput.isEmpty()) {
        //            if (filterList != list) {
        //                filterList = list;
        //                refreshList();
        //            }
        //        } else {
        //
        //        }
    }

    protected void init() {
        int textHeight = Math.min(20, this.entryHeight);
        this.scrollableBorder.addDrawableChild(
                FilterService.createFilter(this::updateFilterList, 1, -textHeight + 1, dx - 2, textHeight - 2)
                //   McWidgetHelpers.createTextFieldEditBox( PropertyTracker.event(this::applyFilter),
                // FilterService.currentUserInput)
                );
        this.updateFilterList();
    }
}
