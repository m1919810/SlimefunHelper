package me.matl114.mixins.hack;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.DataInputStream;
import java.io.IOException;
import me.matl114.hacks.modules.render.SkyRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.math.ColorHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Unique
    private static final int skyRender$FACE_COUNT = 6;

    @Unique
    private VertexBuffer skyRender$backgroundVertices;

    @Unique
    private Identifier skyRender$backgroundTexture;

    @Unique
    private RenderLayer skyRender$backgroundLayer;

    @ModifyArg(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
                            ordinal = 0),
            index = 0)
    private float skyRender$overrideSkyRed(float original) {
        return skyRender$customColorComponent(original, 0);
    }

    @ModifyArg(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
                            ordinal = 0),
            index = 1)
    private float skyRender$overrideSkyGreen(float original) {
        return skyRender$customColorComponent(original, 1);
    }

    @ModifyArg(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
                            ordinal = 0),
            index = 2)
    private float skyRender$overrideSkyBlue(float original) {
        return skyRender$customColorComponent(original, 2);
    }

    @ModifyVariable(method = "renderGlowingSky", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skyRender$hideSunrise(int color) {
        return skyRender$hasSkyOverride() ? 0 : color;
    }

    @Inject(method = "renderSky", at = @At("TAIL"))
    private void skyRender$renderBackground(float red, float green, float blue, CallbackInfo ci) {
        skyRender$drawBackground();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void skyRender$replaceEndSkyWithBackground(CallbackInfo ci) {
        if (skyRender$getBackground() != null) {
            skyRender$drawBackground();
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderEndSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gl/VertexBuffer;draw(Lnet/minecraft/client/render/RenderLayer;)V"))
    private void skyRender$tintVanillaEndSky(VertexBuffer vertexBuffer, RenderLayer layer) {
        var module = SkyRender.INSTANCE;
        if (module == null || !module.hasCustomColor()) {
            vertexBuffer.draw(layer);
            return;
        }
        skyRender$setCustomColor(module.color.get().asRGB());
        try {
            vertexBuffer.draw(layer);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void skyRender$hideDarkSky(CallbackInfo ci) {
        if (skyRender$hasSkyOverride()) {
            ci.cancel();
        }
    }

    @Unique
    private float skyRender$customColorComponent(float original, int component) {
        var module = SkyRender.INSTANCE;
        if (module == null || !module.hasCustomColor()) {
            return original;
        }
        int color = module.color.get().asRGB();
        return switch (component) {
            case 0 -> ColorHelper.getRedFloat(color);
            case 1 -> ColorHelper.getGreenFloat(color);
            case 2 -> ColorHelper.getBlueFloat(color);
            default -> original;
        };
    }

    @Unique
    private boolean skyRender$hasSkyOverride() {
        var module = SkyRender.INSTANCE;
        return module != null && (module.hasCustomColor() || module.getBackground() != null);
    }

    @Unique
    private Identifier skyRender$getBackground() {
        var module = SkyRender.INSTANCE;
        return module != null ? module.getBackground() : null;
    }

    @Unique
    private void skyRender$drawBackground() {
        Identifier texture = skyRender$getBackground();
        if (texture == null) {
            return;
        }
        skyRender$ensureBackgroundVertices(texture);
        var module = SkyRender.INSTANCE;
        if (module != null && module.hasCustomColor()) {
            skyRender$setCustomColor(module.color.get().asRGB());
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        try {
            skyRender$backgroundVertices.draw(skyRender$backgroundLayer);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Unique
    private void skyRender$setCustomColor(int color) {
        RenderSystem.setShaderColor(
                ColorHelper.getRedFloat(color),
                ColorHelper.getGreenFloat(color),
                ColorHelper.getBlueFloat(color),
                1.0F);
    }

    @Unique
    private void skyRender$ensureBackgroundVertices(Identifier texture) {
        if (texture.equals(skyRender$backgroundTexture) && skyRender$backgroundVertices != null) {
            return;
        }
        if (skyRender$backgroundVertices != null) {
            skyRender$backgroundVertices.close();
            skyRender$backgroundVertices = null;
        }
        skyRender$backgroundTexture = null;
        skyRender$backgroundLayer = RenderLayer.of(
                "slimefunhelper:sky_background",
                VertexFormats.POSITION_TEXTURE_COLOR,
                VertexFormat.DrawMode.QUADS,
                1536,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(RenderLayer.POSITION_TEXTURE_COLOR_PROGRAM)
                        .texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
                        .transparency(RenderLayer.TRANSLUCENT_TRANSPARENCY)
                        .writeMaskState(RenderLayer.COLOR_MASK)
                        .build(false));
        boolean sixTileAtlas = skyRender$isSixTileAtlas(texture);
        skyRender$backgroundVertices = VertexBuffer.createAndUpload(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR, builder -> {
                    for (int face = 0; face < skyRender$FACE_COUNT; face++) {
                        int tile =
                                switch (face) {
                                    case 0 -> 4; // North
                                    case 1 -> 5; // West
                                    case 2 -> 2; // South
                                    case 3 -> 3; // East
                                    case 4 -> 1; // Top
                                    case 5 -> 0; // Bottom
                                    default -> throw new IllegalStateException("Unexpected sky face: " + face);
                                };
                        float minU = sixTileAtlas ? (tile % 3) / 3.0F : 0.0F;
                        float maxU = sixTileAtlas ? minU + 1.0F / 3.0F : 1.0F;
                        float minV = sixTileAtlas ? (tile / 3) / 2.0F : 0.0F;
                        float maxV = sixTileAtlas ? minV + 0.5F : 1.0F;
                        Matrix4f matrix = new Matrix4f();
                        switch (face) {
                            case 1 -> matrix.rotationY((float) (Math.PI / 2));
                            case 2 -> matrix.rotationY((float) Math.PI);
                            case 3 -> matrix.rotationY((float) (-Math.PI / 2));
                            case 4 -> matrix.rotationX((float) (Math.PI / 2));
                            case 5 -> matrix.rotationX((float) (-Math.PI / 2));
                            default -> {}
                        }
                        builder.vertex(matrix, -100.0F, 100.0F, -100.0F)
                                .texture(minU, minV)
                                .color(-1);
                        builder.vertex(matrix, -100.0F, -100.0F, -100.0F)
                                .texture(minU, maxV)
                                .color(-1);
                        builder.vertex(matrix, 100.0F, -100.0F, -100.0F)
                                .texture(maxU, maxV)
                                .color(-1);
                        builder.vertex(matrix, 100.0F, 100.0F, -100.0F)
                                .texture(maxU, minV)
                                .color(-1);
                    }
                });
        skyRender$backgroundTexture = texture;
    }

    @Unique
    private boolean skyRender$isSixTileAtlas(Identifier texture) {
        var resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(texture)
                .orElse(null);
        if (resource == null) {
            return false;
        }
        try (var input = new DataInputStream(resource.getInputStream())) {
            if (input.readLong() != 0x89504E470D0A1A0AL || input.readInt() != 13 || input.readInt() != 0x49484452) {
                return false;
            }
            int width = input.readInt();
            int height = input.readInt();
            return width > 0 && height > 0 && width % 3 == 0 && height % 2 == 0 && width / 3 == height / 2;
        } catch (IOException exception) {
            return false;
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void skyRender$closeBackground(CallbackInfo ci) {
        if (skyRender$backgroundVertices != null) {
            skyRender$backgroundVertices.close();
            skyRender$backgroundVertices = null;
        }
        skyRender$backgroundTexture = null;
        skyRender$backgroundLayer = null;
    }
}
