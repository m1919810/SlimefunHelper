package me.matl114.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.utils.config.PropertyTracker;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public interface WidgetFactory {
    WidgetFactory DEFAULT = DefaultWidgetFactory.INSTANCE;

    static ButtonBuilder button() {
        return ButtonBuilder.builder();
    }

    static IconBuilder icon() {
        return IconBuilder.builder();
    }

    static RawTextBuilder rawText() {
        return RawTextBuilder.builder();
    }

    static TextFieldBuilder textField() {
        return TextFieldBuilder.builder();
    }

    ElementHandler create(ButtonBuilder builder);

    ElementHandler create(IconBuilder builder);

    ElementHandler create(RawTextBuilder builder);

    ElementHandler create(TextFieldBuilder builder);

    @Setter
    @Accessors(chain = true, fluent = true)
    abstract class AbstractElementBuilder<B extends AbstractElementBuilder<B>> {
        public WidgetFactory factory = DEFAULT;
        public boolean showTooltips = true;
        public TooltipHandler tooltipHandler;
        public List<RenderHandler> extraRenders = new ArrayList<>();
        public List<RenderHandler> absoluteRenders = new ArrayList<>();
        public List<InputHandler> inputHandlers = new ArrayList<>();
        public Predicate<ElementHandler> presentCondition;
        public Predicate<ElementHandler> activeActionCondition;

        protected final WidgetFactory resolveFactory() {
            return factory == null ? DEFAULT : factory;
        }

        @SuppressWarnings("unchecked")
        protected final B self() {
            return (B) this;
        }

        public ElementHandler build() {
            return build(resolveFactory());
        }

        public abstract ElementHandler build(WidgetFactory factory);

        public B tooltips(List<Text> tooltips) {
            this.tooltipHandler = tooltips == null ? null : TooltipHandler.of(tooltips);
            return self();
        }

        public B tooltips(Supplier<List<Text>> tooltips) {
            this.tooltipHandler = tooltips == null ? null : TooltipHandler.of(tooltips);
            return self();
        }

        public B tooltips(TooltipHandler.TooltipProvider tooltips) {
            this.tooltipHandler = tooltips == null ? null : TooltipHandler.of(tooltips);
            return self();
        }

        public B render(RenderHandler renderHandler) {
            if (renderHandler != null) {
                if (this.extraRenders == null) {
                    this.extraRenders = new ArrayList<>();
                }
                this.extraRenders.add(renderHandler);
            }
            return self();
        }

        public B absoluteRender(RenderHandler renderHandler) {
            if (renderHandler != null) {
                if (this.absoluteRenders == null) {
                    this.absoluteRenders = new ArrayList<>();
                }
                this.absoluteRenders.add(renderHandler);
            }
            return self();
        }

        public B input(InputHandler inputHandler) {
            if (inputHandler != null) {
                if (this.inputHandlers == null) {
                    this.inputHandlers = new ArrayList<>();
                }
                this.inputHandlers.add(inputHandler);
            }
            return self();
        }

        public B presentWhen(Predicate<ElementHandler> presentCondition) {
            this.presentCondition = presentCondition;
            return self();
        }

        public B activeWhen(Predicate<ElementHandler> activeActionCondition) {
            this.activeActionCondition = activeActionCondition;
            return self();
        }
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    abstract class AbstractIconBuilder<B extends AbstractIconBuilder<B>> extends AbstractElementBuilder<B> {
        public ButtonAction action = ButtonAction.empty();
        public Identifier inactiveId;
        public Identifier activeId;
        public boolean guiTexture;
        public boolean active = true;
        public Predicate<IconElement> activePredicate;
        public ColorProvider highLightColor;
        public Color shaderColor = Color.WHITE;

        public B onClick(Runnable task) {
            this.action = task == null ? null : ButtonAction.run(task);
            return self();
        }

        public B state(Identifier activeId, Identifier inactiveId) {
            this.activeId = activeId;
            this.inactiveId = inactiveId;
            return self();
        }

        public B shaderColor(int shaderColor) {
            this.shaderColor = new Color(shaderColor, true);
            return self();
        }
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    final class IconBuilder extends AbstractIconBuilder<IconBuilder> {
        public static IconBuilder builder() {
            return new IconBuilder();
        }

        @Override
        public ElementHandler build(WidgetFactory factory) {
            return factory.create(this);
        }
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    final class ButtonBuilder extends AbstractIconBuilder<ButtonBuilder> {
        public TextProvider textProvider = TextProvider.of(Text.empty());

        public static ButtonBuilder builder() {
            return new ButtonBuilder();
        }

        public ButtonBuilder() {
            this.guiTexture = true;
            this.inactiveId = ButtonElement.BUTTON_INACTIVE;
            this.activeId = ButtonElement.BUTTON;
        }

        public ButtonBuilder text(Text text) {
            this.textProvider = text == null ? null : TextProvider.of(text);
            return this;
        }

        @Override
        public ElementHandler build(WidgetFactory factory) {
            return factory.create(this);
        }
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    final class RawTextBuilder extends AbstractElementBuilder<RawTextBuilder> {
        public TextProvider textProvider = TextProvider.of(Text.empty());
        public ColorSampler color = ColorSampler.WHITE;
        public int alignment;

        public static RawTextBuilder builder() {
            return new RawTextBuilder();
        }

        public RawTextBuilder text(Text text) {
            this.textProvider = text == null ? null : TextProvider.of(text);
            return this;
        }

        public RawTextBuilder color(int color) {
            this.color = ColorSampler.of(color);
            return this;
        }

        @Override
        public ElementHandler build(WidgetFactory factory) {
            return factory.create(this);
        }
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    final class TextFieldBuilder extends AbstractElementBuilder<TextFieldBuilder> {
        public TextFieldWidget textFieldWidget;
        public Text message = Text.empty();
        public String text = "";
        public int maxLength = 32768;
        public Consumer<String> changedListener;
        public PropertyTracker<TextFieldAccess, String> listener;
        public ColorProvider borderColorProvider;
        public Boolean drawsBackground;
        public Boolean focusUnlocked;
        public Boolean editable;
        public Text placeholder;
        public String suggestion;
        public Predicate<String> textPredicate;
        public Integer editableColor;
        public Integer uneditableColor;
        public Boolean cursorToEnd;

        public static TextFieldBuilder builder() {
            return new TextFieldBuilder();
        }

        @Override
        public ElementHandler build(WidgetFactory factory) {
            return factory.create(this);
        }
    }
}