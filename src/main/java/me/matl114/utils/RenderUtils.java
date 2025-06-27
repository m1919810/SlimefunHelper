package me.matl114.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import me.matl114.access.DrawContextAccess;
import me.matl114.utils.UtilClass.RegionPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RenderUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static void drawItem(DrawContext context, @Nullable LivingEntity entity, @Nullable World world, ItemStack stack, float scale, int x, int y, int seed, int z, int dz) {
        if (stack.isEmpty()) {
            return;
        }
        DrawContextAccess access=DrawContextAccess.of(context);
        BakedModel bakedModel =access.getMinecraftClient().getItemRenderer().getModel(stack, world, entity, seed);
        access.getMatrixStack().push();
        access.getMatrixStack().translate(x + 8, y + 8, 150+dz + (bakedModel.hasDepth() ? z : 0));
        try {
            boolean bl;
            access.getMatrixStack().multiplyPositionMatrix(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
            access.getMatrixStack().scale(16.0f*scale, 16.0f*scale, 16.0f*scale);
            boolean bl2 = bl = !bakedModel.isSideLit();
            if (bl) {
                DiffuseLighting.disableGuiDepthLighting();
            }
            access.getMinecraftClient().getItemRenderer().renderItem(stack, ModelTransformationMode.GUI, false, access.getMatrixStack(), context.getVertexConsumers(), 0xF000F0, OverlayTexture.DEFAULT_UV, bakedModel);
            context.draw();
            if (bl) {
                DiffuseLighting.enableGuiDepthLighting();
            }
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.create((Throwable)throwable, (String)"Rendering item");
            CrashReportSection crashReportSection = crashReport.addElement("Item being rendered");
            crashReportSection.add("Item Type", () -> String.valueOf(stack.getItem()));
            crashReportSection.add("Item Damage", () -> String.valueOf(stack.getDamage()));
            crashReportSection.add("Item Foil", () -> String.valueOf(stack.hasGlint()));
            throw new CrashException(crashReport);
        }
        access.getMatrixStack().pop();
    }
    public static void drawSlotLikeItemAt(DrawContext context, TextRenderer textRenderer, ItemStack item, int x, int y, int depth, float scale, int seed){
        context.getMatrices().push();

        drawItem(context, MinecraftClient.getInstance().player,MinecraftClient.getInstance().world, item,scale, x, y, seed, 0 ,depth);

        context.drawItemInSlot(textRenderer, item, x, y, null);
        context.getMatrices().pop();
    }
    //说明：
    //LINES 两点绘制一个线段
    //LINE_STRIP 折线
    //TRIANGLES 三角型
    //TRIANGLE_STRIP 每个三角行和前一个三角行共享两个顶点
    //TRIANGLE_FAN 三角行扇
    //QUADS 四边形

    //VertexFormats要和shader匹配以及和vertex的参数匹配
    //比如PositionColor就要bufferbuilder.vertex.color


    //vertex似乎是用来画线和面的

    //vertexBuffer可以缓存buffer的行为，可以在不同的变换矩阵下重复使用， 使用bind();draw(viewMatrix, projMatrix, shader);unbind();
    //projMatrix从RenderSystem.getProjectionMatrix();获取, shader从RenderSystem.getShader();获取, viewMatrix是正常传参中的玩家位置matrixStack.position

    public static Vec3d getCameraPos(){
        var d = mc.getBlockEntityRenderDispatcher().camera;
        return d == null? Vec3d.ZERO:d.getPos();
    }
    public static BlockPos getCameraBlockPos()
    {
        Camera camera = mc.getBlockEntityRenderDispatcher().camera;
        if(camera == null)
            return BlockPos.ORIGIN;

        return camera.getBlockPos();
    }

    public static Vec3d getClientLookVec(float partialTicks){
        if(mc.player == null)return Vec3d.ZERO;
        return mc.player.getRotationVec(partialTicks);
    }

    public static RegionPos getCameraRegion()
    {
        return RegionPos.of(getCameraBlockPos());
    }
    public static void applyRegionalRenderOffset(MatrixStack matrixStack,
                                                 RegionPos region)
    {
        Vec3d offset = region.toVec3d().subtract(getCameraPos());
        matrixStack.translate(offset.x, offset.y, offset.z);
    }
    /**
     * note: start mush be pair with stop!
     * @param matrixStack
     */
    public static void startDrawVirtual(MatrixStack matrixStack){
        matrixStack.push();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
    }
    public static void stopDrawVirtual(MatrixStack matrixStack){
        RenderSystem.setShaderColor(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        matrixStack.pop();
    }
    //in world coord
    public static void drawStripLineVirtual(MatrixStack matrixStack, List<Vec3d> path,
                                            Color color)
    {
        drawVertexFormatPosition(matrixStack, VertexFormat.DrawMode.DEBUG_LINE_STRIP, path, color);
    }
    //in world coord
    public static void drawVertexFormatPosition(MatrixStack matrixStack, VertexFormat.DrawMode mode, List<Vec3d> path, Color color){
        if(path.isEmpty())
            return;
        Vec3d camPos = RenderUtils.getCameraPos();
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(mode, VertexFormats.POSITION);
        setAsShaderColor(color,1.0F);

        for(Vec3d point : path){
            bufferBuilder.vertex(matrix, (float)(point.x - camPos.x),
                (float)(point.y - camPos.y), (float)(point.z - camPos.z));

        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }
    public static void drawVertexFormatPositionCameraCoord(MatrixStack matrixStack, VertexFormat.DrawMode mode, List<Vec3d> path, Color color){
        if(path.isEmpty())
            return;
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(mode, VertexFormats.POSITION);
        setAsShaderColor(color,1.0F);
        for(Vec3d point : path){
            bufferBuilder.vertex(matrix, (float) point.x, (float) point.y, (float) point.z);

        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public static void drawVertexFromTo(Matrix4f positionMatrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to){
        bufferBuilder.vertex(positionMatrix,(float) from.x,(float) from.y,(float)  from.z);
          //  .color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        bufferBuilder.vertex(positionMatrix,(float) to.x, (float) to.y, (float) to.z);
          // .color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
    //in world coord
    public static void drawLineVirtual(MatrixStack matrixStack, Vec3d from, Vec3d to, Color color){
        drawLineVirtual(matrixStack, List.of(from, to), color);
    }
    public static void drawLineVirtualCameraCoord(MatrixStack matrixStack, Vec3d from, Vec3d to, Color color){
        drawLineVirtualCameraCoord(matrixStack, List.of(from, to), color);
    }
    //in world coord
    public static void drawLineVirtual(MatrixStack matrixStack, List<Vec3d> pairs, Color color){
        drawVertexFormatPosition(matrixStack, VertexFormat.DrawMode.DEBUG_LINES, pairs, color);
    }
    public static void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color){
        drawVertexFormatPositionCameraCoord(matrixStack, VertexFormat.DrawMode.DEBUG_LINES, pairs, color);
    }


    public static void cacheVertexAction(VertexBuffer vertexBuffer, VertexFormat.DrawMode mode, VertexFormat format ,Consumer<BufferBuilder> action){
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tessellator.begin(mode, format);
        action.accept(bufferBuilder);
        BuiltBuffer builtBuffer = bufferBuilder.endNullable();
        if(builtBuffer != null){
            //upload datas
            vertexBuffer.bind();
            vertexBuffer.upload(builtBuffer);
            VertexBuffer.unbind();
        }
    }
    public static void drawOutlinedBox(Matrix4f matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to){
        float minX = (float)from.getX();
        float minY = (float)from.getY();
        float minZ = (float)from.getZ();
        float maxX = (float)to.getX();
        float maxY = (float)to.getY();
        float maxZ = (float)to.getZ();
        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, minZ);

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);

        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);
    }
    public static void drawSolidBox(Matrix4f matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to)
    {
        float minX = (float)from.x;
        float minY = (float)from.y;
        float minZ = (float)from.z;
        float maxX = (float)to.x;
        float maxY = (float)to.y;
        float maxZ = (float)to.z;

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, minZ);

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);

        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);
    }
    public static void drawOutlinedBox(BufferBuilder bufferBuilder, Vec3d from, Vec3d to){
        float minX = (float)from.getX();
        float minY = (float)from.getY();
        float minZ = (float)from.getZ();
        float maxX = (float)to.getX();
        float maxY = (float)to.getY();
        float maxZ = (float)to.getZ();
        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, minZ);

        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, maxZ);

        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(minX, minY, maxZ);

        bufferBuilder.vertex( minX, minY, maxZ);
        bufferBuilder.vertex(minX, minY, minZ);

        bufferBuilder.vertex( minX, minY, minZ);
        bufferBuilder.vertex(minX, maxY, minZ);

        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);

        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);

        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);

        bufferBuilder.vertex(minX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);

        bufferBuilder.vertex(maxX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);

        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);

        bufferBuilder.vertex(minX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, minZ);
    }
    public static void drawSolidBox(BufferBuilder bufferBuilder, Vec3d from, Vec3d to)
    {
        float minX = (float)from.x;
        float minY = (float)from.y;
        float minZ = (float)from.z;
        float maxX = (float)to.x;
        float maxY = (float)to.y;
        float maxZ = (float)to.z;

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(minX, minY, maxZ);

        bufferBuilder.vertex(minX, maxY, minZ);
        bufferBuilder.vertex(minX, maxY, maxZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(maxX, maxY, minZ);

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(minX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);
        bufferBuilder.vertex(maxX, minY, minZ);

        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(maxX, minY, maxZ);

        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, minZ);
    }
    public static void drawOutlinedBox(Matrix4f matrix, Vec3d from, Vec3d to){
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        drawOutlinedBox(matrix, bufferBuilder, from, to);
//        Vec3d vec3d = new Vec3d(matrix.transformPosition((float) from.x, (float) from.y, (float) from.z, new Vector3f()));
//        Vec3d vec3d1 = new Vec3d(matrix.transformPosition((float) to.x, (float) to.y, (float) to.z, new Vector3f()));
//        drawOutlinedBox(bufferBuilder, vec3d, vec3d1);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }
    public static void drawSolidBox(Matrix4f matrix, Vec3d from, Vec3d to){
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawSolidBox(matrix, bufferBuilder, from, to);
//        Vec3d vec3d = new Vec3d(matrix.transformPosition((float) from.x, (float) from.y, (float) from.z, new Vector3f()));
//        Vec3d vec3d1 = new Vec3d(matrix.transformPosition((float) to.x, (float) to.y, (float) to.z, new Vector3f()));
//        drawOutlinedBox(bufferBuilder, vec3d, vec3d1);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }
    public static void setAsShaderColor(Color color, float opacity){
        RenderSystem.setShaderColor(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }

}
