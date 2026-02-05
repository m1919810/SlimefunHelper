package me.matl114.gui.presets.lists;

import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.util.Colors;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ListMultiSelectWidget<W> extends ScrollableListWidget {
    Map<W, AttrKeyValue<Boolean>> list;
    int entryHeight;
    List<Map.Entry<W, AttrKeyValue<Boolean>>> filterList;
    BiFunction<W, AttrKeyValue<Boolean>, RenderHandler> renderFactory;
    Predicate<W> filter;
    public Set<W> buildSelected(){
        return list.entrySet().stream()
            .filter(i -> i.getValue().getOriginValue() == Boolean.TRUE)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public ListMultiSelectWidget(List<W> lst, Set<W> currentSelection, BiFunction<W, AttrKeyValue<Boolean>, RenderHandler> renderFactory, Predicate<W> filter, int x, int y, int dx, int dy, int height){
        super(x, y, dx, dy);
        this.list = new LinkedHashMap<>();
        for (var shit : lst){
            this.list.put(shit, AttrKeyValue.bool("是否选中", currentSelection.contains(shit)));
            if(currentSelection.contains(shit)){
                Debug.info("contains", shit);
            }
        }
        this.entryHeight = height;
        this.filter = filter;
        this.renderFactory = renderFactory;
        init();
    }

    protected void refreshList(){
        clearScrollingWidget();
        int size = this.filterList.size();
        for (var i = 0 ;i< size;++i){
            addScrollingWidget(generateEntry(i));

        }
    }
    protected DrawableWidget generateEntry(int index){
        Map.Entry<W, AttrKeyValue<Boolean>> triplet = this.filterList.get(index);
        var attrKeyValue = triplet.getValue();
        ExecutableWidget shitWidget = ExecutableWidget.instance(0, this.entryHeight * index, this.dx, this.entryHeight);
        RenderHandler renderHandler = renderFactory.apply(triplet.getKey(), attrKeyValue);
        renderHandler = renderHandler.combineRender(
            (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                if(attrKeyValue.getOriginValue() == Boolean.TRUE){
                    RenderHandler.drawHighLightBox(context, 0, 0, this.dx, this.entryHeight, Colors.WHITE);
                }
            }
        );
        InputHandler mouseHandler = InputHandler.run(()->{
            if(attrKeyValue.getOriginValue() == Boolean.TRUE){
                attrKeyValue.valueChange(null, "false");
            }else {
                attrKeyValue.valueChange(null, "true");
            }
            //do not resort when value change
            //because player may do it accidentally
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
    protected void updateFilterList(){
        Comparator<Map.Entry<W, AttrKeyValue<Boolean>>> comparator = (o1, o2)->{
            if(o1.getValue().getOriginValue() && !o2.getValue().getOriginValue()){
                return -1;
            }else if(!o1.getValue().getOriginValue() && o2.getValue().getOriginValue()){
                return 1;
            }else {
                return 0;
            }
        };
        filterList = list.entrySet().stream()
            .filter(i -> filter == null || filter.test(i.getKey()))
            .sorted(comparator)
            .toList();
        refreshList();

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
