package me.matl114.mixins.versioned;

import me.matl114.versioned.accessors.GuiRenderStateLayerAccess;
import me.matl114.versioned.impl.DrawContext_v1_21_11;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.Layer.class)
public abstract class GuiRenderStateLayerMixin implements GuiRenderStateLayerAccess {
    // add depth impl
    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void onInit(CallbackInfo ci) {
        this.depthLevel = DrawContext_v1_21_11.getCurrentDepthLevel();
    }

    @Unique
    int depthLevel = 0;

    @Unique
    public int getDepth() {
        return depthLevel;
    }

    @Unique
    public void setDepth(int depth) {
        this.depthLevel = depth;
    }
}
