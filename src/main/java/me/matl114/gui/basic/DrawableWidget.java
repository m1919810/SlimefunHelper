package me.matl114.gui.basic;

import me.matl114.access.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Environment(EnvType.CLIENT)
public abstract class DrawableWidget implements Element,Drawable, net.minecraft.client.gui.widget.Widget, Selectable ,Draggable{
    public DrawableWidget(int x, int y, int dx, int dy){
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        updateScale();
    }

    //do not read other's value, because of delegate
    protected int x;
    protected int y;

    protected int dx;
    protected int dy;

    private int textureWidth;
    private int textureHeight;
    protected int extraDepth;
    private final void updateScale(){
        this.textureWidth =(int)( dx/ this.textureScale);
        this.textureHeight = (int)( dy/this.textureScale);
    }

    protected float textureScale = 1.0f;
    private float alpha = 1.0f;
    private boolean subWidget = false;
    private  RenderHandler renderHandler;
    protected boolean selected;
    protected boolean focused;
    public RenderHandler getRenderHandler(){
        return this.renderHandler;
    }

    public float getTextureScale(){
        return textureScale;
    }
    public int getTextureWidth(){
        return this.textureWidth;
    }
    public int getTextureHeight(){
        return this.textureHeight;
    }
    public int getExtraDepth(){
        return this.extraDepth;
    }
    public float getAlpha(){
        return this.alpha;
    }
    public boolean isSubWidget(){
        return subWidget;
    }
    public boolean isSelected(){
        return selected;
    }
    public <T extends DrawableWidget> T setExtraDepth(int depth){
        this.extraDepth = depth;
        return (T)this;
    }

    public <T extends DrawableWidget> T setTextureScale(float scale){
        this.textureScale = scale;
        updateScale();
        return (T)this;
    }
    public <T extends DrawableWidget> T setAlpha(float scale){
        this.alpha = scale;
        return (T)this;
    }
    public void setSubWidget(boolean s){
        this.subWidget = s;
    }
    public void setSelected(boolean s){
        this.subWidget = s;
    }

    public <T extends DrawableWidget> T setRenderHandler(RenderHandler renderHandler){
        this.renderHandler = renderHandler;
        return (T)this;
    }
    public <T extends DrawableWidget> T updateRenderHandler(UnaryOperator<RenderHandler> updater){
        this.renderHandler = updater.apply(this.renderHandler);
        return (T)this;
    }





    public boolean onElement(int mouseX, int mouseY){
        return  mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.textureWidth && mouseY < this.getY() + this.textureHeight;
    }

    /**
     * override only for delegate!
     * @param context
     * @param mouseX
     * @param mouseY
     * @param delta
     */
    public final void render(DrawContext context, int mouseX, int mouseY, float delta){
        render0(context, mouseX, mouseY, delta, false);
    }
    protected void checkSelect(boolean disableSelect, int mouseX, int mouseY){
        this.selected = !disableSelect && onElement(mouseX, mouseY);
    }
    public void render0(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        this.selected = !disableSelect && onElement(mouseX, mouseY);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, extraDepth);
        if(textureScale != 1.0f){
            context.getMatrices().push();
            context.getMatrices().scale(textureScale, textureScale, 1);
        }
        renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        if(textureScale != 1.0f){
            context.getMatrices().pop();
        }
        context.getMatrices().pop();
        renderAbsolute(context, mouseX, mouseY, delta, disableSelect);
    }
    public void renderInDefaultMatrix(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        if(this.renderHandler != null){
            this.renderHandler.renderAtCentered(this, context, mouseX, mouseY , delta, this.alpha, this.selected);
        }
    }
    public void renderAbsolute(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        if(this.renderHandler != null){
            this.renderHandler.renderExtraAbsoluteCoord(this, context, mouseX, mouseY , delta, this.alpha, this.selected);
            context.tryDraw();
        }
    }




    public abstract boolean mouseClicked(double mouseX, double mouseY, int button) ;


    public abstract boolean mouseReleased(double mouseX, double mouseY, int button);


    public boolean isMouseOver(double mouseX, double mouseY) {
        return onElement((int) mouseX, (int) mouseY);
    }


    public void setFocused(boolean focused) {
        this.focused = focused;
        //Debug.info("This method should not be called!");
    }


    public boolean isFocused() {
        return focused;
    }



    public void setX(int x) {
        this.x = x;
    }


    public void setY(int y) {
        this.y = y;
    }


    public int getX() {
        return this.x;
    }


    public int getY() {
        return this.y;
    }


    public int getWidth() {
        return dx;
    }


    public int getHeight() {
        return dy;
    }

    public <T extends DrawableWidget> T cast(){
        return (T)this;
    }
    public <T extends DrawableWidget> T addTo(Screen screen){
        ScreenAccess.of(screen).addDrawableChildTo(this);
        return (T)this;
    }
    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen){
        this.subWidget = true;
        screen.addDrawableChild(this);
        return (T)this;
    }





    public void mouseMoved(double mouseX, double mouseY) {
        //should not move
    }


    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        //should not drag
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        //should not scrolled

        return false;
    }


    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }


    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    //------------------------------------- default functions left for -------------------------------------


    public final void forEachChild(Consumer<ClickableWidget> consumer) {

    }


    public Selectable.SelectionType getType() {
        return this.selected ? Selectable.SelectionType.HOVERED : Selectable.SelectionType.NONE;
    }


    public final void appendNarrations(NarrationMessageBuilder builder) {

    }

    @Nullable
    public final GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }

    @Nullable
    public final GuiNavigationPath getFocusedPath() {
        return null;
    }

    public final ScreenRect getNavigationFocus() {
        return ScreenRect.empty();
    }

    public boolean canDrag(double mouseX, double mouseY){
        return false;
    }

    public boolean isDragging(){
        return false;
    }

    public void releaseDrag(Screen screen, double mouseX, double mouseY){

    }


    public void startDrag(Screen screen, double mouseX, double mouseY){

    }

}
