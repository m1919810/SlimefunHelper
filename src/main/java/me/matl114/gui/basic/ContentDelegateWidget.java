package me.matl114.gui.basic;

import lombok.Getter;
import me.matl114.utils.Debug;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

/**
 * this class handles the delegate content's render and click behaviour, but the position and transformation is applied before the handle's
 */
public class ContentDelegateWidget<W extends Element & Drawable  & Selectable> extends DrawableWidget implements Draggable{

    public ContentDelegateWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }
    // you can put nms widget or sth here, not only DrawableWidget
    @Getter
    protected W delegate;
    public ContentDelegateWidget<W> setContentDelegate(W delegate){
        this.delegate = delegate;
        return this;
    }
    public boolean onElement(int mouseX, int mouseY){
        return this.delegate != null && this.delegate.isMouseOver(mouseX - this.x, mouseY - this.y);
    }
    public void renderInDefaultMatrix(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        if(this.delegate != null){
            int translatedMouseX = (mouseX - this.x);

            int translatedMouseY = mouseY - this.y;
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if(this.delegate instanceof DrawableWidget draw){

                draw.render0(context, translatedMouseX, translatedMouseY, delta, disableSelect);
            }else {
                this.delegate.render(context, translatedMouseX, translatedMouseY, delta);
            }
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if(this.delegate != null ){
            int translatedMouseX = (int) (mouseX - this.x);

            int translatedMouseY = (int) (mouseY - this.y);
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if(this.delegate.mouseClicked(translatedMouseX, translatedMouseY, button)){
                return true;
            }

        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(this.delegate != null ){
            int translatedMouseX = (int) (mouseX - this.x);

            int translatedMouseY = (int) (mouseY - this.y);
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if(this.delegate.mouseReleased(translatedMouseX, translatedMouseY, button)){
                return true;
            }

        }

        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        //should not move
        if(this.delegate != null){
            int translatedMouseX = (int) (mouseX - this.x);

            int translatedMouseY = (int) (mouseY - this.y);
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            this.delegate.mouseMoved(translatedMouseX, translatedMouseY);
        }
    }


    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        //should not drag
        if(this.delegate != null ){
            int translatedMouseX = (int) (mouseX - this.x);

            int translatedMouseY = (int) (mouseY - this.y);
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if(this.delegate.mouseDragged(translatedMouseX, translatedMouseY, button, deltaX * this.textureScale, deltaY * this.textureScale)){
                return true;
            }

        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        //should not scrolled
        if(this.delegate != null ){
            int translatedMouseX = (int) (mouseX - this.x);

            int translatedMouseY = (int) (mouseY - this.y);
            if(this.textureScale != 1.0f){
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if(this.delegate.mouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount)){
                return true;
            }

        }
        return false;
    }


    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.delegate != null && this.delegate.keyPressed(keyCode, scanCode, modifiers);
    }


    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.delegate != null && this.delegate.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.delegate != null && this.delegate.charTyped(chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.delegate != null && this.delegate.isMouseOver((mouseX - this.x)/this.textureScale, (mouseY - this.y)/this.textureScale);
    }

    @Override
    public boolean isFocused() {
        return this.delegate != null && this.delegate.isFocused();
    }

    public void setFocused(boolean focused){
        if(this.delegate != null)this.delegate.setFocused(focused);
    }

    public SelectionType getType(){
        return this.delegate == null? SelectionType.NONE : this.delegate.getType();
    }



    @Override
    public boolean isDragging() {
        return this.delegate != null && this.delegate instanceof Draggable draggable && draggable.isDragging();
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if(delegate instanceof  Draggable draggable){
            draggable.releaseDrag(screen, (mouseX - this.x)/this.textureScale, (mouseY - this.y)/this.textureScale);
        }
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if(delegate instanceof  Draggable draggable){
            return draggable.startDrag(screen, (mouseX - this.x)/this.textureScale, (mouseY - this.y)/this.textureScale);
        }return false;
    }

}
