package me.matl114.gui.slimefun;

import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.*;

public class SlimefunChoiceScreen<T> extends SlimefunPageScreen {
    List<T> originalValues;
    List<T> values;
    Function<T, DrawableWidget> function;
    ContentDelegateWidget[] pageContents;
    int entryX;
    int entryY;

    BiPredicate<String,T> filter;
   // TextFieldWidget filterWidget;
    DrawableWidget filterWidget;
    List<Text> labelTooltips;
    public SlimefunChoiceScreen(Text title, List<Text> texts, List<T> values, Function<T, DrawableWidget> widgetFunction){
        this(title, texts, values, values, widgetFunction);
    }
    public SlimefunChoiceScreen(Text title, List<T> values, Function<T, DrawableWidget> widgetFunction){
        this(title, null, values, values, widgetFunction);
    }

    protected SlimefunChoiceScreen(Text title, List<Text> titleTooltips, List<T> originValue, List<T> values, Function<T, DrawableWidget> widgetFunction) {
        super(title);
        this.originalValues = originValue;
        this.values = values;
        this.function = widgetFunction;
        this.labelTooltips = titleTooltips;
    }

    public SlimefunChoiceScreen<T> setSearchFilter(BiPredicate<String,T> filter){
        this.filter = filter;
        return this;
    }

    protected static final List<Text> SEARCH_TOOLTIP = List.of(Text.literal("在下方的输入框输入匹配字符"),Text.literal("点击本按钮用于刷新界面"), Text.literal("正常输入将按名字匹配"), Text.literal("输入空字符串将取消匹配"), Text.literal("输入@按id匹配(如果有id)").formatted(Formatting.GREEN));

    protected List<Text> getSearchButtonTooltips(){
        return this.filter != null ? SEARCH_TOOLTIP : super.getSearchButtonTooltips();
    }
    public Consumer<String> getFilterTask(){
        return (str)->{
            FilterService.currentUserInput = str;
            if(initFilter()){
                resetPage();
            }
        };
//            this.filter == null ? (str)->{ currentUserInput = str ;if(this.values != this.originalValues){this.values = originalValues; resetPage();}  }:(str)->{
//            currentUserInput = str;
//            this.values = str == null ? originalValues : originalValues.stream()
//                .filter(t->filter.test(str, t))
//                .toList();
//            this.resetPage();
//        };
    }
    @Override
    protected void resetPage() {
        //reset maxPage when filter or sth reset the page
        this.maxPage = Math.max(1, 1+((values.size() -1) / entryPerPage) );
        this.page = MathHelper.clamp(this.page ,1, this.maxPage);
        int sizeOfEntries = values.size();
        int pageIndex = (this.getPage() - 1)* entryPerPage;
        for (int y0 = 0 ;y0 < entryY; ++y0){
            for (int x0 = 0; x0 < entryX; ++ x0){
                int index = y0 * entryX + x0;
                if(pageContents[index] == null){
                    int startX = (this.backgroundWidth % 16)/2;
                    pageContents[index] = new ContentDelegateWidget(this.x + startX + 16* x0, this.y + LABEL_OCCUPIED + 16*y0, 16, 16).addTo(this);
                }
                ;
                int valueIndex = pageIndex + index;
                if(valueIndex >= sizeOfEntries){
                    pageContents[index].setContentDelegate(null);
                }else {
                    pageContents[index].setContentDelegate(this.function.apply(this.values.get(valueIndex)));
                }
            }
        }

    }
    public void executeFilterTask(){
        getFilterTask().accept(FilterService.currentUserInput);
    }
    public boolean initFilter(){
        if(this.filter != null){
            if(FilterService. currentUserInput == null || FilterService.currentUserInput.isEmpty()){
                if(this.values != this.originalValues){
                    this.values = this.originalValues;
                    return true;
                }
                return false;
            }else {
                this.values = originalValues.stream()
                    .filter(t->filter.test(FilterService. currentUserInput, t))
                    .toList();
                return true;
            }
        }else {
            return false;
        }
    }
    @Override
    protected void init() {
        super.init();
        if(this.labelTooltips != null){
            this.titleWidget.setRenderHandler(((AbstractElement)this.titleWidget.getRenderHandler()).withTooltips(TooltipHandler.of(this.labelTooltips)));
        }
        int availableRenderSpace = this.backgroundHeight - LABEL_OCCUPIED ;
        this.entryX = ((this.backgroundWidth ) / 16) - 1;
        this.entryY = (availableRenderSpace/16) - 1;
        int maxinum_entry =  entryX* entryY;
        this.entryPerPage = Math.max( maxinum_entry,0);
        this.maxPage = Math.max(1, 1+((values.size() -1) / maxinum_entry) );
        this.page = MathHelper.clamp(this.page ,1, this.maxPage);
        initPageButton();
        pageContents = new ContentDelegateWidget[entryPerPage];
        int startX = 8 + (this.backgroundWidth % 16)/2;
        for (int y0 = 0 ;y0 < entryY; ++y0){
            for (int x0 = 0; x0 < entryX; ++ x0){
                int index = y0 * entryX + x0;
                pageContents[index] = new ContentDelegateWidget(this.x + startX + 16* x0, this.y + LABEL_OCCUPIED + 16*y0 + 8, 16, 16).addTo(this);
            }
        }
        //Search button
        if(this.filter != null){
            this.filterWidget = McWidgetHelpers.createTextFieldEditBox(this.x + 5, this.y + this.backgroundHeight -4, this.backgroundWidth - 10,16, PropertyTracker.event(this.getFilterTask()),FilterService.currentUserInput)
                .addTo(this)
            ;
            this.searchButton.setMouseHandler(MouseHandler.run(this::executeFilterTask));
            initFilter();
        }
        resetPage();
        ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 12, 26, 26)
            .setMouseHandler(MouseHandler.run(this::close))
            .setRenderHandler(PlateElement.instance().combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE,4, 4, 18,18)))
            .addTo(this)
        ;

    }
}
