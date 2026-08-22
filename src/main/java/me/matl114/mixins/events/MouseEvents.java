package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.MouseDragAction;
import me.matl114.events.impl.MouseMoveAction;
import me.matl114.managers.input.SimpleInputManager;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.FPoint;
import me.matl114.utils.collections.Point;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = Mouse.class, priority = 1)
public abstract class MouseEvents {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getY();
    //    @Inject(method = "onCursorPos",
    //            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Mouse;hasResolutionChanged:Z", ordinal = 0))
    //    private void onMouseMove(long handle, double xpos, double ypos, CallbackInfo ci)
    //    {//暂时没东西
    //
    //    }

    @Shadow
    private int activeButton;

    @Inject(
            method = "onMouseScroll",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;"))
    private void onMouseScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) { // 暂时没东西
        if (MinecraftClient.getInstance().getOverlay() == null) {
            if (SimpleInputManager.getInstance().onMouseScroll(xOffset, yOffset)) {
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "onMouseButton",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;",
                            ordinal = 0,
                            shift = At.Shift.BEFORE))
    private void onMouseClick(
            long handle, final int button, final int action, int mods, CallbackInfo ci, @Local(ordinal = 3) int i) {

        // capture the modified i
        Point coord = ScreenUtils.getMouseCoord(this.client, (Mouse) (Object) this);
        if (SimpleInputManager.getInstance().onMouseClick(coord.x, coord.y, i, action, mods)) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;mouseMoved(DD)V"))
    private void onMouseMove(Screen instance, double f, double g, Operation<Void> original) {
        Event<MouseMoveAction> event = new Event<>(new MouseMoveAction((Mouse) (Object) this, f, g), true, false);
        Listener.getMouseMove().handleValue(event);
        if (!event.isCancelled()) {
            original.call(instance, f, g);
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;mouseDragged(DDIDD)Z"))
    private boolean onMouseDrag(
            Screen instance, double f, double g, int activeButton, double h, double i, Operation<Boolean> original) {
        Event<MouseDragAction> event = new Event<>(new MouseDragAction((Mouse) (Object) this, f, g, h, i), true, false);
        Listener.getMouseDrag().handleValue(event);
        if (!event.isCancelled()) {
            original.call(instance, f, g, activeButton, h, i);
        }
        return false;
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;isCursorLocked()Z"),
            cancellable = true)
    private void onScreenNull(CallbackInfo ci) {
        // handle screen is null case, we should also send Events
        if (MinecraftClient.getInstance().currentScreen == null
                && MinecraftClient.getInstance().getOverlay() == null) {
            double f = getX()
                    * (double) this.client.getWindow().getScaledWidth()
                    / (double) this.client.getWindow().getWidth();
            double g = getY()
                    * (double) this.client.getWindow().getScaledHeight()
                    / (double) this.client.getWindow().getHeight();
            Event<MouseMoveAction> event = new Event<>(new MouseMoveAction((Mouse) (Object) this, f, g), true, false);
            Listener.getMouseMove().handleValue(event);

            if (this.activeButton != -1) {
                double h = this.cursorDeltaX
                        * (double) this.client.getWindow().getScaledWidth()
                        / (double) this.client.getWindow().getWidth();
                double i = this.cursorDeltaY
                        * (double) this.client.getWindow().getScaledHeight()
                        / (double) this.client.getWindow().getHeight();
                Event<MouseDragAction> event2 =
                        new Event<>(new MouseDragAction((Mouse) (Object) this, f, g, h, i), true, false);
                Listener.getMouseDrag().handleValue(event2);
            }
        }
    }

    @WrapOperation(
            method = "updateMouse",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void onMouseUpdateLook(ClientPlayerEntity instance, double x, double y, Operation<Void> original) {
        Event<FPoint> event = new Event<>(new FPoint(x, y), true, true);
        Listener.getPlayerChangeLook().handleValue(event);
        if (!event.isCancelled()) {
            original.call(instance, event.context().x, event.context().y);
        }
    }
}
