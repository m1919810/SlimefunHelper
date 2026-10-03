package me.matl114.gui.elements;

import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.TextAlign;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class SlidingInputElement<T> extends TextFieldElement {
    private final Value<T> value;
    private final WrapperFactory<Double, T> sliderFactory;
    private ColorSampler sliderColor = ColorSampler.of(0x203FA7FF);
    private boolean sliderArmed;
    private boolean sliding;

    public SlidingInputElement(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        super(ValueAccessor.of(
                () -> value == null ? "" : value.getInput(),
                input -> {
                    if (value != null) {
                        value.setInput(input);
                    }
                }));
        this.value = value;
        this.sliderFactory = sliderFactory;
        this.setMaxLength(32768);
        this.setAlign(TextAlign.MIDDLE);
        if (value != null) {
            setBorderColorProvider(McWidgetHelpers.getWrongRedTextBoxColorProvider(value::isValidate));
        }
    }

    public SlidingInputElement<T> setSliderColor(ColorSampler sliderColor) {
        this.sliderColor = sliderColor == null ? ColorSampler.of(0x203FA7FF) : sliderColor;
        return this;
    }

    protected double getPercentage() {
        if (this.value == null || this.sliderFactory == null) {
            return 0.0;
        }
        try {
            T current = this.value.get();
            Double currentPercentage = this.sliderFactory.get(current);
            return MathHelper.clamp(currentPercentage == null ? 0.0 : currentPercentage, 0.0, 1.0);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    protected boolean updateFromMouse(ExecutableWidget element, double mouseX) {
        if (this.value == null || this.sliderFactory == null) {
            return false;
        }
        syncWidgetState(element);
        double localX = translateMouseX(element, mouseX);
        double trackWidth = Math.max(1.0, element.getTextureWidth() - 2.0);
        double selected = MathHelper.clamp(
                (localX - 1.0) / trackWidth, 0.0, 1.0);
        try {
            T next = this.sliderFactory.create(selected);
            this.value.accept(next);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void renderInputBackgroundOverlay(
        DrawableWidget element, VDrawContext context, int mouseX, int mouseY) {
        int trackWidth = Math.max(0, element.getTextureWidth() - 2);
        int progress = MathHelper.clamp(
                MathHelper.floor(trackWidth * getPercentage() ) + 2, 0, trackWidth);
        if (progress > 0) {
            context.fill(
                    1,
                    1,
                    1 + progress,
                    Math.max(1, element.getTextureHeight() - 1),
                    this.sliderColor.getColorInt());
        }
    }

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
        return switch (type) {
            case MOUSE_CLICK -> {
                if (button == 0) {
                    yield super.onAction(element, mouseX, mouseY, button, type);
                }
                sliderArmed = button == 1;
                yield sliderArmed;
            }
            case MOUSE_START_DRAG -> {
                if (sliderArmed) {
                    sliderArmed = false;
                    sliding = true;
                    yield updateFromMouse(element, mouseX);
                }
                yield super.onAction(element, mouseX, mouseY, button, type);
            }
            case MOUSE_DRAG -> {
                if (sliding) {
                    yield updateFromMouse(element, mouseX);
                }
                yield super.onAction(element, mouseX, mouseY, button, type);
            }
            case MOUSE_RELEASE, MOUSE_RELEASE_DRAG -> {
                boolean wasSliding = sliding;
                boolean wasArmed = sliderArmed;
                sliderArmed = false;
                sliding = false;
                yield wasSliding || wasArmed || super.onAction(element, mouseX, mouseY, button, type);
            }
            default -> super.onAction(element, mouseX, mouseY, button, type);
        };
    }
}
