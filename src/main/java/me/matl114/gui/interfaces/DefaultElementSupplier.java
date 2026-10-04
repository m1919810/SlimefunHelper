package me.matl114.gui.interfaces;

import java.util.List;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.elements.TextFieldElement;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class DefaultElementSupplier implements ElementSupplier {
    public static final DefaultElementSupplier INSTANCE = new DefaultElementSupplier();

    private DefaultElementSupplier() {}

    @Override
    public ElementHandler create(ElementSupplier.ButtonBuilder builder) {
        ButtonElement element = new ButtonElement(safeTextProvider(builder.textProvider), safeAction(builder.action));
        if (builder.inactiveId != null) {
            element.setInactiveId(builder.inactiveId);
        }
        if (builder.activeId != null) {
            element.setActiveId(builder.activeId);
        }
        element.setGuiTexture(builder.guiTexture);
        element.setActive(builder.active);
        if (builder.activePredicate != null) {
            element.setActivePredicate(builder.activePredicate);
        }
        if (builder.highLightColor != null) {
            element.setHighLightColor(builder.highLightColor);
        }
        if (builder.shaderColor != null) {
            element.setShaderColor(builder.shaderColor);
        }
        return applyCommon(element, builder);
    }

    @Override
    public ElementHandler create(ElementSupplier.IconBuilder builder) {
        return buildIcon(builder);
    }

    @Override
    public ElementHandler create(ElementSupplier.RawTextBuilder builder) {
        RawTextElement element = new RawTextElement(
                TextProvider.withAlign(safeTextProvider(builder.textProvider), TextAlign.fromLegacy(builder.alignment)),
                builder.color == null ? ColorSampler.WHITE : builder.color);
        return applyCommon(element, builder);
    }

    @Override
    public ElementHandler create(ElementSupplier.TextFieldBuilder builder) {
        String initialText = builder.textFieldWidget == null ? builder.text : builder.textFieldWidget.getText();
        TextFieldElement element = new TextFieldElement(ValueAccessor.holder(initialText == null ? "" : initialText));
        element.setMessage(builder.message);
        element.setMaxLength(builder.maxLength);
        element.setText(builder.text == null ? "" : builder.text);
        if (builder.cursorToEnd != null) {
            element.setCursorToEnd(builder.cursorToEnd);
        }
        if (builder.borderColorProvider != null) {
            element.setBorderColorProvider(builder.borderColorProvider);
        }
        if (builder.drawsBackground != null) {
            element.setDrawsBackground(builder.drawsBackground);
        }
        if (builder.focusUnlocked != null) {
            element.setFocusUnlocked(builder.focusUnlocked);
        }
        return applyCommon(element, builder);
    }

    private ElementHandler buildIcon(ElementSupplier.AbstractIconBuilder<?> builder) {
        Identifier activeId = builder.activeId != null ? builder.activeId : builder.inactiveId;
        Identifier inactiveId = builder.inactiveId != null ? builder.inactiveId : builder.activeId;
        IconElement.SimpleIconElement element =
                new IconElement.SimpleIconElement(inactiveId, activeId, builder.guiTexture, safeAction(builder.action));
        element.setActive(builder.active);
        if (builder.activePredicate != null) {
            element.setActivePredicate(builder.activePredicate);
        }
        if (builder.highLightColor != null) {
            element.setHighLightColor(builder.highLightColor);
        }
        if (builder.shaderColor != null) {
            element.setShaderColor(builder.shaderColor);
        }
        return applyCommon(element, builder);
    }

    private ElementHandler applyCommon(AbstractElement element, ElementSupplier.AbstractElementBuilder<?> builder) {
        element.setShowTooltips(builder.showTooltips);
        if (builder.tooltipHandler != null) {
            element.withTooltips(builder.tooltipHandler);
        }
        applyRenders(element, builder.extraRenders);
        applyAbsoluteRenders(element, builder.absoluteRenders);
        applyInputs(element, builder.inputHandlers);
        ElementHandler handler = element;
        if (builder.activeActionCondition != null) {
            handler = handler.withActiveActionCondition(builder.activeActionCondition);
        }
        if (builder.presentCondition != null) {
            handler = handler.withPresentCondition(builder.presentCondition);
        }
        return handler;
    }

    private void applyRenders(AbstractElement element, List<RenderHandler> renders) {
        if (renders == null) {
            return;
        }
        for (RenderHandler renderHandler : renders) {
            if (renderHandler != null) {
                element.combineRender(renderHandler);
            }
        }
    }

    private void applyAbsoluteRenders(AbstractElement element, List<RenderHandler> renders) {
        if (renders == null) {
            return;
        }
        for (RenderHandler renderHandler : renders) {
            if (renderHandler != null) {
                element.combineAbsoluteRender(renderHandler);
            }
        }
    }

    private void applyInputs(AbstractElement element, List<InputHandler> handlers) {
        if (handlers == null) {
            return;
        }
        for (InputHandler inputHandler : handlers) {
            if (inputHandler != null) {
                element.withInputHandler(inputHandler);
            }
        }
    }

    private ButtonAction safeAction(ButtonAction action) {
        return action == null ? ButtonAction.empty() : action;
    }

    private TextProvider safeTextProvider(TextProvider provider) {
        return provider == null ? TextProvider.of(Text.empty()) : provider;
    }
}
