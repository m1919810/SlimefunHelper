package me.matl114.mixins.events;

import me.matl114.events.RenderListener;
import me.matl114.versioned.accessors.LayeredDrawerAccess;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LayeredDrawer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudEvents {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private LayeredDrawer layeredDrawer;

    @Inject(method = "render", at = @At("HEAD"))
    private void renderPlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        LayeredDrawerAccess.of(this.layeredDrawer).setPostRenderTask((ctx, tc) -> {
            VDrawContext vdraw = VDrawContext.of(ctx);
            vdraw.pushMatrix();
            try {
                RenderListener.getRender2DEvent().broadcast(vdraw, tc.getTickDelta(false), client.options.hudHidden);
            } finally {
                vdraw.popMatrix();
            }
        });
    }
}
