package me.matl114.gui.slimefun;

import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.LabelElement;
import me.matl114.gui.basic.PageButtonElement;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public abstract class SlimefunPageScreen extends SlimefunScreen {

    protected int maxPage = 1;

    protected int page = 1;
    protected int entryPerPage;
    public int getMaxPage(){
        return this.maxPage;
    }

    public int getPage(){
        return this.page;
    }

    public void setPage(int page){

        this.page = MathHelper.clamp(page,1,maxPage);
        resetPage();
    }
    protected abstract void resetPage();

    protected static final int PAGE_LABEL_HEIGHT = 12;

    public SlimefunPageScreen(Text title) {
        super(title);
    }
    protected static final int LABEL_OCCUPIED = TITLE_OCCUPIED + PAGE_LABEL_HEIGHT;
    protected void initPageButton(){
        DisplayWidget.instance(this.x + 5, this.y + TITLE_OCCUPIED, this.backgroundWidth - 5 - 5, PAGE_LABEL_HEIGHT)
            .setRenderHandler(new LabelElement((i)->{
                return Text.literal(this.page + "/" + this.maxPage);
            }, Colors.WHITE,0))
            .addTo(this);
        ExecutableWidget.instance(this.x + 5, this.y + TITLE_OCCUPIED, PAGE_LABEL_HEIGHT, PAGE_LABEL_HEIGHT)
            .setElementHandler( PageButtonElement.prev(this::getMaxPage,this::getPage, this::setPage))
            .addTo(this);
        ExecutableWidget.instance(this.x + this.backgroundWidth - 5 - TITLE_LABEL_HEIGHT, this.y + TITLE_OCCUPIED, PAGE_LABEL_HEIGHT, PAGE_LABEL_HEIGHT)
            .setElementHandler(PageButtonElement.next(this::getMaxPage,this::getPage, this::setPage))
            .addTo(this);
    }

}
