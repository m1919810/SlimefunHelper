package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.TooltipsRender;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DrawContext.class)
public abstract class DrawContextEvents {
    @WrapMethod(method = "drawTooltipImmediately")
    private void onRenderTooltip(
            TextRenderer textRenderer,
            List<TooltipComponent> components,
            int x,
            int y,
            TooltipPositioner positioner,
            @org.jspecify.annotations.Nullable Identifier texture,
            Operation<Void> original) {
        TooltipsRender tooltipRender = new TooltipsRender(
                VDrawContext.of((DrawContext) (Object) this), components, x, y, positioner, texture);
        RenderListener.getRenderTooltipEvent().broadcast(tooltipRender);
        original.call(textRenderer, components, tooltipRender.x, tooltipRender.y, positioner, texture);
    }
}
