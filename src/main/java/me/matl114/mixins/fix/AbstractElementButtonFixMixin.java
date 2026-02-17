package me.matl114.mixins.fix;

import me.matl114.accessors.gui.CustomFocusBehaviourScreenAccess;
import me.matl114.gui.basic.DrawableWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Element;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractParentElement.class)
public class AbstractElementButtonFixMixin {
    @Inject(method = "setFocused(Lnet/minecraft/client/gui/Element;)V", at = @At("HEAD"), cancellable = true)
    private void onSetFocused(Element focused, CallbackInfo ci) {
        if ((Object) this instanceof CustomFocusBehaviourScreenAccess access
                && !access.canFocusButtonWhenClicked()
                && focused instanceof DrawableWidget bw) {
            ci.cancel();
        }
    }
}
