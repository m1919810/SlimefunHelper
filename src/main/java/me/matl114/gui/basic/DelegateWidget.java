package me.matl114.gui.basic;

import javax.annotation.Nullable;
import lombok.Getter;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.Screen;

public class DelegateWidget extends DrawableWidget implements Draggable {
    @Getter
    @Nullable
    DrawableWidget delegate;

    public DelegateWidget setDelegate(DrawableWidget delegate) {
        this.delegate = delegate;
        return this;
    }

    public DelegateWidget() {
        super(0, 0, 0, 0);
    }

    // ----------------------------------------- values get and set ----------------------------------------------
    public RenderHandler getRenderHandler() {
        return this.delegate == null ? null : this.delegate.getRenderHandler();
    }

    public float getTextureScale() {
        return this.delegate == null ? 0f : this.delegate.getTextureScale();
    }

    public int getTextureWidth() {
        return this.delegate == null ? 0 : this.delegate.getTextureWidth();
    }

    public int getTextureHeight() {
        return this.delegate == null ? 0 : this.delegate.getTextureHeight();
    }

    public int getExtraDepth() {
        return this.delegate == null ? 0 : this.delegate.getExtraDepth();
    }

    public float getAlpha() {
        return this.delegate == null ? 0 : this.delegate.getAlpha();
    }

    public boolean isSubWidget() {
        return this.delegate != null && this.delegate.isSubWidget();
    }

    public boolean isSelected() {
        return this.delegate != null && this.delegate.isSelected();
    }
    // remove the extra Depth here, do not set this
    //    public <T extends DrawableWidget> T setExtraDepth(int depth){
    //        if(this.delegate != null){
    //            this.delegate.setExtraDepth(depth);
    //        }
    //        return (T) this;
    //    }

    public <T extends DrawableWidget> T setTextureScale(float scale) {
        if (this.delegate != null) {
            this.delegate.setTextureScale(scale);
        }
        return (T) this;
    }

    public <T extends DrawableWidget> T setAlpha(float scale) {
        if (this.delegate != null) {
            this.delegate.setAlpha(scale);
        }
        return (T) this;
    }

    public void setSubWidget(boolean s) {
        if (this.delegate != null) {
            this.delegate.setSubWidget(s);
        }
    }

    public void setSelected(boolean s) {
        if (this.delegate != null) {
            this.delegate.setSelected(s);
        }
    }

    public <T extends DrawableWidget> T setRenderHandler(RenderHandler renderHandler) {
        if (this.delegate != null) {
            this.delegate.setRenderHandler(renderHandler);
        }
        return (T) this;
    }

    @Override
    public boolean canSelect() {
        return this.delegate != null && this.delegate.canSelect();
    }

    public void render0(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        if (this.delegate != null) {
            this.delegate.render0(context, mouseX, mouseY, delta, disableSelect);
        }
    }

    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        // NO
        if (this.delegate != null) {
            this.delegate.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        }
    }

    public void renderAbsolute(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        // NO
        if (this.delegate != null) {
            this.delegate.renderAbsolute(context, mouseX, mouseY, delta, disableSelect);
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return this.delegate != null && this.delegate.onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return this.delegate != null && this.delegate.onMouseReleased(mouseX, mouseY, button);
    }

    public void onMouseMoved(double mouseX, double mouseY) {
        // should not move
        if (this.delegate != null) {
            this.delegate.onMouseMoved(mouseX, mouseY);
        }
    }

    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // should not drag
        return this.delegate != null && this.delegate.onMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled
        return this.delegate != null && this.delegate.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return this.delegate != null && this.delegate.onKeyPressed(keyCode, scanCode, modifiers);
    }

    public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        return this.delegate != null && this.delegate.onKeyReleased(keyCode, scanCode, modifiers);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.delegate != null && this.delegate.isMouseOver(mouseX, mouseY);
    }

    public void setFocused(boolean focused) {
        if (delegate != null) {
            this.delegate.setFocused(focused);
        }
        // Debug.info("This method should not be called!");
    }

    public boolean isFocused() {
        return this.delegate != null && this.delegate.focused;
    }

    public void x(int x) {
        if (this.delegate != null) {
            this.delegate.x(x);
        }
    }

    public void y(int y) {
        if (delegate != null) {
            this.delegate.y(y);
        }
    }

    public void width(int x) {
        if (this.delegate != null) {
            this.delegate.width(x);
        }
    }

    public void height(int y) {
        if (delegate != null) {
            this.delegate.height(y);
        }
    }

    public final int x() {
        return this.delegate == null ? 0 : this.delegate.x();
    }

    public final int y() {
        return this.delegate == null ? 0 : this.delegate.y();
    }

    public final int width() {
        return this.delegate == null ? 0 : this.delegate.width();
    }

    public final int height() {
        return this.delegate == null ? 0 : this.delegate.height();
    }

    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen) {
        if (this.delegate != null) this.delegate.setSubWidget(true);
        screen.addDrawableChild(this);
        return (T) this;
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        return this.delegate != null && this.delegate.onCharTyped(chr, modifiers);
    }

    public SelectionType getType() {
        return this.delegate == null ? SelectionType.NONE : this.delegate.getType();
    }

    @Override
    public boolean isDragging() {
        return this.delegate != null && this.delegate.isDragging();
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (delegate != null) {
            this.delegate.releaseDrag(screen, mouseX, mouseY);
        }
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        return this.delegate != null && delegate.startDrag(screen, mouseX, mouseY);
    }
}
