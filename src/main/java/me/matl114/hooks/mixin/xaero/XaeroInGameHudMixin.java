package me.matl114.hooks.mixin.xaero;

import me.matl114.hacks.modules.survival.XaeroHelper;
import me.matl114.hooks.XaeroHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class XaeroInGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    Screen cachedScreen;

    @Inject(method = "render", at = @At("HEAD"), order = 1)
    private void onTransparentGuiFix(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (XaeroHelper.INSTANCE.transparentGuiMapFix.get()
                && XaeroHooks.getInstance().isXaeroPlusEnable()
                && XaeroHooks.getInstance().isGuiMap(client.currentScreen)) {
            cachedScreen = client.currentScreen;
            client.currentScreen = null;
        }
    }

    @Inject(method = "render", at = @At("HEAD"), order = 99999)
    private void onTransparentGuiRestore(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (cachedScreen != null) {
            client.currentScreen = cachedScreen;
            cachedScreen = null;
        }
    }
}
