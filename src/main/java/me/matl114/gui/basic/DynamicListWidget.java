package me.matl114.gui.basic;

import java.util.ArrayList;
import java.util.List;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.Screen;

public class DynamicListWidget extends DrawableWidget implements SubSelectable {
    public DynamicListWidget(int x, int y, int width) {
        super(x, y, width, width);
    }

    private final List<DrawableWidget> children = new ArrayList<>();
    protected DrawableWidget selected = null;
    protected DrawableWidget dragging = null;

    public <T extends SubSelectable> T setSelected(DrawableWidget subWidget) {
        if (selected != null) {
            this.selected.setFocused(false);
        }
        this.selected = subWidget;
        if (this.selected != null && super.isFocused()) {
            this.selected.setFocused(true);
        }
        return (T) this;
    }

    @Override
    public DrawableWidget getSelected() {
        return selected;
    }

    @Override
    public int getHeight() {
        int y = 0;
        for (DrawableWidget child : children) {
            y += child.getHeight();
        }
        return y;
    }

    @Override
    public boolean canSelect() {
        //        for (var ch: children){
        //            if(ch.canSelect())return true;
        //        }
        //        return false;
        return true;
    }

    public void clearChildren() {
        children.clear();
    }

    public DynamicListWidget addDrawableChild(DrawableWidget widget) {
        children.add(widget);
        widget.setSubWidget(true);
        return this;
    }

    public boolean remove(DrawableWidget widget) {
        widget.setSubWidget(false);
        return children.remove(widget);
    }

    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        // handling mouse Coord in render should be scaled? here
        int translatedMouseX = (mouseX - this.getX());

        int translatedMouseY = mouseY - this.getY();
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            translatedMouseX = (int) (translatedMouseX / textureScale);
            translatedMouseY = (int) (translatedMouseY / textureScale);
        }
        DrawableWidget selected = null;
        if (this.isSelected()) {
            // use super.selected as a cache value to show whether there is a child which is selecting
            // it is calculated in render0
            //
            // use interact order to search the first widget that is selectable
            int yLevel = 0;
            for (var ch : children) {
                if (ch.canSelect() && ch.isMouseOver(translatedMouseX, translatedMouseY - yLevel)) {
                    selected = ch;
                    break;
                }
                yLevel += ch.getHeight();
            }
        }
        int yLevel = 0;
        context.pushMatrix();
        for (var ch : children) {
            boolean disable = ch != selected;

            // force disable child highlight, only highlight the first met

            ch.render0(context, translatedMouseX, translatedMouseY - yLevel, delta, disable);
            // dynamic height calculation
            int height = ch.getHeight();
            ;
            yLevel += height;
            if (height != 0) {
                context.getMatrices().translate(0, height);
            }
        }
        context.popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // this should not be scaled because, scale do not change bounding box
        double translatedMouseX = mouseX - this.getX();
        double translatedMouseY = mouseY - this.getY();
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            translatedMouseX = (int) (translatedMouseX / textureScale);
            translatedMouseY = (int) (translatedMouseY / textureScale);
        }
        int yLevel = 0;
        for (var ch : children) {
            if (ch.mouseClicked(translatedMouseX, translatedMouseY - yLevel, button)) {
                setSelected(ch);
                return true;
            }
            yLevel += ch.getHeight();
        }
        setSelected(null);
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {

        double translatedMouseX = mouseX - this.getX();
        double translatedMouseY = mouseY - this.getY();
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            translatedMouseX = (int) (translatedMouseX / textureScale);
            translatedMouseY = (int) (translatedMouseY / textureScale);
        }
        int yLevel = 0;
        for (var ch : children) {
            if (ch.mouseReleased(translatedMouseX, translatedMouseY - yLevel, button)) {
                return true;
            }
            yLevel += ch.getHeight();
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging != null && this.dragging.isDragging()) {
            int yLevel = 0;
            for (var ch : children) {
                if (ch == this.dragging) {
                    break;
                }
                yLevel += ch.getHeight();
            }
            float textureScale = getTextureScale();
            return this.dragging.mouseDragged(
                    (mouseX - this.getX()) / textureScale,
                    ((mouseY - this.getY()) / textureScale) - yLevel,
                    button,
                    deltaX / textureScale,
                    deltaY / textureScale);
        }
        return false;

        //        double translatedMouseX = mouseX - this.getX();
        //        double translatedMouseY = mouseY - this.getY();
        //        if(textureScale != 1.0f){
        //            translatedMouseX = (int) (translatedMouseX / textureScale);
        //            translatedMouseY = (int) (translatedMouseY / textureScale);
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
        double translatedMouseX = mouseX - this.getX();
        double translatedMouseY = mouseY - this.getY();
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            translatedMouseX = (int) (translatedMouseX / textureScale);
            translatedMouseY = (int) (translatedMouseY / textureScale);
        }
        int yLevel = 0;
        for (var ch : children) {
            if (ch.startDrag(screen, translatedMouseX, translatedMouseY - yLevel)) {
                this.dragging = ch;
                return true;
            }
            yLevel += ch.getHeight();
        }
        return false;
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (this.dragging != null) {
            int yLevel = 0;
            for (var ch : children) {
                if (ch == this.dragging) {
                    break;
                }
                yLevel += ch.getHeight();
            }
            double translatedMouseX = mouseX - this.getX();
            double translatedMouseY = mouseY - this.getY();
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            this.dragging.releaseDrag(screen, translatedMouseX, translatedMouseY - yLevel);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled
        double translatedMouseX = mouseX - this.getX();
        double translatedMouseY = mouseY - this.getY();
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            translatedMouseX = (int) (translatedMouseX / textureScale);
            translatedMouseY = (int) (translatedMouseY / textureScale);
        }
        int yLevel = 0;
        for (var ch : children) {
            if (ch.mouseScrolled(translatedMouseX, translatedMouseY - yLevel, horizontalAmount, verticalAmount)) {
                return true;
            }
            yLevel += ch.getHeight();
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (var ch : children) {
            if (ch.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (var ch : children) {
            if (ch.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (var ch : children) {
            if (ch.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int yLevel = 0;
        for (var entry : children) {
            float textureScale = getTextureScale();
            if (entry.isMouseOver(
                    (mouseX - this.getX()) / textureScale, ((mouseY - this.getY()) / textureScale) - yLevel)) {
                return true;
            }
            yLevel += entry.getHeight();
        }
        return false;
    }

    @Override
    public boolean isFocused() {
        return this.selected != null && this.selected.isFocused();
    }

    public void setFocused(boolean val) {
        // save focus state
        super.setFocused(val);
        if (this.selected != null) {
            this.selected.setFocused(val);
        }
    }
}
