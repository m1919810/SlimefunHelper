package me.matl114.SlimefunMixin.FixMixin;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ParentElement.class)
public interface ParentElementButtonFixMixin {
    @Shadow public abstract void setFocused(@Nullable Element focused);

    @Inject(method = "mouseClicked",at=@At( "RETURN"))
    default void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        boolean returnValue=cir.getReturnValueZ();
        if( !returnValue ){
           // Debug.info("miss!");
            Element defaultVal=null;
            if(((ParentElement)((Object)this)) instanceof ButtonNotFocusedScreenAccess access){
                //force=!access.doKeepButtonWhenClicked();
                defaultVal=access.getDefaultElement();
            }
            this.setFocused(defaultVal);
        }
    }
}
