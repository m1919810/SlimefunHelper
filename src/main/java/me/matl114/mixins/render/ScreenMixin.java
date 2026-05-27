package me.matl114.mixins.render;

import me.matl114.accessors.gui.CustomFocusBehaviourScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractParentElement {

    @Override
    public Element getFocused() {
        Element focused = super.getFocused();
        //  Debug.info("getFocused called");
        if (focused == null
                && (Object) this instanceof CustomFocusBehaviourScreenAccess access
                && access.autoSelectDefaultElementWhenNotFocused()
                && (focused = access.getDefaultElement()) != null) {
            // Debug.info("to default Value");
            this.setFocused(focused);
        }
        return focused;
    }

    @Inject(
            method = "keyPressed",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/Screen;switchFocus(Lnet/minecraft/client/gui/navigation/GuiNavigationPath;)V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof CustomFocusBehaviourScreenAccess access && !access.enableSwitchUsingNavigation()) {
            cir.setReturnValue(false);
        }
    }
}
