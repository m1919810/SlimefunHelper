package me.matl114.gui.basic;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import me.matl114.utils.config.PropertyTracker;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;

public class ScrollableExpandWidget extends DrawableWidget implements SubSelectable {
    int currentPose = 0;
    int maxWidth;
    ScrollElement scroll;
    DraggableExecutableWidget scoll;

    @Getter
    protected SubScreenWidget scrollableBorder;

    DrawableWidget selectedElement;
    DrawableWidget draggingElement;

    public ScrollableExpandWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
        resizeMaxHeight();
        initBorderWidgets();
    }

    protected List<DrawableWidget> widgets = new ArrayList<>();

    private double getPercentage() {
        return this.scroll == null ? 0.0d : this.scroll.getPercentage();
    }

    private void resizeMaxHeight() {
        this.maxWidth = dx;
        for (var widget : widgets) {
            // 由于这是相对位置, 考虑y+dy是这个最深的地方
            int height = widget.getX() + widget.getWidth();
            if (height > this.maxWidth) {
                this.maxWidth = height;
            }
        }
        // 重定向当前位置,保证 curr + dy <= maxHeight
        resizePose(getPercentage());
    }

    private void resizePose(double percentage) {
        this.currentPose = MathHelper.clamp((int) (percentage * (this.maxWidth - this.dx)), 0, this.maxWidth - dx);
    }

    public ScrollableExpandWidget addScrollingWidget(DrawableWidget widget) {
        Preconditions.checkArgument(!(widget instanceof ScrollableListWidget), "Recursive Scrolling is not supported");
        widgets.add(widget);
        resizeMaxHeight();
        return this;
    }

    public ScrollableExpandWidget removeScrollingWidget(DrawableWidget widget) {
        widgets.remove(widget);
        resizeMaxHeight();
        return this;
    }

    private void initBorderWidgets() {
        this.scroll = new ScrollElement(
                        PropertyTracker.<ScrollElement, Double>event((v, b) -> {
                            if (hasScroll()) {
                                resizePose(b);
                            } else {
                                resizePose(0.0d);
                                v.setPercentage(0.0d);
                            }
                        }),
                        this::hasScroll)
                .setDraggingY(true);

        this.scoll = new DraggableExecutableWidget(
                        ScrollableExpandWidget.this.getX() + ScrollableExpandWidget.this.dx,
                        ScrollableExpandWidget.this.getY(),
                        (int) ScrollElement.BUTTON_WIDTH,
                        ScrollableExpandWidget.this.dy)
                .setElementHandler(this.scroll);
        this.scrollableBorder = new SubScreenWidget(
                ScrollableExpandWidget.this.getX(),
                ScrollableExpandWidget.this.getY(),
                ScrollableExpandWidget.this.dx,
                ScrollableExpandWidget.this.dy);
    }

    public ScrollableExpandWidget clearScrollingWidget() {
        widgets.clear();
        resizeMaxHeight();
        return this;
    }

    public boolean hasScroll() {
        return this.maxWidth > this.dy;
    }

    @Override
    public boolean canSelect() {
        return true;
    }

    public void render0(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        this.selected = !disableSelect && isMouseOver(mouseX, mouseY);
        if (scoll != null) {
            scoll.render0(context, mouseX, mouseY, delta, disableSelect);
        }
        if (this.scrollableBorder != null) {
            scrollableBorder.render0(context, mouseX, mouseY, delta, disableSelect);
        }

        context.getMatrices().pushMatrix();
        // apply scissors, content outside the template will not be rendered

        context.enableScissor(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight());
        // apply current pose
        context.getMatrices().translate(getX(), getY() - this.currentPose);
        if (this.priority != 0) {
            context.pushLayer(priority);
        }
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            context.getMatrices().scale(textureScale, textureScale);
        }

        renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        context.disableScissor();
        if (this.priority != 0) {
            context.popLayer();
        }
        context.getMatrices().popMatrix();
        renderAbsolute(context, mouseX, mouseY, delta, disableSelect);
    }

    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        // handling mouse Coord in render should be scaled? here
        // add the current pose of the scroll
        int translatedMouseX = (mouseX - this.getX());
        int translatedMouseY = mouseY - this.getY() + currentPose;
        boolean selected = false;
        for (var ch : widgets) {
            // 只有接触了这个界面中的子组件需要渲染
            // 通过计算高度限制这个
            if (ch.getY() + ch.getHeight() > this.currentPose && ch.getY() < this.currentPose + this.dy) {
                // 尝试是否要在这里进行selected计算
                if (this.selected) {
                    boolean disable = true;
                    if (!selected && ch.canSelect() && ch.isMouseOver(translatedMouseX, translatedMouseY)) {
                        disable = false;
                        // select only one in a subScreen
                        selected = true;
                    }
                    // mouse in select, calculate the selected field
                    ch.render0(context, translatedMouseX, translatedMouseY, delta, disable);
                } else {
                    // mouse not select in big part, force set select to false
                    ch.render0(context, translatedMouseX, translatedMouseY, delta, true);
                }
            }
        }
    }

    @Override
    public <T extends SubSelectable> T setSelected(DrawableWidget subWidget) {
        if (this.selectedElement != null) {
            this.selectedElement.setFocused(false);
        }
        this.selectedElement = subWidget;
        if (this.selectedElement != null && super.isFocused()) {
            this.selectedElement.setFocused(true);
        }
        return (T) this;
    }

    @Override
    public DrawableWidget getSelected() {
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // this should not be scaled because, scale do not change bounding box
        //        if(this.scoll != null && this.scoll.mouseClicked(mouseX, mouseY, button)){
        //            return true;
        //        }
        // force check, only if the mouse is on the template can the mouse interact with subwidgets
        if (this.scrollableBorder != null && this.scrollableBorder.mouseClicked(mouseX, mouseY, button)) {
            setSelected(this.scrollableBorder);
            return true;
        }
        if (isMouseOver(mouseX, mouseY)) {
            double translatedMouseX = mouseX - this.getX();
            double translatedMouseY = mouseY - this.getY() + this.currentPose;
            for (var ch : widgets) {
                if (ch.mouseClicked(translatedMouseX, translatedMouseY, button)) {
                    setSelected(ch);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        //        if(this.scoll != null && this.scoll.mouseClicked(mouseX, mouseY, button)){
        //            return true;
        //        }
        if (this.scrollableBorder != null && this.scrollableBorder.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        // force check, only if the mouse is on the template can the mouse interact with subwidgets
        if (isMouseOver(mouseX, mouseY)) {
            double translatedMouseX = mouseX - this.getX();
            double translatedMouseY = mouseY - this.getY() + this.currentPose;
            for (var ch : widgets) {
                if (ch.mouseReleased(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);

        if (this.isMouseOver(mouseX, mouseY) || (this.scoll != null && this.scoll.isMouseOver(mouseX, mouseY))) {
            double per = (verticalAmount * this.maxWidth / 100d) / this.maxWidth;
            if (this.scroll != null) {
                scroll.setPercentage(scroll.getPercentage() - per);
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingElement != null && this.draggingElement.isDragging()) {
            if (this.draggingElement == this.scoll) {
                return this.draggingElement.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
            return this.draggingElement.mouseDragged(
                    mouseX - this.getX(), mouseY - this.getY() + this.currentPose, button, deltaX, deltaY);
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.scrollableBorder != null && this.scrollableBorder.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        for (var ch : widgets) {
            if (ch.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.scrollableBorder != null && this.scrollableBorder.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        for (var ch : widgets) {
            if (ch.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.scrollableBorder != null && this.scrollableBorder.charTyped(chr, modifiers)) {
            return true;
        }
        for (var ch : widgets) {
            if (ch.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public <T extends DrawableWidget> T addTo(Screen screen) {
        //        if(this.scoll != null)
        //            ScreenAccess.of(screen).addDrawableChildTo(this.scoll);

        return super.addTo(screen);
    }

    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen) {
        //        if(this.scoll != null)
        //            screen.addDrawableChild(this.scoll);
        return super.addToSub(screen);
    }

    @Override
    public boolean isFocused() {
        return this.selectedElement != null && this.selectedElement.isFocused();
    }

    public void setFocused(boolean val) {
        // save focus state
        super.setFocused(val);
        if (this.selectedElement != null) {
            this.selectedElement.setFocused(val);
        }
    }
    // delegate scoll 's drag

    public boolean isDragging() {
        return this.draggingElement != null && this.draggingElement.isDragging();
    }

    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (this.draggingElement != null) {
            if (this.draggingElement == this.scoll) {
                this.draggingElement.releaseDrag(screen, mouseX, mouseY);
                return;
            }
            this.draggingElement.releaseDrag(screen, mouseX - this.getX(), mouseY - this.getY() + this.currentPose);
            this.draggingElement = null;
        }
    }

    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (this.scoll != null && this.scoll.startDrag(screen, mouseX, mouseY)) {
            this.draggingElement = scoll;
            return true;
        }
        if (isMouseOver(mouseX, mouseY)) {
            double translatedMouseX = mouseX - this.getX();
            double translatedMouseY = mouseY - this.getY() + this.currentPose;
            for (var ch : widgets) {
                if (ch.startDrag(screen, translatedMouseX, translatedMouseY)) {
                    draggingElement = ch;
                    return true;
                }
            }
        }
        return false;
    }
}
