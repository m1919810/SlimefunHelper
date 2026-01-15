package me.matl114.gui.presets.lists;

import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import net.minecraft.util.Colors;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ListSelectWidget<W> extends ScrollableListWidget {
    protected W selected;
    List<W> list;
    int entryHeight;
    List<W> filterList;
    Function<W, RenderHandler> renderFactory;
    Predicate<W> filter;
    public W getSelectedEntry(){
        return selected;
    }
    public ListSelectWidget(List<W> lst, Function<W, RenderHandler> renderFactory, Predicate<W> filter, int x, int y, int dx, int dy, int height){
        super(x, y, dx, dy);
        this.list = lst;
        this.entryHeight = height;
        this.filter = filter;
        this.renderFactory = renderFactory;
        init();
    }

    protected void refreshList(){
        clearScrollingWidget();
        int size = this.filterList.size();
        boolean matchSelect =false;
        for (var i = 0 ;i<size;++i){
            addScrollingWidget(generateEntry(i));
            if(this.filterList.get(i) == this.selected){
                matchSelect = true;
            }
        }
        if(!matchSelect)
            this.selected = null;
    }
    protected DrawableWidget generateEntry(int index){
        W triplet = this.filterList.get(index);
        ExecutableWidget shitWidget = ExecutableWidget.instance(0, this.entryHeight * index, this.dx, this.entryHeight);
        RenderHandler renderHandler = renderFactory.apply(triplet);
        renderHandler = renderHandler.combineRender(
            (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                if(triplet == selected){
                    McWidgetHelpers.drawHighLightBox(context, 0, 0, this.dx, this.entryHeight, Colors.WHITE);
                }
            }
        );
        InputHandler mouseHandler = InputHandler.run(()->{this.selected = triplet;});
        shitWidget.setMouseHandler(mouseHandler).setRenderHandler(renderHandler);
        return shitWidget;
    }
//    protected void applyFilter(String filter){
//        if(!Objects.equals(FilterService.currentUserInput, filter)){
//            FilterService.currentUserInput = filter;
//            updateFilterList();
//        }
//    }
    protected void updateFilterList(){
        if(FilterService.currentUserInput == null|| FilterService.currentUserInput.isEmpty()){
            if(filterList != list){
                filterList = list;
                refreshList();
            }
        }else {
            filterList = list.stream()
                .filter(filter)
                .toList();
            refreshList();
        }
    }
    protected void init(){
        int textHeight = Math.min(20, this.entryHeight);
        this.scrollableBorder
            .addDrawableChild(
                FilterService.createFilter(this::updateFilterList, 1, -textHeight +1, dx -2, textHeight-2)
             //   McWidgetHelpers.createTextFieldEditBox( PropertyTracker.event(this::applyFilter), FilterService.currentUserInput)
            );
        this.updateFilterList();
    }
}
