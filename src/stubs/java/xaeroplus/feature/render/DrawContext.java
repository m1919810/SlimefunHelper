package xaeroplus.feature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import xaero.lib.client.graphics.XaeroBufferProvider;

public record DrawContext(
        PoseStack matrixStack,
        XaeroBufferProvider renderTypeBuffers,
        double fboScale,
        boolean worldmap,
        Matrix4f untranslatedMapViewMatrix,
        int cameraBlockX,
        int cameraBlockZ) {}
