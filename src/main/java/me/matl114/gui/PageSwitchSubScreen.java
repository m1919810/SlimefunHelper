package me.matl114.gui;

import me.matl114.gui.basic.*;
import me.matl114.gui.presets.single.IntFastInputWidget;
import me.matl114.utils.UtilClass.AttrKeyValue;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.IntConsumer;

public class PageSwitchSubScreen  extends SubScreenWidget {
    protected int maxPage = 1;

    protected int page = 1;
    public final int getMaxPage(){
        return this.maxPage;
    }
    public final void updateMaxPage(int val){
        this.maxPage = val;
        this.page = MathHelper.clamp(this.page, 1, maxPage);
    }
//
//    public final void updateMaxPage(int page, int maxPage){
//
//    }
    public final void updatePage(int page){
        this.page = MathHelper.clamp(page, 1, maxPage);
    }


    public int getPage(){
        return this.page;
    }

    protected void setPage(int page){

        this.page = MathHelper.clamp(page,1,maxPage);
        this.pageSwitchCallback.accept(this.page);
    }

    protected IntConsumer pageSwitchCallback;
    private int pageHeight;
    // add scroll on top to switch page finished
    // add shift click to fastinput page finished
    private ContentDelegateWidget<IntFastInputWidget> hovering;
    private static List<Text> TOOLTIPS_PAGE_LABEL = List.of(
        Text.literal("使用滚轮以前后切换页数"),
        Text.literal("点击以输入快捷切换的页数")
    );

    public PageSwitchSubScreen(int x, int y, int dx, int dy, int pageHeight, IntConsumer pageSwitchCallback) {
        super(x, y, dx, dy);
        this.pageSwitchCallback = pageSwitchCallback;
        this.pageHeight = pageHeight;
        initPageButton();;
    }
    protected void hoverInputPageWidget(){
        AttrKeyValue<Integer> clampedValue = AttrKeyValue.clampedInt("输入页数", this.page, 1, this.maxPage);
        hovering.setContentDelegate(IntFastInputWidget.instance(clampedValue, this::hoverInputCallback,
            (dx - 96)/2 , (this.pageHeight - 30)/2 ,96, 30, 64
            )
                .setFinishRunning(()->this.hovering.setContentDelegate(null))
                .setExtraDepth(500)
        );
    }
    protected void hoverInputCallback(AttrKeyValue<Integer> val){
        hovering.setContentDelegate(null);
        setPage(val.getOriginValue());
    }
    protected void initPageButton(){

        ExecutableWidget.instance( 5 + dy + 1, 0, dx - 5 - 5 - 2 - 2* dy, dy)
            .setElementHandler(
                new AbstractElement()
                    .combineRender(new LabelElement((i)->{
                        return Text.literal(this.page + "/" + this.maxPage);
                    }, Colors.WHITE,0))
                    .withInputHandler(InputHandler.scroller((w, a)-> {setPage(getPage() + (a > 0 ? -1 : 1));return true;}))
                    .withInputHandler(InputHandler.clickRun(this::hoverInputPageWidget))
                    .withTooltips(TooltipHandler.of(TOOLTIPS_PAGE_LABEL))
            )
            .addToSub(this);
        ExecutableWidget.instance( 5, 0, dy, dy)
            .setElementHandler( PageButtonElement.prev(this::getMaxPage,this::getPage, this::setPage))
            .addToSub(this);
        ExecutableWidget.instance(dx - 5 - dy, 0, dy, dy)
            .setElementHandler(PageButtonElement.next(this::getMaxPage,this::getPage, this::setPage))
            .addToSub(this);
        //应该在当前组件中的优先级最低
        hovering = new ContentDelegateWidget<>(0,0, dx, dy)
            .addToSub(this)
        ;
    }

}
