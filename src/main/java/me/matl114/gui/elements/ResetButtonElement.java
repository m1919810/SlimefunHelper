package me.matl114.gui.elements;

import java.util.function.BooleanSupplier;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.utils.ChatUtils;

public class ResetButtonElement extends IconElement.SimpleIconElement {
    public ResetButtonElement(BooleanSupplier canReset, Runnable reset) {
        super(Constants.BUTTON_INACTIVE_SPRITE, Constants.BUTTON_SPRITE, true, ButtonAction.run(() -> {
            if (canReset.getAsBoolean()) {
                reset.run();
            }
        }));
        setActivePredicate((el) -> canReset.getAsBoolean());
        combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            int width = element.getTextureWidth();
            int height = element.getTextureHeight();
            int hhw = width / 4;
            int hhh = height / 4;
            context.drawGuiTexture(Constants.RESET_BUTTON_SPRITE, hhw, hhh, width - 2 * hhw, height - 2 * hhh);
        });
        withTooltips(TooltipHandler.of(
                ChatUtils.parseTooltipsTranslation("widget.gui.reset-button-element.reset.tooltips", "")));
    }
}
