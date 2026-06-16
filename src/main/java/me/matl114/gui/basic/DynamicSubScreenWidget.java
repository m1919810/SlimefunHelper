package me.matl114.gui.basic;

import me.matl114.utils.config.ValueAccessor;

public class DynamicSubScreenWidget extends SubScreenWidget {
    ValueAccessor<Integer> xCoord;
    ValueAccessor<Integer> yCoord;
    ValueAccessor<Float> scale;

    public DynamicSubScreenWidget(ValueAccessor<Integer> xCoord, ValueAccessor<Integer> yCoord) {
        this(xCoord, yCoord, ValueAccessor.of(1.0F));
    }

    public DynamicSubScreenWidget(
            ValueAccessor<Integer> xCoord, ValueAccessor<Integer> yCoord, ValueAccessor<Float> scale) {
        super(0, 0, 0, 0);
        this.xCoord = xCoord;
        this.yCoord = yCoord;
        this.scale = scale;
    }

    @Override
    public float getTextureScale() {
        return scale.getValue();
    }

    public <T extends DrawableWidget> T setTextureScale(float scale) {
        this.scale.setValue(scale);
        return (T) this;
    }

    @Override
    public int getX() {
        return xCoord.getValue();
    }

    public int getY() {
        return yCoord.getValue();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (isMouseOver(mouseX, mouseY)) {
            // scroll up so other can see down below
            yCoord.setValue(yCoord.getValue() + (int) (verticalAmount * 10.0D));
            return true;
        }
        return false;
    }
}
