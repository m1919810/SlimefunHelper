package me.matl114.gui.basic;

import me.matl114.utils.Debug;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class SubScreenWidget extends DrawableWidget implements SubSelectable{
    /**
     * this is a subscreen , children will be placed in the coordinate where SubScreen lies at 0,0 with its scaler
     * @param x
     * @param y
     * @param dx
     * @param dy
     */
    public static SubScreenWidget instance(int x, int y, int dx, int dy){
        return new SubScreenWidget(x,y, dx, dy);
    }
    public SubScreenWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }

    @Override
    public <T extends DrawableWidget> T setTextureScale(float scale) {
        //we should deal with it, somehow, because scaler changed the coord
        return super.setTextureScale(scale);
    }

    protected List<DrawableWidget> children = Lists.newArrayList();
    protected DrawableWidget selected = null;
    protected DrawableWidget dragging = null;

    public <T extends SubSelectable> T setSelected(DrawableWidget subWidget){
        if(selected != null){
            this.selected.setFocused(false);
        }
        this.selected = subWidget;
        if(super.isFocused()){
            this.selected.setFocused(true);
        }
        return (T) this;
    }

    @Override
    public DrawableWidget getSelected() {
        return selected;
    }

    @Override
    public boolean canSelect() {
//        for (var ch: children){
//            if(ch.canSelect())return true;
//        }
//        return false;
        return true;
    }

    public SubScreenWidget addDrawableChild(DrawableWidget widget){
        children.add(widget);
        widget.setSubWidget(true);
        return this;
    }
    public boolean remove(DrawableWidget widget){
        widget.setSubWidget(false);
        return children.remove(widget);
    }

    public void renderInDefaultMatrix(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        //handling mouse Coord in render should be scaled? here
        int translatedMouseX = (mouseX - this.x);

        int translatedMouseY = mouseY - this.y;
        if(this.textureScale != 1.0f){
            translatedMouseX = (int) (translatedMouseX / this.textureScale);
            translatedMouseY = (int) (translatedMouseY / this.textureScale);
        }
        boolean selected = false;
        for (var ch: children){
            boolean disable = true;
            //use super.selected as a cache value to show whether there is a child which is selecting
            //it is calculated in render0
            if(this.isSelected() && !selected && ch.canSelect() && ch.isMouseOver(translatedMouseX, translatedMouseY)){
                disable = false;
                //select only one in a subScreen
                selected = true;
            }
            //force disable child highlight, only highlight the first met
            ch.render0(context, translatedMouseX, translatedMouseY, delta, disable);
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //this should not be scaled because, scale do not change bounding box
        double translatedMouseX = mouseX - this.x;
        double translatedMouseY = mouseY - this.y;
        if(this.textureScale != 1.0f){
            translatedMouseX = (int) (translatedMouseX / this.textureScale);
            translatedMouseY = (int) (translatedMouseY / this.textureScale);
        }
        for (var ch : children){
            if(ch.mouseClicked(translatedMouseX, translatedMouseY, button)){
                setSelected(ch);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {

        double translatedMouseX = mouseX - this.x;
        double translatedMouseY = mouseY - this.y;
        if(this.textureScale != 1.0f){
            translatedMouseX = (int) (translatedMouseX / this.textureScale);
            translatedMouseY = (int) (translatedMouseY / this.textureScale);
        }

        for (var ch : children){
            if(ch.mouseReleased(translatedMouseX, translatedMouseY, button)){
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY){
        return this.dragging != null && this.dragging.isDragging() && this.dragging.mouseDragged((mouseX - this.x)/ this.textureScale, (mouseY - this.y)/this.textureScale,button, deltaX/this.textureScale, deltaY/this.textureScale);
//        double translatedMouseX = mouseX - this.x;
//        double translatedMouseY = mouseY - this.y;
//        if(this.textureScale != 1.0f){
//            translatedMouseX = (int) (translatedMouseX / this.textureScale);
//            translatedMouseY = (int) (translatedMouseY / this.textureScale);
//        }
//        for (var ch : children){
//            if(ch.mouseDragged(translatedMouseX, translatedMouseY, button, deltaX, deltaY)){
//                return true;
//            }
//        }
//        return false;
    }

    @Override
    public boolean isDragging() {
        return this.dragging != null && this.dragging.isDragging();
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        //todo how?
        double translatedMouseX = mouseX - this.x;
        double translatedMouseY = mouseY - this.y;
        if(this.textureScale != 1.0f){
            translatedMouseX = (int) (translatedMouseX / this.textureScale);
            translatedMouseY = (int) (translatedMouseY / this.textureScale);
        }
        for (var ch: children){
            if(ch.startDrag(screen, translatedMouseX, translatedMouseY)){
                this.dragging = ch;
                return true;
            }
        }
        return false;
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if(this.dragging != null){
            double translatedMouseX = mouseX - this.x;
            double translatedMouseY = mouseY - this.y;
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            this.dragging.releaseDrag(screen, translatedMouseX, translatedMouseY);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        //should not scrolled
        double translatedMouseX = mouseX - this.x;
        double translatedMouseY = mouseY - this.y;
        if(this.textureScale != 1.0f){
            translatedMouseX = (int) (translatedMouseX / this.textureScale);
            translatedMouseY = (int) (translatedMouseY / this.textureScale);
        }
        for (var ch : children){
            if(ch.mouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount)){
                return true;
            }
        }
        return false;
    }


    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (var ch : children){
            if(ch.keyPressed(keyCode, scanCode, modifiers)){
                return true;
            }
        }
        return false;
    }


    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (var ch : children){
            if(ch.keyReleased(keyCode, scanCode, modifiers)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (var ch : children){
            if(ch.charTyped(chr, modifiers)){
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        for (var entry: this.children){
            if(entry.isMouseOver((mouseX - this.x)/this.textureScale, (mouseY - this.y)/this.textureScale))return true;
        }
        return false;
    }

    @Override
    public boolean isFocused() {
        return this.selected != null && this.selected.isFocused();
    }

    public void setFocused(boolean val){
        //save focus state
        super.setFocused(val);
        if(this.selected != null){
            this.selected.setFocused(val);
        }
    }
}
