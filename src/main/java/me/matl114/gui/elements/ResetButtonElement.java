package me.matl114.gui.elements;

import java.util.function.BooleanSupplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.utils.ChatUtils;
import net.minecraft.util.Identifier;

public class ResetButtonElement extends IconElement.SimpleIconElement {
    public static final Identifier RESET_BUTTON = new Identifier("slimefunhelper", "gui/reset");

    public ResetButtonElement(BooleanSupplier canReset, Runnable reset) {
        super(ButtonElement.BUTTON_INACTIVE, ButtonElement.BUTTON, true, ButtonAction.run(() -> {
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
            context.drawGuiTexture(RESET_BUTTON, hhw, hhh, width - 2 * hhw, height - 2 * hhh);
        });
        withTooltips(TooltipHandler.of(
                ChatUtils.parseTooltipsTranslation("widget.gui.reset-button-element.reset.tooltips", "")));
    }
}
