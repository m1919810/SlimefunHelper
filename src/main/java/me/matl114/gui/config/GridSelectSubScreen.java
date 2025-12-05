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
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

public class GridSelectSubScreen<R> extends SubScreenWidget {
    final PageSwitchSubScreen pageSwitcher;
    GridSubScreen<DrawableWidget> gridSubScreen;
    final ContentDelegateWidget<SubScreenWidget> textFieldWidget;
    final SubScreenWidget delegate;
    @Getter
    BiPredicate<String, R> filter;
    List<R> values;
    Supplier<List<R>> originalValues;
    final Function<R, DrawableWidget> function;
    final int baseHeight;
    final int filterHeight;
    final int filterDistance;
    final int elementX;
    final int elementY;
    public GridSelectSubScreen(int x, int y, int dx, int pageHeight, int page2Grid, int gridHeight, int grid2Filter, int filterHeight, int elementX, int elementY, Supplier<List<R>> origins, BiPredicate<String, R> filter, Function<R, DrawableWidget> function) {
        super(x, y, dx, 0);

        this.baseHeight = pageHeight + page2Grid;
        this.elementX = elementX;
        this.elementY = elementY;
        this.filterDistance = grid2Filter;
        this.filterHeight = filterHeight;
        this.originalValues = origins;
        this.function = function;
        this.pageSwitcher = new PageSwitchSubScreen(
            0,0, dx, pageHeight, 100,(i)->resetPage()
        ).addToSub(this);
        this.delegate = FilterService.createFilter(()->this.getFilterTask().accept(FilterService.currentUserInput), 5, 0,dx - 10, this.filterHeight);
//            McWidgetHelpers.createTextFieldEditBox(
//            , PropertyTracker.event(this.getFilterTask()), FilterService.currentUserInput
//        );//.addToSub(this);
        this.textFieldWidget =  new ContentDelegateWidget(0,0,0,0)
            .setContentDelegate(this.delegate); //this.textFieldWidget.getDelegate();
        this.textFieldWidget
            .addToSub(this);
        setFilter(filter);

        resetHeight(pageHeight + page2Grid + gridHeight + grid2Filter + filterHeight);

    }
    public void setFilter(BiPredicate<String,R> filter){
        var origin = this.filter;
        this.filter = filter;
        if(this.filter != null){
            this.textFieldWidget.setContentDelegate(this.delegate);
        }else {
            this.textFieldWidget.setContentDelegate(null);
        }
        //refresh filter
        if(origin != filter)
            initFilter();
    }

    public boolean resetHeight(int newHeight){
        if(newHeight != dy){
            dy = newHeight;
            int gridHeight = newHeight - baseHeight - this.filterDistance - this.filterHeight;
            this.textFieldWidget.setY(newHeight - this.filterHeight);
            if(this.gridSubScreen != null)this.remove(this.gridSubScreen);
            this.gridSubScreen = new GridSubScreen<DrawableWidget>(
                0, this.baseHeight, dx, gridHeight, elementX, elementY
            ).addToSub(this);
            CompletableFuture.supplyAsync(this::initFilter).thenRun(this::resetPage);
            return true;
        }
        return false;
    }
    public void resetGridHeightAndRefresh(int gridHeight){
        if(!resetGridHeight(gridHeight)){
            refresh();
        }
    }
    public boolean resetGridHeight(int gridHeight){
        return resetHeight(gridHeight + baseHeight + this.filterDistance + this.filterHeight);
    }


    protected synchronized void resetPage() {
        //reset maxPage when filter or sth reset the page
        this.pageSwitcher.updateMaxPage(Math.max(1, 1+((this.values.size() -1) / this.gridSubScreen.getEntryPerPage()) ));
        int  page = this.pageSwitcher.getPage();//  MathHelper.clamp(this.page ,1, this.maxPage);
        this.gridSubScreen.refreshPage(this.values, this.function, page);
    }
    public synchronized boolean initFilter(){
        // input is null does not means escape filter
        var filter = this.filter;
        if(this.filter != null){
            values = originalValues.get().stream()
                .filter(t->filter.test(FilterService. currentUserInput, t))
                .toList();
            return true;
        }else {
            var list =  this.originalValues.get();
            if(this.values != list){
                this.values = list;
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
        CompletableFuture.supplyAsync(this::initFilter)
            .thenAccept(i->{
            if (i){
                resetPage();
            }
        });
    }

}
