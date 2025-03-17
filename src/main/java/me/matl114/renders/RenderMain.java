package me.matl114.renders;

import me.matl114.access.DrawContextAccess;
import me.matl114.renders.implement.NewVersionModelRender;
import me.matl114.renders.implement.SpawnerRender;
import me.matl114.utils.UtilClass.OrderedSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.data.client.Model;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.*;
import java.util.function.Function;

public class RenderMain {
    public static void init(){
    }
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
    private static final List<OrderedSupplier<ItemStack,Optional<Identifier>>> modelOverrideFunctions = new ArrayList<>();
    //lower goes first
    public static void registerModelOverridePredicate(int priority ,Function<ItemStack,Optional<Identifier>> function) {
        modelOverrideFunctions.add(OrderedSupplier.create(priority, function));
        Collections.sort(modelOverrideFunctions);
    }
    public static void registerModelOverridePredicate(Function<ItemStack,Optional<Identifier>> function) {
        modelOverrideFunctions.add(OrderedSupplier.create(1000, function));
        Collections.sort(modelOverrideFunctions);
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
    public static boolean validateModel(Identifier identifier){
        return getModelOf(identifier)!=null;
    }
    public static BakedModel getModelOf(Identifier identifier){
        BakedModel model;
        if(identifier instanceof ModelIdentifier modeled){
            model = mc.getBakedModelManager().getModel(modeled);
            if(model == null || model ==  mc.getBakedModelManager().getMissingModel()){
                identifier = new Identifier(identifier.getNamespace(), identifier.getPath());
            }else {
                return model;
            }
        }
        model = mc.getBakedModelManager().getModel(identifier);
        if(model == null || model ==  mc.getBakedModelManager().getMissingModel()){
            return null;
        }else {
            return model;
        }
    }
    public static void drawSlotLikeItemAt(DrawContext context, TextRenderer textRenderer, ItemStack item, int x, int y,int depth,float scale, int seed){
        context.getMatrices().push();

       drawItem(context, MinecraftClient.getInstance().player,MinecraftClient.getInstance().world, item,scale, x, y, seed, 0 ,depth);

        context.drawItemInSlot(textRenderer, item, x, y, null);
        context.getMatrices().pop();
    }
    private static final List<OrderedSupplier<ItemStack,ItemStack>> containerInfoSuppilers = new ArrayList<>();
    public static void registerContainerInfoPredicate(Function<ItemStack,ItemStack> func){
        containerInfoSuppilers.add(OrderedSupplier.create(1000, func));
        Collections.sort(containerInfoSuppilers);
    }
    public static void registerContainerInfoPredicate(int priority ,Function<ItemStack,ItemStack> func){
        containerInfoSuppilers.add(OrderedSupplier.create(priority, func));
        Collections.sort(containerInfoSuppilers);
    }

    public static ItemStack getContainedItemInfo(ItemStack stack){
        ItemStack result = null;
        for (OrderedSupplier<ItemStack,ItemStack> supplier : containerInfoSuppilers) {
            if((result = supplier.apply(stack)) != null){
                return result;
            }
        }
        return null;
    }

    static {
        NewVersionModelRender.init();
        SpawnerRender.init();
    }
}
