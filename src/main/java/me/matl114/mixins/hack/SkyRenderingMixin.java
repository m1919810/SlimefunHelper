package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.matl114.hacks.modules.render.SkyRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Shadow
    public abstract void renderEndSky();

    @Unique
    private static final int skyRender$FACE_COUNT = 6;

    @Unique
    private GpuBuffer skyRender$backgroundVertices;

    @Unique
    private AbstractTexture skyRender$loadedBackgroundTexture;

    @ModifyExpressionValue(
            method = "renderEndSky",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/render/SkyRendering;endSkyTexture:Lnet/minecraft/client/texture/AbstractTexture;"))
    private AbstractTexture skyRender$backgroundTexture(AbstractTexture original) {
        return skyRender$loadedBackgroundTexture != null ? skyRender$loadedBackgroundTexture : original;
    }

    @ModifyArg(
            method = "renderEndSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/systems/RenderPass;setVertexBuffer(ILcom/mojang/blaze3d/buffers/GpuBuffer;)V"),
            index = 1)
    private GpuBuffer skyRender$selectBackgroundVertices(GpuBuffer original) {
        return skyRender$loadedBackgroundTexture != null && skyRender$backgroundVertices != null
                ? skyRender$backgroundVertices
                : original;
    }

    @ModifyArg(
            method = "renderEndSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gl/DynamicUniforms;write(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1)
    private Vector4fc tintEndSky(Vector4fc original) {
        var module = SkyRender.INSTANCE;
        return module != null && module.hasCustomColor()
                ? ColorHelper.toRgbaVector(module.color.get().withAlpha(255))
                : original;
    }

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void updateCustomSky(
            ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        var module = SkyRender.INSTANCE;
        var background = module != null ? module.getBackground() : null;
        skyRender$loadedBackgroundTexture = background != null
                ? MinecraftClient.getInstance().getTextureManager().getTexture(background)
                : null;
        if (skyRender$loadedBackgroundTexture != null && skyRender$backgroundVertices == null) {
            skyRender$backgroundVertices = skyRender$createBackgroundVertices();
        }
        if (module == null) return;
        if (module.hasCustomColor()) {
            state.skyColor = module.color.get().withAlpha(255);
        }
        if (module.hasCustomColor() || skyRender$loadedBackgroundTexture != null) {
            state.sunriseAndSunsetColor = 0;
            state.shouldRenderSkyDark = false;
        }
    }

    @Inject(method = "renderTopSky", at = @At("TAIL"))
    private void renderCustomBackground(int color, CallbackInfo ci) {
        if (skyRender$loadedBackgroundTexture != null) {
            renderEndSky();
        }
    }

    @Unique
    private GpuBuffer skyRender$createBackgroundVertices() {
        try (var allocator = BufferAllocator.fixedSized(
                skyRender$FACE_COUNT * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            var builder =
                    new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int face = 0; face < skyRender$FACE_COUNT; face++) {
                var matrix = new Matrix4f();
                switch (face) {
                    case 1 -> matrix.rotationY((float) (Math.PI / 2));
                    case 2 -> matrix.rotationY((float) Math.PI);
                    case 3 -> matrix.rotationY((float) (-Math.PI / 2));
                    case 4 -> matrix.rotationX((float) (Math.PI / 2));
                    case 5 -> matrix.rotationX((float) (-Math.PI / 2));
                }
                builder.vertex(matrix, -100, 100, -100).texture(0, 0).color(-1);
                builder.vertex(matrix, -100, -100, -100).texture(0, 1).color(-1);
                builder.vertex(matrix, 100, -100, -100).texture(1, 1).color(-1);
                builder.vertex(matrix, 100, 100, -100).texture(1, 0).color(-1);
            }
            try (var built = builder.end()) {
                return RenderSystem.getDevice().createBuffer(() -> "SkyRender background", 32, built.getBuffer());
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void closeCustomBackground(CallbackInfo ci) {
        skyRender$loadedBackgroundTexture = null;
        if (skyRender$backgroundVertices != null) {
            skyRender$backgroundVertices.close();
            skyRender$backgroundVertices = null;
        }
    }
}
