package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.hacks.modules.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @WrapWithCondition(
            method = "renderBackground",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/Screen;renderInGameBackground(Lnet/minecraft/client/gui/DrawContext;)V"))
    private boolean renderBackground(Screen screen, DrawContext drawContext) {
        if (NoRender.INSTANCE.noGuiBackGroundOverlay()) {
            return false;
        }
        return true;
    }
}
