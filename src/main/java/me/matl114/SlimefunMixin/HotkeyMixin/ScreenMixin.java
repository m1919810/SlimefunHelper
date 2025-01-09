package me.matl114.SlimefunMixin.HotkeyMixin;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractParentElement {
    @Override
    public Element getFocused(){
        Element focused=super.getFocused();
      //  Debug.info("getFocused called");
        if(focused==null&& (Object)this instanceof ButtonNotFocusedScreenAccess access&&access.autoSelectDefaultElementWhenNotFocused()&&(focused=access.getDefaultElement())!=null){
            //Debug.info("to default Value");
            this.setFocused(focused);
        }
        return focused;
    }
    @Inject(method = "keyPressed",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;switchFocus(Lnet/minecraft/client/gui/navigation/GuiNavigationPath;)V",shift = At.Shift.BEFORE),cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if(this instanceof ButtonNotFocusedScreenAccess access&&!access.enableSwitchUsingKey()){
            cir.setReturnValue(false);
        }
    }
}
