package me.matl114.renders;

import lombok.Getter;
import me.matl114.renders.implement.EnchantmentRender;
import me.matl114.renders.implement.NewVersionModelRender;
import me.matl114.renders.implement.SlimefunRender;
import me.matl114.renders.implement.SpawnerRender;
import me.matl114.utils.UtilClass.ArgumentListenerPoint;
import me.matl114.utils.UtilClass.ListenerPoint;
import me.matl114.utils.UtilClass.OrderedSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.function.Function;

public class RenderMain {
    public static void init(){
    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();

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
    public static boolean shouldStopVanillaColoring(ItemStack item){
        return NewVersionModelRender.isNewVersion(item);
    }
    public static ItemColorProvider getSpecificItemColorProvider(ItemConvertible[] item){
        return null;
//            ((stack, tintIndex) -> {
//            return -999;
//        });
    }
    @Getter
    private static final ListenerPoint<MatrixStack> renderTasks = new ListenerPoint<>();
    public static void renderMoreTasks(MatrixStack stack){
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        renderTasks.handleValue(stack);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }
    @Getter
    private static final ArgumentListenerPoint<DrawContext> renderSlot = new ArgumentListenerPoint<>();
    public static void renderSlotInScreen(DrawContext context, HandledScreen<?> renderer, Slot stack, int mouseX, int mouseY){
        renderSlot.handleValue(context, renderer, stack, mouseX, mouseY);
    }

    @Getter
    private static final ArgumentListenerPoint<DrawContext> renderHandledScreen = new ArgumentListenerPoint<>();
    public static void renderHandledScreen(DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY, float delta){
        renderHandledScreen.handleValue(context, screen, mouseX, mouseY, delta);
    }

//    @Getter
//    private static final ArgumentListenerPoint<DrawContext> renderToolTips = new ArgumentListenerPoint<>();
//    public static void renderItemTooltipsTasks(DrawContext context, TextRenderer renderer, ItemStack stack, int x, int y){
//        renderToolTips.handleValue(context, renderer, stack, x, y);
//    }


    static {
        NewVersionModelRender.init();
        SpawnerRender.init();
        EnchantmentRender.init();
        SlimefunRender.init();
    }
}
