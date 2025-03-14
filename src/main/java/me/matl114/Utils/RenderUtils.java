package me.matl114.Utils;

import com.mojang.datafixers.util.Pair;
import me.matl114.Access.DrawContextAccess;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class RenderUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
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
    private static final List<Function<ItemStack,Optional<Identifier>>> modelOverrideFunctions = new ArrayList<>();
    public static void registerModelOverridePredicate(Function<ItemStack,Optional<Identifier>> function) {
        modelOverrideFunctions.add(function);
    }
    public static Optional<BakedModel> getCustomItemModel(ItemStack stack) {

        Identifier overrides = null;
        BakedModel model = null;
        for(Function<ItemStack,Optional<Identifier>> function : modelOverrideFunctions){
            var re = function.apply(stack);
            if(re.isPresent()){
                overrides = re.get();
                if(overrides instanceof ModelIdentifier modeled){
                    model = mc.getBakedModelManager().getModel(modeled);
                    if(model == null || model ==  mc.getBakedModelManager().getMissingModel()){
                        overrides = new Identifier(overrides.getNamespace(), overrides.getPath());
                    }else {
                        return Optional.of(model);
                    }
                }
                model = mc.getBakedModelManager().getModel(overrides);
                if(model == null || model ==  mc.getBakedModelManager().getMissingModel()){
                    continue;
                }else {
                    return Optional.of(model);
                }
            }
        }
        return Optional.empty();
    }
    public static void drawSlotLikeItemAt(DrawContext context, TextRenderer textRenderer, ItemStack item, int x, int y,int depth,float scale, int seed){
        context.getMatrices().push();

       drawItem(context, MinecraftClient.getInstance().player,MinecraftClient.getInstance().world, item,scale, x, y, seed, 0 ,depth);

        context.drawItemInSlot(textRenderer, item, x, y, null);
        context.getMatrices().pop();
    }
}
