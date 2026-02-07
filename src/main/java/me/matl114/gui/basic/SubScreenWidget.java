package me.matl114.gui.basic;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.Setter;
import me.matl114.utils.Debug;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.collections.UnmodifiableListMappingIterator;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

    private final List<IndexEntry<DrawableWidget>> childrenRender = Lists.newArrayList();
    private final List<IndexEntry<DrawableWidget>> childrenInteract = Lists.newArrayList();
    protected DrawableWidget selected = null;
    protected DrawableWidget dragging = null;

    @Deprecated
    @Setter
    @Getter
    protected int basicDepth = 0;

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
    private int cnt = 0;

    private void resortChildren() {
        childrenRender.sort(Comparator.<IndexEntry<DrawableWidget>>comparingInt(s -> s.val().priority).thenComparingInt(IndexEntry::index));
        childrenInteract.sort(Comparator.<IndexEntry<DrawableWidget>>comparingInt(s -> - s.val().priority).thenComparingInt(IndexEntry::index));
    }

    protected void clearChildren(){
        childrenRender.clear();
        childrenInteract.clear();
    }
    private void addChildrenInternal(DrawableWidget child){
        var re = new IndexEntry<>(++cnt, child);
        childrenRender.add(re);
        childrenInteract.add(re);
        resortChildren();
    }
    private boolean removeChildrenInternal(DrawableWidget child){
        childrenRender.removeIf(s -> Objects.equals(s.val(), child));
        return childrenInteract.removeIf(s -> Objects.equals(s.val(), child));
        // no need to resort!
    }

    protected Iterable<DrawableWidget> childrenRenderOrder() {
        return () -> new UnmodifiableListMappingIterator<>(childrenRender, IndexEntry::val);
    }
    protected Iterable<DrawableWidget> childrenInteractOrder(){
        return ()-> new UnmodifiableListMappingIterator<>(childrenInteract, IndexEntry::val);
    }

    public SubScreenWidget addDrawableChild(DrawableWidget widget){
        addChildrenInternal(widget);
        widget.setSubWidget(true);
        return this;
    }
    public boolean remove(DrawableWidget widget){
        widget.setSubWidget(false);
        return removeChildrenInternal(widget);
    }

    public void renderInDefaultMatrix(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect){
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        //handling mouse Coord in render should be scaled? here
        int translatedMouseX = (mouseX - this.x);

        int translatedMouseY = mouseY - this.y;
        if(this.textureScale != 1.0f){
            translatedMouseX = (int) (translatedMouseX / this.textureScale);
            translatedMouseY = (int) (translatedMouseY / this.textureScale);
        }
        DrawableWidget selected = null;
        if(this.isSelected()){
            //use super.selected as a cache value to show whether there is a child which is selecting
            //it is calculated in render0
            //
            //use interact order to search the first widget that is selectable
            for (var ch : childrenInteractOrder()){
                if(ch.canSelect() && ch.isMouseOver(translatedMouseX, translatedMouseY)){
                    selected = ch;
                    break;
                }
            }
        }

        for (var ch: childrenRenderOrder()){
            boolean disable = ch != selected;

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
        for (var ch : childrenInteractOrder()){
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

        for (var ch : childrenInteractOrder()){
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
        for (var ch: childrenInteractOrder()){
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
        for (var ch : childrenInteractOrder()){
            if(ch.mouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount)){
                return true;
            }
        }
        return false;
    }


    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (var ch : childrenInteractOrder()){
            if(ch.keyPressed(keyCode, scanCode, modifiers)){
                return true;
            }
        }
        return false;
    }


    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (var ch : childrenInteractOrder()){
            if(ch.keyReleased(keyCode, scanCode, modifiers)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (var ch : childrenInteractOrder()){
            if(ch.charTyped(chr, modifiers)){
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        for (var entry: this.childrenInteractOrder()){
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
