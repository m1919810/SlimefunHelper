package me.matl114.mixins.events;

import me.matl114.events.RenderListener;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudEvents {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderSubtitlesHud(Lnet/minecraft/client/gui/DrawContext;Z)V", shift = At.Shift.AFTER))
    private void renderPlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        RenderListener.getRenderGameHudTasks().broadcast(VDrawContext.of(context), tickCounter, client.options.hudHidden);
    }
}
