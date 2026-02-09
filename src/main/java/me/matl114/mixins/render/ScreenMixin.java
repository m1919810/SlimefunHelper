package me.matl114.mixins.render;

import lombok.Getter;
import lombok.Setter;
import lombok.val;
import me.matl114.accessors.gui.ButtonNotFocusedScreenAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.basic.DisplayWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractParentElement implements ScreenAccess {
    @Shadow
    protected abstract  <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);
    @Shadow
    protected void remove(Element child){

    }

    @Shadow protected abstract <T extends Drawable> T addDrawable(T drawable);

    @Unique
    public <T extends Element & Drawable & Selectable> T addDrawableChildTo(T drawable){
        if(drawable instanceof DisplayWidget display){
            addDrawable(display);
            return drawable;
        }else{
            return addDrawableChild(drawable);
        }

    }
    @Unique
    public void removeChildFrom(Element val){
        remove(val);
    }
    @Getter
    @Setter
    @Unique
    Screen parent = null;
    @Unique
    public void open(){
        MinecraftClient.getInstance().setScreen((Screen)(Object) this);
    }
    @Unique
    public void openFromCurrent(){
        parent = MinecraftClient.getInstance().currentScreen;
        open();
    }
    @Unique
    public void switchToScreen(Screen anotherScreen){
        Screen p = this.parent;
        this.parent = null;
        ScreenAccess.of(anotherScreen).setParent(p);
        MinecraftClient.getInstance().setScreen(anotherScreen);
    }
    public void switchFromCurrent(){
        Screen current = MinecraftClient.getInstance().currentScreen;
        if(current == null){
            this.parent = null;
        }else{
            this.parent =  ((ScreenMixin)(Object)current).parent;
            ((ScreenMixin)(Object)current).parent = null;
        }
        MinecraftClient.getInstance().setScreen((Screen)(Object)this);
    }


    @ModifyArgs(method = "close",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    public void onRedirectReturnScreen(Args args){
        if(parent != null){
            args.set(0, parent);
            parent = null;
        }
    }

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
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if(this instanceof ButtonNotFocusedScreenAccess access && !access.enableSwitchUsingKey()){
            cir.setReturnValue(false);
        }
    }



    //todo: add close future list


}
