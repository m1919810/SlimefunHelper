package me.matl114.events.impl;

import java.util.List;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class TooltipsRender {
    public final VDrawContext context;
    public final List<TooltipComponent> components;
    public final TooltipPositioner positioner;

    @Nullable
    public final Identifier texture;

    public int x;
    public int y;

    public TooltipsRender(
            VDrawContext context,
            List<TooltipComponent> components,
            int x,
            int y,
            TooltipPositioner positioner,
            @Nullable Identifier texture) {
        this.context = context;
        this.components = components;
        this.x = x;
        this.y = y;
        this.positioner = positioner;
        this.texture = texture;
    }
}
