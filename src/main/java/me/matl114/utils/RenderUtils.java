package me.matl114.utils;

import java.awt.*;
import java.util.List;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.world.RegionPos;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public class RenderUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // 说明：
    // LINES 两点绘制一个线段
    // LINE_STRIP 折线
    // TRIANGLES 三角型
    // TRIANGLE_STRIP 每个三角行和前一个三角行共享两个顶点
    // TRIANGLE_FAN 三角行扇
    // QUADS 四边形

    // VertexFormats要和shader匹配以及和vertex的参数匹配
    // 比如PositionColor就要bufferbuilder.vertex.color

    // vertex似乎是用来画线和面的

    // vertexBuffer可以缓存buffer的行为，可以在不同的变换矩阵下重复使用， 使用bind();draw(viewMatrix, projMatrix, shader);unbind();
    // projMatrix从RenderSystem.getProjectionMatrix();获取, shader从RenderSystem.getShader();获取,
    // viewMatrix是正常传参中的玩家位置matrixStack.position
    public static Vec3d getCameraPos() {
        var d = mc.gameRenderer.getCamera();
        return d == null ? Vec3d.ZERO : d.getCameraPos();
    }

    public static BlockPos getCameraBlockPos() {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return BlockPos.ORIGIN;

        return camera.getBlockPos();
    }

    public static Vec3d getClientLookVec(float partialTicks) {
        if (mc.player == null) return Vec3d.ZERO;
        return mc.player.getRotationVec(partialTicks);
    }

    public static Vec3d getTracerOrigin(float partialTicks) {
        Vec3d start = getClientLookVec(partialTicks).multiply(10);
        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) start = start.negate();

        return start;
    }

    public static RegionPos getCameraRegion() {
        return RegionPos.of(getCameraBlockPos());
    }

    public static void applyRegionalRenderOffset(MatrixStack matrixStack, RegionPos region) {
        Vec3d offset = region.toVec3d().subtract(getCameraPos());
        matrixStack.translate(offset.x, offset.y, offset.z);
    }
    /**
     * note: start mush be pair with stop!
     * @param matrixStack
     */
    public static void startDrawVirtual(MatrixStack matrixStack) {
        matrixStack.push();
        //        GL11.glEnable(GL11.GL_BLEND);
        //        //remove this
        ////        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        //        GL11.glDisable(GL11.GL_DEPTH_TEST);
        //        GL11.glDepthMask(false);
    }

    public static void stopDrawVirtual(MatrixStack matrixStack) {
        setAsCurrentShaderColor(Color.WHITE, 1.0f);
        //        GL11.glDisable(GL11.GL_BLEND);
        //        GL11.glEnable(GL11.GL_DEPTH_TEST);
        //        GL11.glDepthMask(true);
        matrixStack.pop();
    }
    // in world coord
    public static void drawStripLineVirtual(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        if (path.size() < 2) return;
        Vec3d vec3d = getCameraPos();
        VRender.getInstance()
                .drawStripLineVirtualCameraCoord(
                        matrixStack, path.stream().map(v -> v.subtract(vec3d)).toList(), color);
    }

    public static void drawStripLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        VRender.getInstance().drawStripLineVirtualCameraCoord(matrixStack, path, color);
    }

    // in world coord
    public static void drawLineVirtual(MatrixStack matrixStack, Vec3d from, Vec3d to, Color color) {
        drawLineVirtual(matrixStack, List.of(from, to), color);
    }

    public static void drawLineVirtualCameraCoord(MatrixStack matrixStack, Vec3d from, Vec3d to, Color color) {
        drawLineVirtualCameraCoord(matrixStack, List.of(from, to), color);
    }
    // in world coord
    public static void drawLineVirtual(MatrixStack matrixStack, List<Vec3d> pairs, Color color) {
        if (pairs.size() < 2) return;
        Vec3d vec3d = getCameraPos();
        VRender.getInstance()
                .drawLineVirtualCameraCoord(
                        matrixStack, pairs.stream().map(v -> v.subtract(vec3d)).toList(), color);
    }

    public static void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color) {
        VRender.getInstance().drawLineVirtualCameraCoord(matrixStack, pairs, color);
    }

    public static void drawOutlinedBox(MatrixStack matrix, Vec3d from, Vec3d to) {
        Vec3d vec3d = getCameraPos();
        drawOutlinedBoxCameraCoord(matrix, from.subtract(vec3d), to.subtract(vec3d));
    }

    public static void drawOutlinedBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to) {
        VRender.getInstance().drawOutlinedBoxCameraCoord(matrix, from, to);
    }

    public static void drawSolidBox(MatrixStack matrix, Vec3d from, Vec3d to) {
        Vec3d vec3d = getCameraPos();
        VRender.getInstance().drawSolidBoxCameraCoord(matrix, from.subtract(vec3d), to.subtract(vec3d));
    }

    public static void drawQuadCameraCoord(MatrixStack matrix4f, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Color color) {
        VRender.getInstance().drawQuadCameraCoord(matrix4f, new Quad(a, b, c, d), ColorQuad.of(color.getRGB()));
    }

    public static void drawQuad(MatrixStack matrix4f, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Color color) {
        Vec3d vec3d = getCameraPos();
        drawQuadCameraCoord(
                matrix4f, a.subtract(vec3d), b.subtract(vec3d), c.subtract(vec3d), d.subtract(vec3d), color);
    }

    public static void setAsCurrentShaderColor(Color color, float opacity) {
        VRender.getInstance().setAsShaderColor(color, opacity);
    }

    public static void resetCurrentShaderColor() {
        VRender.getInstance().setAsShaderColor(Color.WHITE, 1.0F);
    }

    public static Box getLerpedBox(Entity e, float partialTicks) {
        // When an entity is removed, it stops moving and its lastRenderX/Y/Z
        // values are no longer updated.
        if (e.isRemoved()) return e.getBoundingBox();

        Vec3d offset = getLerpedPos(e, partialTicks).subtract(e.getPos());
        return e.getBoundingBox().offset(offset);
    }

    public static Vec3d getLerpedPos(Entity e, float partialTicks) {
        // When an entity is removed, it stops moving and its lastRenderX/Y/Z
        // values are no longer updated.
        if (e.isRemoved()) return e.getPos();

        double x = MathHelper.lerp(partialTicks, e.lastRenderX, e.getX());
        double y = MathHelper.lerp(partialTicks, e.lastRenderY, e.getY());
        double z = MathHelper.lerp(partialTicks, e.lastRenderZ, e.getZ());
        return new Vec3d(x, y, z);
    }

    public static Vec3d getLerpedDelta(Entity e, float partialTicks) {
        return getLerpedPos(e, partialTicks).subtract(e.getPos());
    }

    public static Quaternionf getBillboardRotation(DisplayEntity.BillboardMode renderState, float pitch, float yaw) {
        Quaternionf rotation = new Quaternionf();
        Camera camera = mc.gameRenderer.getCamera();
        Quaternionf var10000;
        switch (renderState) {
            case FIXED -> var10000 = rotation.rotationYXZ(-0.017453292F * yaw, 0.017453292F * pitch, 0.0F);
            case HORIZONTAL -> var10000 =
                    rotation.rotationYXZ(-0.017453292F * yaw, 0.017453292F * getNegatedPitch(camera.getPitch()), 0.0F);
            case VERTICAL -> var10000 =
                    rotation.rotationYXZ(-0.017453292F * getBackwardsYaw(camera.getYaw()), 0.017453292F * pitch, 0.0F);
            case CENTER -> var10000 = rotation.rotationYXZ(
                    -0.017453292F * getBackwardsYaw(camera.getYaw()),
                    0.017453292F * getNegatedPitch(camera.getPitch()),
                    0.0F);
            default -> throw new MatchException((String) null, (Throwable) null);
        }

        return var10000;
    }

    private static float getBackwardsYaw(float yaw) {
        return yaw - 180.0F;
    }

    private static float getNegatedPitch(float pitch) {
        return -pitch;
    }

    public static VertexConsumer getSpriteVertexConsumer(VertexConsumer vertexConsumer, Sprite sprite) {
        return new SpriteTexturedVertexConsumer(vertexConsumer, sprite);
    }

    public static class SpriteTexturedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Sprite sprite;

        public SpriteTexturedVertexConsumer(VertexConsumer delegate, Sprite sprite) {
            this.delegate = delegate;
            this.sprite = sprite;
        }

        public VertexConsumer vertex(float x, float y, float z) {
            this.delegate.vertex(x, y, z);
            return this;
        }

        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.delegate.color(red, green, blue, alpha);
            return this;
        }

        public VertexConsumer color(int argb) {
            this.delegate.color(argb);
            return this;
        }

        public VertexConsumer texture(float u, float v) {
            this.delegate.texture(this.sprite.getFrameU(u), this.sprite.getFrameV(v));
            return this;
        }

        public VertexConsumer overlay(int u, int v) {
            this.delegate.overlay(u, v);
            return this;
        }

        public VertexConsumer light(int u, int v) {
            this.delegate.light(u, v);
            return this;
        }

        public VertexConsumer normal(float x, float y, float z) {
            this.delegate.normal(x, y, z);
            return this;
        }

        public VertexConsumer lineWidth(float width) {
            this.delegate.lineWidth(width);
            return this;
        }

        public void vertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int overlay,
                int light,
                float normalX,
                float normalY,
                float normalZ) {
            this.delegate.vertex(
                    x,
                    y,
                    z,
                    color,
                    this.sprite.getFrameU(u),
                    this.sprite.getFrameV(v),
                    overlay,
                    light,
                    normalX,
                    normalY,
                    normalZ);
        }
    }
}
