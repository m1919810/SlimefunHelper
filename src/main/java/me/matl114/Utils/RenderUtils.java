package me.matl114.Utils;

import me.matl114.Access.DrawContextAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class RenderUtils {
    public static void drawItem(DrawContext context, @Nullable LivingEntity entity, @Nullable World world, ItemStack stack,float scale, int x, int y, int seed, int z,int dz) {
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
}
