package me.matl114.gui.elements;

import java.util.List;
import java.util.function.BooleanSupplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.TooltipHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class ResetButtonElement extends IconElement.SimpleIconElement {
    public static final Identifier RESET_BUTTON = new Identifier("slimefunhelper", "gui/reset");
    public static final List<Text> TOOLTIPS_RESET = List.of(
            Text.literal("点击重置").formatted(Formatting.RED), Text.literal("谨慎点击").formatted(Formatting.RED));

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
        withTooltips(TooltipHandler.of(TOOLTIPS_RESET));
    }
}
