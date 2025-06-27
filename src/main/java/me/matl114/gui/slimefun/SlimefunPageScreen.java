package me.matl114.gui.slimefun;

import me.matl114.gui.PageSwitchSubScreen;
import me.matl114.gui.basic.*;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public abstract class SlimefunPageScreen extends SlimefunScreen {



    protected final PageSwitchSubScreen pageSwitcher;
    protected ContentDelegateWidget<PageSwitchSubScreen> pageDelegate;
    protected abstract void resetPage();

    protected static final int PAGE_LABEL_HEIGHT = 12;



    public SlimefunPageScreen(Text title) {
        super(title);
        this.pageSwitcher = new PageSwitchSubScreen(0,  TITLE_OCCUPIED, this.backgroundWidth, PAGE_LABEL_HEIGHT, i->this.resetPage());
    }
    protected void initPageButton(){
        this.pageDelegate = new ContentDelegateWidget<>(this.x, this.y, 0,0)
            .setContentDelegate(this.pageSwitcher)
            .addTo(this);
        ;
    }

}
