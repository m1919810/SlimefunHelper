package me.matl114.gui;

import me.matl114.gui.basic.Draggable;
import me.matl114.gui.basic.DrawableWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.Iterator;

public class GenericScreen extends Screen {
    protected int backgroundWidth ;
    protected int backgroundDefaultHeight;
    protected int backgroundHeight ;
    protected int x;
    protected int y;
    protected Text titleLabel;
    public GenericScreen setTitleLabel(Text text){
        this.titleLabel = text;
        return this;
    }

    public Text getTitleLabel(DrawableWidget widget) {
        return titleLabel;
    }
    protected GenericScreen(Text title, int backgroundWidth, int backgroundDefaultHeight) {
        super(title);
        setTitleLabel(title);
        this.backgroundDefaultHeight = backgroundDefaultHeight;
        this.backgroundWidth = backgroundWidth;
        this.backgroundHeight = backgroundDefaultHeight;
    }
    protected void init0(){
        this.x = (this.width - this.backgroundWidth) / 2;
        if(this.height > this.backgroundDefaultHeight + 24){
            this.backgroundHeight = this.backgroundDefaultHeight;
            this.y = (this.height - this.backgroundHeight) / 2;
        }else {
            this.y = 12;
            this.backgroundHeight = this.height - 24;
        }

    }
    @Override
    protected void init() {
        super.init();
        init0();
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        //call for all children
        Iterator var5 = this.children().iterator();
        Element element;
        do {
            if (!var5.hasNext()) {
                return false;
            }

            element = (Element)var5.next();
            if(element.isMouseOver(mouseX, mouseY) && element.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)){
                return true;
            }
        } while(true);
    }
    protected Draggable draggingElement = null;


    protected boolean doubleClicking = false;


    @Override
    public final boolean mouseReleased(Click click) {
        if(click.button() == 0 && draggingElement != null){
            //stop dragging here
            draggingElement.releaseDrag(this, click.x(), click.y());
            draggingElement = null;
        }
        return super.mouseReleased(click);
    }

    @Override
    public final boolean mouseClicked(Click click, boolean input) {
        //remove the fucking super method
        boolean val = false;
        for (Element element : this.children()) {
            if (element.mouseClicked(click, input)) {
                this.setFocused(element);
                if (click.button() == 0) {
                    this.setDragging(true);
                }

                val = true;
                break;
            }
        }
        if(click.button() == 0){
            for (var iter: this.children()){
                if(iter instanceof Draggable drag && drag.startDrag(this, click.x(), click.y())){
                    //start drag this element
                    draggingElement = drag;
                    break;
                }
            }
        }
        return val;
    }

    @Override
    public final boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return this.draggingElement != null && click.button() == 0 && this.draggingElement.mouseDragged(click, deltaX, deltaY);
    }

    public final boolean keyPressed(KeyInput click) {
        if (super.keyPressed(click)) {
            return true;
            //we mixin the input field of these
            //it will return tru at keyPressed
        } else if (this.client.options.inventoryKey.matchesKey(click)) {
            this.close();
            return true;
        }
        return true;
    }

    public void resetScreen(){
        //schedule refresh
        this.clearAndInit();
        //mc.executeSync(()->this.init(mc,mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()));
    }


}
