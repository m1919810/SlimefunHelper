package me.matl114.mixins.events;

import me.matl114.accessors.events.MetadataHolder;
import me.matl114.events.Listener;
import me.matl114.utils.containers.MetaData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenEvents extends AbstractParentElement implements MetadataHolder {
    @Inject(method = "close", at = @At(value = "RETURN"))
    private void onScreenClsoe(CallbackInfo ci){
        Listener.getPostCloseScreen().broadcast((Screen) (AbstractParentElement)this);
    }
    @Inject(method = "init(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;setInitialFocus()V", shift = At.Shift.BEFORE))
    public void onPostInitialization(int width, int height, CallbackInfo ci){
        //first initialize
        Listener.getPostInitializeScreen().broadcast((Screen) (AbstractParentElement)this);
    }
    @Inject(method = "clearAndInit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;setInitialFocus()V", shift = At.Shift.BEFORE))
    public void onClearAndInit(CallbackInfo ci){
        Listener.getPostInitializeScreen().broadcast((Screen) (AbstractParentElement)this);
    }

    @Unique
    public MetaData metaData;
    @Unique
    public MetaData getMetadata(){
        if(metaData == null){
            metaData = new MetaData();
        }
        return metaData;
    }
}
