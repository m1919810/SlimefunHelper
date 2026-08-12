package me.matl114.hacks.modules.render;

import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public abstract class IRender2DColoredModule extends IRender2DModule {
    public IRender2DColoredModule() {
        super("IRender2DColoredModule");
    }

    public IRender2DColoredModule(String name) {
        super(name);
    }

    public NBTRef<WrapColor> color = builder(hud.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(TextColor.parse("#F05BDA").getOrThrow()))
            .build();

    public FlagRef bold = flagBuilder(hud.add("bold")).defaultValue(true).build();

    public void drawText(VDrawContext vdraw, OrderedText text) {
        int rgb = color.get().withAlpha(255);
        if (right.get()) {
            int width = mc.textRenderer.getWidth(text);
            vdraw.drawText(mc.textRenderer, text, -width, 0, rgb, true);
        } else {
            vdraw.drawText(mc.textRenderer, text, 0, 0, rgb, true);
        }
        vdraw.getMatrices().translate(0, HEIGHT);
    }

    public void drawText(VDrawContext vdraw, Text text) {
        if (bold.get()) {
            text = text.copy().formatted(Formatting.BOLD);
        }
        drawText(vdraw, text.asOrderedText());
    }

    public void drawText(VDrawContext vdraw, String text) {
        MutableText text0 = Text.literal(text);
        if (bold.get()) {
            text0 = text0.formatted(Formatting.BOLD);
        }
        drawText(vdraw, text0.asOrderedText());
    }
}
