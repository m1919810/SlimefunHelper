package me.matl114.gui;

import me.matl114.gui.basic.*;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

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


    public PageSwitchSubScreen(int x, int y, int dx, int dy, IntConsumer pageSwitchCallback) {
        super(x, y, dx, dy);
        this.pageSwitchCallback = pageSwitchCallback;
        initPageButton();;
    }
    protected void initPageButton(){
        DisplayWidget.instance( 5, 0, dx - 5 - 5, dy)
            .setRenderHandler(new LabelElement((i)->{
                return Text.literal(this.page + "/" + this.maxPage);
            }, Colors.WHITE,0))
            .addToSub(this);
        ExecutableWidget.instance( 5, 0, dy, dy)
            .setElementHandler( PageButtonElement.prev(this::getMaxPage,this::getPage, this::setPage))
            .addToSub(this);
        ExecutableWidget.instance(dx - 5 - dy, 0, dy, dy)
            .setElementHandler(PageButtonElement.next(this::getMaxPage,this::getPage, this::setPage))
            .addToSub(this);
    }
}
