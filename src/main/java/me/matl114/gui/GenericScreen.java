package me.matl114.gui;

import me.matl114.gui.basic.Draggable;
import me.matl114.gui.basic.DrawableWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(button == 0 && draggingElement != null){
            //stop dragging here
            draggingElement.releaseDrag(this, mouseX, mouseY);
            draggingElement = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (var iter: this.children()){
            if(iter instanceof Draggable drag && drag.canDrag(mouseX, mouseY)){
                //start drag this element
                draggingElement = drag;
                drag.startDrag(this, mouseX, mouseY);
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.draggingElement != null && button == 0 && this.draggingElement.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

}
