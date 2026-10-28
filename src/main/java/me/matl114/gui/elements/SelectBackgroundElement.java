package me.matl114.gui.elements;

import java.util.Objects;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;

/** Selection background with left-click toggling through the supplied accessor. */
public class SelectBackgroundElement extends AbstractElement {
    private final ValueAccessor<Boolean> selected;
    private final ColorSampler selectedColor;
    private final ColorSampler hoverColor;
    private final ColorSampler normalColor;

    public SelectBackgroundElement(ValueAccessor<Boolean> selected) {
        this(selected, ColorSampler.of(0xA0607090), ColorSampler.of(0x80505050), ColorSampler.of(0x30282828));
    }

    public SelectBackgroundElement(
            ValueAccessor<Boolean> selected,
            ColorSampler selectedColor,
            ColorSampler hoverColor,
            ColorSampler normalColor) {
        this.selected = Objects.requireNonNull(selected);
        this.selectedColor = Objects.requireNonNull(selectedColor);
        this.hoverColor = Objects.requireNonNull(hoverColor);
        this.normalColor = Objects.requireNonNull(normalColor);
    }

    public void toggleSelection() {
        selected.setValue(!selected.getValue());
    }

    protected int backgroundColor(boolean shouldHighlight) {
        return (selected.getValue() ? selectedColor : shouldHighlight ? hoverColor : normalColor).getColorInt();
    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        toggleSelection();
        return true;
    }

    @Override
    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        context.fill(0, 0, element.getTextureWidth(), element.getTextureHeight(), backgroundColor(shouldHighlight));
    }
}
