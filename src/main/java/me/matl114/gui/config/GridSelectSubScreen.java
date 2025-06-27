package me.matl114.gui.config;

import lombok.Getter;
import me.matl114.gui.FilterService;
import me.matl114.gui.GridSubScreen;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.PageSwitchSubScreen;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class GridSelectSubScreen<R> extends SubScreenWidget {
    final PageSwitchSubScreen pageSwitcher;
    GridSubScreen<DrawableWidget> gridSubScreen;
    final ContentDelegateWidget<TextFieldWidget> textFieldWidget;
    final TextFieldWidget delegate;
    @Getter
    BiPredicate<String, R> filter;
    List<R> values;
    List<R> originalValues;
    final Function<R, DrawableWidget> function;
    final int baseHeight;
    final int filterHeight;
    final int filterDistance;
    final int elementX;
    final int elementY;
    public GridSelectSubScreen(int x, int y, int dx, int pageHeight, int page2Grid, int gridHeight, int grid2Filter, int filterHeight, int elementX, int elementY, List<R> origins, BiPredicate<String, R> filter, Function<R, DrawableWidget> function) {
        super(x, y, dx, 0);

        this.baseHeight = pageHeight + page2Grid;
        this.elementX = elementX;
        this.elementY = elementY;
        this.filterDistance = grid2Filter;
        this.filterHeight = filterHeight;
        this.originalValues = origins;
        this.function = function;
        this.pageSwitcher = new PageSwitchSubScreen(
            0,0, dx, pageHeight,(i)->resetPage()
        ).addToSub(this);
        this.textFieldWidget = McWidgetHelpers.createTextFieldEditBox(
            5, this.baseHeight + gridHeight + this.filterDistance,dx - 10, this.filterHeight, PropertyTracker.event(this.getFilterTask()), FilterService.currentUserInput
        ).addToSub(this);
        this.delegate = this.textFieldWidget.getDelegate();
        setFilter(filter);
        resetHeight(pageHeight + page2Grid + gridHeight + grid2Filter + filterHeight);

    }
    public void setFilter(BiPredicate<String,R> filter){
        this.filter = filter;
        if(this.filter != null){
            this.textFieldWidget.setContentDelegate(this.delegate);
        }else {
            this.textFieldWidget.setContentDelegate(null);
        }
    }

    public void resetHeight(int newHeight){
        if(newHeight != dy){
            dy = newHeight;
            int gridHeight = newHeight - baseHeight - this.filterDistance - this.filterHeight;
            this.textFieldWidget.setY(newHeight - this.filterHeight);
            if(this.gridSubScreen != null)this.remove(this.gridSubScreen);
            this.gridSubScreen = new GridSubScreen<DrawableWidget>(
                0, this.baseHeight, dx, gridHeight, elementX, elementY
            ).addToSub(this);
            initFilter();
            resetPage();
        }
    }
    public void resetGridHeight(int gridHeight){
        resetHeight(gridHeight + baseHeight + this.filterDistance + this.filterHeight);
    }


    protected void resetPage() {
        //reset maxPage when filter or sth reset the page
        this.pageSwitcher.updateMaxPage(Math.max(1, 1+((this.values.size() -1) / this.gridSubScreen.getEntryPerPage()) ));
        int  page = this.pageSwitcher.getPage();//  MathHelper.clamp(this.page ,1, this.maxPage);
        this.gridSubScreen.refreshPage(this.values, this.function, page);
    }
    public boolean initFilter(){
        //fixme: input is null does not means escape filter
        if(this.filter != null && (FilterService. currentUserInput != null && !FilterService. currentUserInput.isEmpty())){
            this.values = originalValues.stream()
                .filter(t->filter.test(FilterService. currentUserInput, t))
                .toList();
            return true;
        }else {
            if(this.values != this.originalValues){
                this.values = this.originalValues;
                return true;
            }
            return false;
        }
    }
    public Consumer<String> getFilterTask(){
        return (str)->{
            FilterService.currentUserInput = str;
            refresh();
        };

    }
    public void refresh(){
        if(initFilter()){
            resetPage();
        }
    }

}
