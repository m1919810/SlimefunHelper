package me.matl114.SlimefunMixin.RenderMixin;

import me.matl114.Access.DrawContextAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin implements DrawContextAccess {
    @Final
    @Shadow
    private MinecraftClient client;
    @Final
    @Shadow
    private MatrixStack matrices;
    public MinecraftClient getMinecraftClient(){
        return this.client;
    }
    public MatrixStack getMatrixStack(){
        return this.matrices;
    }
}
