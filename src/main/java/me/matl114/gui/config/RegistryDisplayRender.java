package me.matl114.gui.config;

import lombok.AllArgsConstructor;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

@AllArgsConstructor
public class RegistryDisplayRender implements RenderHandler {
    ItemStack icon;
    Text name;
    Identifier identifier;
    //this render should be 20 high
    @Override
    public void renderAtCentered(DrawableWidget element, VDrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        context.drawItem(icon, 2, 2, 114514, 0);
        RenderHandler.drawScaledText0(
            context,
            mc.textRenderer,
            name,
            20,
            1,
            200,
            10,
            -16711936,
-1
        );
        RenderHandler.drawScaledText0(
            context,
            mc.textRenderer,
            Text.literal(identifier.toString()),
            20,
            10,
            200,
            19,
            -16711936,
            -1
        );

    }
}
