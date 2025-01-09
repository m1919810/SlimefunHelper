package me.matl114.SlimefunMixin.FixMixin;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractParentElement.class)
public class AbstractElementButtonFixMixin {
    @Inject(method = "setFocused(Lnet/minecraft/client/gui/Element;)V",at = @At("HEAD"),cancellable = true)
    private void onSetFocused(Element focused, CallbackInfo ci) {
        if((Object)this instanceof ButtonNotFocusedScreenAccess access&& !access.doFocusButtonWhenClicked()&& focused instanceof ButtonWidget bw){
            ci.cancel();
        }
    }
}
