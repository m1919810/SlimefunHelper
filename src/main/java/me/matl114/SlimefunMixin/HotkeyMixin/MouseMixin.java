package me.matl114.SlimefunMixin.HotkeyMixin;

import me.matl114.ManageUtils.SimpleInputManager;
import me.matl114.Utils.ScreenUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;

@Environment(EnvType.CLIENT)
@Mixin(value = Mouse.class,priority = 1)
public abstract class MouseMixin
{
    @Shadow @Final private MinecraftClient client;
    @Shadow
    public abstract double getX();
    @Shadow
    public abstract double getY();
//    @Inject(method = "onCursorPos",
//            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Mouse;hasResolutionChanged:Z", ordinal = 0))
//    private void hookOnMouseMove(long handle, double xpos, double ypos, CallbackInfo ci)
//    {//暂时没东西
//
//    }
//
//    @Inject(method = "onMouseScroll", cancellable = true,
//            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;", ordinal = 0))
//    private void hookOnMouseScroll(long handle, double xOffset, double yOffset, CallbackInfo ci)
//    {//暂时没东西
//
//    }
    @Inject(method = "onMouseButton", cancellable = true,
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z", ordinal = 0))
    private void onMouseClick(long handle, final int button, final int action, int mods, CallbackInfo ci)
    {
        Pair<Integer,Integer> coord= ScreenUtils.getMouseCoord(this.client,(Mouse)(Object)this);
        if (SimpleInputManager.getInstance().onMouseClick(coord.getLeft(),coord.getRight(), button, action))
        {
            ci.cancel();
        }
    }
}
