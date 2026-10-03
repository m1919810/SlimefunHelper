package me.matl114.gui;

import java.util.function.BooleanSupplier;
import me.matl114.gui.basic.DrawableWidget;

public record ConditionalWidget(BooleanSupplier condition, DrawableWidget widget) {
    public static final BooleanSupplier ALWAYS_TRUE = () -> true;

    public ConditionalWidget(DrawableWidget widget) {
        this(ALWAYS_TRUE, widget);
    }

    public boolean shouldShow() {
        return condition.getAsBoolean();
    }
}
