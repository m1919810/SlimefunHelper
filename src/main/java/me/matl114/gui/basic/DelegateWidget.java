package me.matl114.gui.basic;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import javax.annotation.Nullable;

public class DelegateWidget extends DrawableWidget implements Draggable{
    @Getter
    @Nullable
    DrawableWidget delegate;

    public DelegateWidget setDelegate(DrawableWidget delegate){
        this.delegate = delegate;
        return this;
    }

    public DelegateWidget() {
        super(0, 0, 0, 0);
    }


    //----------------------------------------- values get and set ----------------------------------------------
    public RenderHandler getRenderHandler(){
        return this.delegate == null ? null : this.delegate.getRenderHandler();
    }
    public float getTextureScale(){
        return this.delegate == null?0f:this.delegate.getTextureScale();
    }
    public int getTextureWidth(){
        return this.delegate == null ? 0:this.delegate.getTextureWidth();
    }
    public int getTextureHeight(){
        return this.delegate == null ? 0:this.delegate.getTextureHeight();
    }
    public int getExtraDepth(){
        return this.delegate == null ? 0:this.delegate.getExtraDepth();
    }
    public float getAlpha(){
        return this.delegate == null ? 0:this.delegate.getAlpha();
    }
    public boolean isSubWidget(){
        return this.delegate != null && this.delegate.isSubWidget();
    }
    public boolean isSelected(){
        return this.delegate != null && this.delegate.isSelected();
    }
    public <T extends DrawableWidget> T setExtraDepth(int depth){
        if(this.delegate != null){
            this.delegate.setExtraDepth(depth);
        }
        return (T) this;
    }

    public <T extends DrawableWidget> T setTextureScale(float scale){
        if(this.delegate != null){
            this.delegate.setTextureScale(scale);
        }
        return (T) this;
    }
    public <T extends DrawableWidget> T setAlpha(float scale){
        if(this.delegate != null){
            this.delegate.setAlpha(scale);
        }
        return (T) this;
    }
    public void setSubWidget(boolean s){
        if(this.delegate != null){
            this.delegate.setSubWidget(s);
        }

    }
    public void setSelected(boolean s){
        if(this.delegate != null){
            this.delegate.setSelected(s);
        }

    }

    public <T extends DrawableWidget> T setRenderHandler(RenderHandler renderHandler){
        if(this.delegate != null){
            this.delegate.setRenderHandler(renderHandler);
        }
        return (T) this;
    }


    public boolean onElement(int mouseX, int mouseY){
        return this.delegate != null && this.delegate.onElement(mouseX, mouseY);
    }


    public void render(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        if(this.delegate != null){
            this.delegate.render(context, mouseX, mouseY, delta);
        }
    }

    public void render0(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        if(this.delegate != null){
            this.delegate.render0(context, mouseX, mouseY, delta, disableSelect);
        }
    }
    public void renderInDefaultMatrix(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        //NO
        if(this.delegate != null){
            this.delegate.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        }
    }
    public void renderAbsolute(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        //NO
        if(this.delegate != null){
            this.delegate.renderAbsolute(context, mouseX, mouseY, delta, disableSelect);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.delegate != null && this.delegate.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.delegate != null && this.delegate.mouseReleased(mouseX, mouseY, button);
    }
    public void mouseMoved(double mouseX, double mouseY) {
        //should not move
        if(this.delegate != null){
            this.delegate.mouseMoved(mouseX, mouseY);
        }
    }


    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        //should not drag
        return this.delegate != null && this.delegate.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        //should not scrolled
        return this.delegate != null && this.delegate.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.delegate != null && this.delegate.keyPressed(keyCode, scanCode, modifiers);
    }


    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.delegate != null && this.delegate.keyReleased(keyCode, scanCode, modifiers);
    }


    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.delegate != null && this.delegate.isMouseOver(mouseX, mouseY);
    }


    public void setFocused(boolean focused) {
        if(delegate != null){
            this.delegate.setFocused(focused);
        }
        //Debug.info("This method should not be called!");
    }


    public boolean isFocused() {
        return this.delegate!= null && this.delegate.focused;
    }



    public void setX(int x) {
        if(this.delegate != null){
            this.delegate.setX(x);
        }
    }


    public void setY(int y) {
        if(delegate !=null){
            this.delegate.setY(y);
        }
    }


    public final int getX() {
        return this.delegate == null ? 0: this.delegate.getX();
    }


    public final int getY() {
        return this.delegate == null ? 0: this.delegate.getY();
    }


    public final int getWidth() {
        return this.delegate == null ? 0: this.delegate.getWidth();
    }


    public final int getHeight() {
        return this.delegate == null?0:this.delegate.getHeight();
    }



    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen){
        if(this.delegate != null)
            this.delegate.setSubWidget(true);
        screen.addDrawableChild(this);
        return (T)this;
    }


    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.delegate != null && this.delegate.charTyped(chr, modifiers);
    }



    public SelectionType getType(){
        return this.delegate == null? SelectionType.NONE : this.delegate.getType();
    }


    @Override
    public boolean isDragging() {
        return this.delegate != null &&  this.delegate.isDragging();
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if(delegate != null){
            this.delegate.releaseDrag(screen, mouseX, mouseY);
        }
    }

    @Override
    public  boolean startDrag(Screen screen, double mouseX, double mouseY) {
        return this.delegate != null && delegate.startDrag(screen, mouseX, mouseY);

    }
}
