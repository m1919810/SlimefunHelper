package me.matl114.mixins.render;

import lombok.Getter;
import me.matl114.accessors.gui.ClickableAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClickableWidget.class)
public abstract class DepthClickableMixin implements ClickableAccess {
    @Unique
    @Getter
    private int extraDepth = 0;
    public ClickableAccess setExtraDepth(int x){
        this.extraDepth = x;
        return this;
    }
    @Shadow
    protected abstract void renderWidget(DrawContext context, int mouseX, int mouseY, float delta);


    @Redirect(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ClickableWidget;renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    public void onDepthedWidgetRender(ClickableWidget instance, DrawContext context, int i, int j, float v){
        if(extraDepth != 0){
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, extraDepth);
            renderWidget(context, i,j, v);
            context.getMatrices().pop();
        }else {
            renderWidget(context, i,j, v);
        }
    }
}
