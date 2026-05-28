package me.matl114.gui.elements;

import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.*;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.util.Colors;

@Accessors(chain = true)
public class AdvancedScrollElement extends AbstractElement {
    ValueAccessor<Integer> screenY;
    ValueAccessor<Integer> allY;
    ValueAccessor<Double> percentage;

    @Setter
    boolean backGround = false;

    public AdvancedScrollElement(
            ValueAccessor<Integer> screenY, ValueAccessor<Integer> allY, ValueAccessor<Double> percentage) {
        this.screenY = screenY;
        this.allY = allY;
        this.percentage = percentage;
    }

    public double getPercentage() {
        return Math.clamp(percentage.getValue(), 0.0D, 1.0D);
    }

    public void setPercentage(double percentage) {
        this.percentage.setValue(Math.clamp(percentage, 0.0D, 1.0D));
    }

    public boolean shouldActive() {
        return screenY.getValue() < allY.getValue();
    }

    public double getBarHeightPercentage() {
        return Math.min(1.0D, ((double) screenY.getValue()) / (double) allY.getValue());
    }

    public double getBarHeight(int elementHeight) {
        return elementHeight * (getBarHeightPercentage());
    }

    public double getBarBlankHeight(int elementHeight) {
        return elementHeight * (1 - getBarHeightPercentage());
    }

    public double getCurrentStartY(int elementHeight) {
        // we show bar a
        double barLeftHeight = getBarBlankHeight(elementHeight);
        return getPercentage() * barLeftHeight;
    }

    public void locateBarAt(double location, int height) {
        double percentage = Math.clamp(location / (getBarBlankHeight(height)), 0.0D, 1.0D);
        setPercentage(percentage);
    }

    private boolean isMouseOverBar(DrawableWidget element, double mouseY) {
        mouseY = mouseY - element.getY();
        double barStartY = getCurrentStartY(element.getHeight());
        double barHeight = getBarHeight(element.getHeight());
        return mouseY >= barStartY && mouseY <= barStartY + barHeight;
    }

    double startDragDeltaY;

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
        if (shouldActive()) {
            if (type == Type.MOUSE_CLICK && element.isMouseOver(mouseX, mouseY) && !isMouseOverBar(element, mouseY)) {
                // snap to location

            }
            if (type == Type.MOUSE_START_DRAG) {
                if (element.isMouseOver(mouseX, mouseY)) {
                    if (isMouseOverBar(element, mouseY)) {
                        double barStartY = getCurrentStartY(element.getHeight());
                        startDragDeltaY = (mouseY - barStartY - element.getY());
                    } else {
                        double pointHeight = mouseY - element.getY();
                        double percentage = pointHeight / screenY.getValue();
                        double barHeight = getBarHeight(element.getHeight());
                        startDragDeltaY = barHeight * percentage;
                        locateBarAt(pointHeight - startDragDeltaY, element.getHeight());
                    }
                    return true;
                } else {
                    return false;
                }
            }
            if ((type == Type.MOUSE_DRAG)) {
                double locate = mouseY - startDragDeltaY - element.getY();
                locateBarAt(locate, element.getHeight());
            }
            return true;
        }
        return false;
    }

    public boolean onScroll(
            ExecutableWidget widget, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (shouldActive()) {
            double barBlankHeight = getCurrentStartY(widget.getHeight());
            locateBarAt(barBlankHeight - verticalAmount * 5.0D, widget.getHeight());
            return true;
        }
        return false;
    }

    private static final double MIN_RENDER_PERCENTAGE = 0.05;

    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        if (backGround || shouldActive()) {
            int y1, y2;
            if (((double) screenY.getValue() / allY.getValue()) < MIN_RENDER_PERCENTAGE) {
                double currentStartX = getCurrentStartY(element.getTextureHeight());
                double minimalHeight = screenY.getValue() * MIN_RENDER_PERCENTAGE;
                if (currentStartX + minimalHeight < screenY.getValue()) {
                    y1 = (int) currentStartX;
                    y2 = (int) (currentStartX + minimalHeight);
                } else {
                    y2 = (int) screenY.getValue();
                    y1 = (int) (y2 - minimalHeight);
                }
            } else {
                double currentStartX = getCurrentStartY(element.getTextureHeight());
                double currentBarHeight = screenY.getValue() - getBarBlankHeight(element.getTextureHeight());
                y1 = (int) currentStartX;
                y2 = (int) (currentStartX + currentBarHeight);
            }

            context.fill(1, y1 + 1, element.getTextureWidth() - 1, y2 - 1, Colors.GRAY);
            if (element.isDragging() || (element.isMouseOver(mouseX, mouseY) && isMouseOverBar(element, mouseY))) {
                RenderHandler.drawHighlightFrame(context, 0, y1, element.getTextureWidth(), y2 - y1, Colors.WHITE);
            }
        }
    }
}
