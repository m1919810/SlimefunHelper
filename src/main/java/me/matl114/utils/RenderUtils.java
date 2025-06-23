package me.matl114.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import me.matl114.access.DrawContextAccess;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;

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
            crashReportSection.add("Item NBT", () -> String.valueOf(stack.getNbt()));
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

    public static Vec3d getCameraPos(){
        var d = mc.getBlockEntityRenderDispatcher().camera;
        return d == null? Vec3d.ZERO:d.getPos();
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
    public static void drawLineVirtual(MatrixStack matrixStack, ArrayList<Vec3d> path,
                         Color color)
    {

        if(path.isEmpty())
            return;
        Vec3d camPos = RenderUtils.getCameraPos();
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionProgram);

        bufferBuilder.begin(
            VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION);
        setAsShaderColor(color,1.0F);

        for(Vec3d point : path){
            bufferBuilder.vertex(matrix, (float)(point.x - camPos.x),
                (float)(point.y - camPos.y), (float)(point.z - camPos.z)).next();

        }
        tessellator.draw();
    }
    public static void setAsShaderColor(Color color, float opacity){
        RenderSystem.setShaderColor(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }

}
