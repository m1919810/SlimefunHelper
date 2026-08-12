package me.matl114.gui.basic;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import me.matl114.gui.elements.AdvancedScrollElement;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;

public class ScrollableListWidget extends DrawableWidget implements SubSelectable {
    double percentage = 0.0D;
    int maxHeight;
    ExecutableWidget scoll;

    public int getCurrentPose() {
        return percentage == 0.0D ? 0 : (int) (percentage * Math.max(maxHeight - getHeight(), 0));
    }

    @Getter
    protected SubScreenWidget scrollableBorder;

    DrawableWidget selectedElement;
    DrawableWidget draggingElement;

    public ScrollableListWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
        resizeMaxHeight();
        initBorderWidgets();
    }

    protected List<DrawableWidget> widgets = new ArrayList<>();

    private double getPercentage() {
        return this.percentage;
    }

    private void resizeMaxHeight() {
        this.maxHeight = dy;
        for (var widget : widgets) {
            // 由于这是相对位置, 考虑y+dy是这个最深的地方
            int height = widget.getY() + widget.getHeight();
            if (height > this.maxHeight) {
                this.maxHeight = height;
            }
        }
        // 重定向当前位置,保证 curr + dy <= maxHeight
        resizePose(getPercentage());
    }

    private void resizePose(double percentage) {
        this.percentage = MathHelper.clamp(percentage, 0.0D, 1.0D);
    }

    public ScrollableListWidget addScrollingWidget(DrawableWidget widget) {
        Preconditions.checkArgument(!(widget instanceof ScrollableListWidget), "Recursive Scrolling is not supported");
        widgets.add(widget);
        resizeMaxHeight();
        return this;
    }

    public ScrollableListWidget removeScrollingWidget(DrawableWidget widget) {
        widgets.remove(widget);
        resizeMaxHeight();
        return this;
    }

    private void initBorderWidgets() {
        var scroll = new AdvancedScrollElement(
                        ValueAccessor.ofIgnore(this::getHeight),
                        ValueAccessor.ofIgnore(() -> maxHeight),
                        ValueAccessor.of(this::getPercentage, this::resizePose))
                .setBackGround(true);

        this.scoll = new ExecutableWidget(
                        ScrollableListWidget.this.getX() + ScrollableListWidget.this.dx,
                        ScrollableListWidget.this.getY(),
                        12,
                        ScrollableListWidget.this.dy)
                .setElementHandler(scroll);
        this.scrollableBorder = new SubScreenWidget(
                ScrollableListWidget.this.getX(),
                ScrollableListWidget.this.getY(),
                ScrollableListWidget.this.dx,
                ScrollableListWidget.this.dy);
    }

    public ScrollableListWidget clearScrollingWidget() {
        widgets.clear();
        resizeMaxHeight();
        return this;
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
        context.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        // apply current pose
        int currentPose = getCurrentPose();
        context.getMatrices().translate(getX(), getY() - currentPose);
        if (this.priority != 0) {
            context.pushLayer(this.priority);
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
        int currentPose = getCurrentPose();
        int translatedMouseY = mouseY - this.getY() + currentPose;
        boolean selected = false;
        for (var ch : widgets) {
            // 只有接触了这个界面中的子组件需要渲染
            // 通过计算高度限制这个f
            if (ch.getY() + ch.getHeight() > currentPose && ch.getY() < currentPose + this.getHeight()) {
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
        return selectedElement;
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
            int currentPose = getCurrentPose();
            double translatedMouseY = mouseY - this.getY() + currentPose;
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
            int currentPose = getCurrentPose();
            double translatedMouseY = mouseY - this.getY() + currentPose;
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
            this.scoll.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingElement != null) {
            if (this.draggingElement.isDragging()) {
                if (this.draggingElement == this.scoll) {
                    return this.draggingElement.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                }
                int currentPose = getCurrentPose();
                return this.draggingElement.mouseDragged(
                        mouseX - this.getX(), mouseY - this.getY() + currentPose, button, deltaX, deltaY);
            } else {
                return false;
            }

        } else {
            return scrollableBorder.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
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
        return this.draggingElement != null ? this.draggingElement.isDragging() : scrollableBorder.isDragging();
    }

    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (this.draggingElement != null) {
            if (this.draggingElement == this.scoll) {
                this.draggingElement.releaseDrag(screen, mouseX, mouseY);
                this.draggingElement = null;
                return;
            }
            int currentPose = getCurrentPose();
            this.draggingElement.releaseDrag(screen, mouseX - this.getX(), mouseY - this.getY() + currentPose);
            this.draggingElement = null;
        } else {
            scrollableBorder.releaseDrag(screen, mouseX, mouseY);
        }
    }

    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (this.scoll != null && this.scoll.startDrag(screen, mouseX, mouseY)) {
            this.draggingElement = scoll;
            return true;
        }
        if (isMouseOver(mouseX, mouseY)) {
            double translatedMouseX = mouseX - this.getX();
            int currentPose = getCurrentPose();
            double translatedMouseY = mouseY - this.getY() + currentPose;
            for (var ch : widgets) {
                if (ch.startDrag(screen, translatedMouseX, translatedMouseY)) {
                    draggingElement = ch;
                    return true;
                }
            }
        }
        return scrollableBorder.startDrag(screen, mouseX, mouseY);
    }
}
