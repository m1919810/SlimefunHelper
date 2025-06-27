package me.matl114.gui;

import me.matl114.gui.basic.*;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

import java.util.List;

public class GenericBackGroundScreen extends GenericScreen{
    protected GenericBackGroundScreen(Text title, int backgroundWidth, int backgroundDefaultHeight) {
        super(title, backgroundWidth, backgroundDefaultHeight);
    }
    protected static final int TITLE_LABEL_HEIGHT = 12;
    protected static final int TITLE_OCCUPIED = 20;
    protected static final int PAGE_LABEL_HEIGHT = 12;
    protected static final int LABEL_OCCUPIED = TITLE_OCCUPIED + PAGE_LABEL_HEIGHT;
    protected DrawableWidget background;
    protected DrawableWidget titleWidget;
    protected List<Text> provideTitleTooltips(DrawableWidget widget){
        return null;
    }
    protected void runClickTitle(boolean isLeft){

    }
    protected void initBackground(){
        this.background = DisplayWidget.instance(this.x, this.y, this.backgroundWidth, this.backgroundHeight)
            .setRenderHandler(PlateElement.instance())
            .addTo(this)
        ;
        this.titleWidget = ExecutableWidget.instance(this.x + 5, this.y + 5, this.backgroundWidth - 10, TITLE_LABEL_HEIGHT)
            .setElementHandler(
                new LabelElement(this::getTitleLabel, Colors.WHITE, 0)
                    .withMouseHandler(MouseHandler.isLeft(this::runClickTitle))
                    .withTooltips(TooltipHandler.of(this::provideTitleTooltips))
            )
            .addTo(this)
        ;
    }
    protected void init(){
        super.init();;
        initBackground();
    }
}
