package me.matl114.gui.slimefun;

import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.GridSelectSubScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.*;

public class SlimefunChoiceScreen<T> extends SlimefunScreen {
    final GridSelectSubScreen<T> selectGrid;
    ContentDelegateWidget<GridSelectSubScreen<T>> gridDelegate;
    List<Text> labelTooltips;
    public SlimefunChoiceScreen(Text title, List<T> values, Function<T, DrawableWidget> widgetFunction){
        this(title, null, ()-> values,  widgetFunction);
    }

    public SlimefunChoiceScreen(Text title, List<Text> titleTooltips, Supplier<List<T>> originValue,  Function<T, DrawableWidget> widgetFunction) {
        super(title);
        this.selectGrid = new GridSelectSubScreen<>(
            0,TITLE_OCCUPIED , this.backgroundWidth  ,PAGE_LABEL_HEIGHT, 0, this.backgroundHeight - LABEL_OCCUPIED, -4,16,
            16, 16, originValue,null, widgetFunction
        );
        this.labelTooltips = titleTooltips;
    }


    public SlimefunChoiceScreen<T> setSearchFilter(BiPredicate<String,T> filter){
        this.selectGrid.setFilter(filter);
        return this;
    }

    protected static final List<Text> SEARCH_TOOLTIP = List.of(Text.literal("在下方的输入框输入匹配字符"),Text.literal("点击本按钮用于刷新界面"), Text.literal("正常输入将按名字匹配"), Text.literal("输入空字符串将取消匹配"), Text.literal("输入@按id匹配(如果有id)").formatted(Formatting.GREEN));

    protected List<Text> getSearchButtonTooltips(){
        return this.selectGrid.getFilter() != null ? SEARCH_TOOLTIP : super.getSearchButtonTooltips();
    }


    public void executeFilterTask(){
        this.selectGrid.getFilterTask().accept(FilterService.currentUserInput);
    }

    @Override
    protected List<Text> provideTitleTooltips(DrawableWidget widget) {
        return this.labelTooltips;
    }

    @Override
    protected void init() {
        super.init();

        int availableRenderSpace = this.backgroundHeight - LABEL_OCCUPIED ;
        this.selectGrid.resetGridHeightAndRefresh(availableRenderSpace);
        this.gridDelegate = new ContentDelegateWidget<>(this.x, this.y, 0,0)
            .setContentDelegate(this.selectGrid)
            .addTo(this);
        //Search button
        if(this.selectGrid.getFilter() != null){

            this.searchButton.setMouseHandler(InputHandler.run(this::executeFilterTask));

        }

        ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 12, 26, 26)
            .setMouseHandler(InputHandler.run(this::close))
            .setRenderHandler(PlateElement.instance().combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE,4, 4, 18,18)))
            .addTo(this)
        ;
    }
}
