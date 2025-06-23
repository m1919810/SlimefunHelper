package me.matl114.mixins.RenderMixin;

import lombok.Getter;
import me.matl114.access.DrawContextAccess;
import me.matl114.renders.RenderMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin implements DrawContextAccess {
    @Final
    @Shadow
    private MinecraftClient client;
    @Final
    @Shadow
    private MatrixStack matrices;
    @Final
    @Getter
    @Shadow
    private VertexConsumerProvider.Immediate vertexConsumers;
    public MinecraftClient getMinecraftClient(){
        return this.client;
    }
    public MatrixStack getMatrixStack(){
        return this.matrices;
    }

    @Inject(method = "drawItemTooltip",at = @At("HEAD"))
    public void onItemTooptipDraw(TextRenderer textRenderer, ItemStack stack, int x, int y, CallbackInfo ci){
       // RenderMain.renderItemTooltipsTasks((DrawContext) ((Object)this), textRenderer, stack, x, y);
    }
}
