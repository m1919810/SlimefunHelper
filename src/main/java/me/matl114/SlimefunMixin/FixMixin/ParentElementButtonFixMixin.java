package me.matl114.SlimefunMixin.FixMixin;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.AbstractParentElement;
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
        Element defaultVal=null;
        Element nowValue=((ParentElement)((Object)this)).getFocused();
        boolean force=false;
        if(nowValue instanceof ButtonWidget widget&&((ParentElement)((Object)this)) instanceof ButtonNotFocusedScreenAccess access){
            force=!access.doKeepButtonAfterClicked();
            defaultVal=access.getDefaultElement();
        }
        if(force|| !returnValue ){
           // Debug.info("miss!");
            this.setFocused(defaultVal);
        }
    }
}
