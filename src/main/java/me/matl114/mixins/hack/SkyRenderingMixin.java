package me.matl114.mixins.hack;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.DataInputStream;
import java.io.IOException;
import me.matl114.hacks.modules.render.SkyRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class SkyRenderingMixin {
    @Unique private Identifier skyRender$cachedTexture;
    @Unique private net.minecraft.resource.ResourceManager skyRender$cachedResourceManager;
    @Unique private boolean skyRender$cachedAtlas;

    @ModifyArg(
            method = "renderSky",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0),
            index = 0)
    private float skyRender$red(float original) {
        return skyRender$color(original, 0);
    }

    @ModifyArg(
            method = "renderSky",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0),
            index = 1)
    private float skyRender$green(float original) {
        return skyRender$color(original, 1);
    }

    @ModifyArg(
            method = "renderSky",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0),
            index = 2)
    private float skyRender$blue(float original) {
        return skyRender$color(original, 2);
    }

    @Unique
    private float skyRender$color(float original, int channel) {
        SkyRender module = SkyRender.INSTANCE;
        if (module == null || !module.hasCustomColor()) return original;
        int color = module.color.get().asRGB();
        return switch (channel) {
            case 0 -> ColorHelper.Argb.getRed(color) / 255.0F;
            case 1 -> ColorHelper.Argb.getGreen(color) / 255.0F;
            default -> ColorHelper.Argb.getBlue(color) / 255.0F;
        };
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",
                    shift = At.Shift.AFTER,
                    ordinal = 0))
    private void skyRender$drawOverSky(Matrix4f view, Matrix4f projection, float tickDelta,
            net.minecraft.client.render.Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        skyRender$drawBackground(view);
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void skyRender$replaceEndSky(MatrixStack matrices, CallbackInfo ci) {
        if (skyRender$getBackground() == null) return;
        skyRender$drawBackground(matrices.peek().getPositionMatrix());
        ci.cancel();
    }

    @Unique
    private Identifier skyRender$getBackground() {
        SkyRender module = SkyRender.INSTANCE;
        return module == null ? null : module.getBackground();
    }

    @Unique
    private void skyRender$drawBackground(Matrix4f view) {
        Identifier texture = skyRender$getBackground();
        if (texture == null) return;
        SkyRender module = SkyRender.INSTANCE;
        int color = module != null && module.hasCustomColor() ? module.color.get().asRGB() : -1;
        boolean atlas = skyRender$isSixTileAtlas(texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int face = 0; face < 6; face++) {
            int tile = switch (face) {
                case 0 -> 4;
                case 1 -> 5;
                case 2 -> 2;
                case 3 -> 3;
                case 4 -> 1;
                default -> 0;
            };
            float u = atlas ? (tile % 3) / 3.0F : 0.0F;
            float v = atlas ? (tile / 3) / 2.0F : 0.0F;
            float u2 = atlas ? u + 1.0F / 3.0F : 1.0F;
            float v2 = atlas ? v + 0.5F : 1.0F;
            Matrix4f matrix = new Matrix4f(view);
            switch (face) {
                case 1 -> matrix.rotateY((float) (Math.PI / 2));
                case 2 -> matrix.rotateY((float) Math.PI);
                case 3 -> matrix.rotateY((float) (-Math.PI / 2));
                case 4 -> matrix.rotateX((float) (Math.PI / 2));
                case 5 -> matrix.rotateX((float) (-Math.PI / 2));
                default -> {}
            }
            buffer.vertex(matrix, -100, 100, -100).texture(u, v).color(color);
            buffer.vertex(matrix, -100, -100, -100).texture(u, v2).color(color);
            buffer.vertex(matrix, 100, -100, -100).texture(u2, v2).color(color);
            buffer.vertex(matrix, 100, 100, -100).texture(u2, v).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        if (cull) RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Unique
    private boolean skyRender$isSixTileAtlas(Identifier texture) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        if (texture.equals(skyRender$cachedTexture) && resourceManager == skyRender$cachedResourceManager) {
            return skyRender$cachedAtlas;
        }
        skyRender$cachedTexture = texture;
        skyRender$cachedResourceManager = resourceManager;
        skyRender$cachedAtlas = false;
        var resource = resourceManager.getResource(texture).orElse(null);
        if (resource == null) return false;
        try (var input = new DataInputStream(resource.getInputStream())) {
            if (input.readLong() != 0x89504E470D0A1A0AL || input.readInt() != 13
                    || input.readInt() != 0x49484452) return false;
            int width = input.readInt();
            int height = input.readInt();
            skyRender$cachedAtlas = width > 0 && height > 0 && width % 3 == 0 && height % 2 == 0
                    && width / 3 == height / 2;
            return skyRender$cachedAtlas;
        } catch (IOException exception) {
            return false;
        }
    }
}
