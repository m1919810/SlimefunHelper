package me.matl114.renders;

import lombok.Getter;
import me.matl114.access.BakedModelManagerAccess;
import me.matl114.renders.implement.EnchantmentRender;
import me.matl114.renders.implement.NewVersionModelRender;
import me.matl114.renders.implement.SlimefunRender;
import me.matl114.renders.implement.SpawnerRender;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.Event;
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

    private static final List<OrderedSupplier<ItemStack,Optional<ModelIdentifier>>> modelOverrideFunctions = new ArrayList<>();
    //lower goes first
    public static void registerModelOverridePredicate(int priority ,Function<ItemStack,Optional<ModelIdentifier>> function) {
        modelOverrideFunctions.add(OrderedSupplier.create(priority, function));
        Collections.sort(modelOverrideFunctions);
    }
    public static void registerModelOverridePredicate(Function<ItemStack,Optional<ModelIdentifier>> function) {
        modelOverrideFunctions.add(OrderedSupplier.create(1000, function));
        Collections.sort(modelOverrideFunctions);
    }
    //todo: Model Override by json, use Event
    public static Optional<BakedModel> getCustomItemModel(ItemStack stack) {
        ModelIdentifier overrides = null;
        BakedModel model = null;
        for(Function<ItemStack,Optional<ModelIdentifier>> function : modelOverrideFunctions){
            var re = function.apply(stack);
            if(re.isPresent()){
                overrides = re.get();
                model = getModelOf(overrides);
                if(model != null)return Optional.of(model);
            }
        }
        return Optional.empty();
    }
    public static boolean validateCustomModel(Identifier identifier){
        return getCustomModelOf(identifier)!=null;
    }
    public static BakedModel getModelOf(ModelIdentifier modeled){
        BakedModel model;
        model = mc.getBakedModelManager().getModel(modeled);
        if(model == null || model ==  mc.getBakedModelManager().getMissingModel()){
            return getCustomModelOf(modeled.id());
        }else {
            return model;
        }
    }
    public static BakedModel getCustomModelOf(Identifier identifier){
        BakedModel model = mc.getBakedModelManager().getModel(identifier);
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
    public static final String RESOURCE_SPECIAL_VARIANT = "fabric_resource";
    public static ModelIdentifier wrapAsModel(Identifier id){
        return new ModelIdentifier(id, RESOURCE_SPECIAL_VARIANT);
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
    //在屏幕之上渲染的
    @Getter
    private static final ListenerPoint<Event<MatrixStack>> renderLayerTasks = new ListenerPoint<>();
    public static void renderMoreTasks(MatrixStack stack, float tickDelta){
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
//        GL11.glEnable(GL11.GL_BLEND);
//        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // This stack start with the position with RenderUtils.getCameraPose();
        Event<MatrixStack> renderEvent = new Event<>(stack, false, false, tickDelta);
        //TODO: fix this with event
        renderLayerTasks.handleValue(renderEvent);
//        GL11.glEnable(GL11.GL_DEPTH_TEST);
//        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);

    }
    @Getter
    private static final ListenerPoint<Event<DrawContext>> renderSlot = new ListenerPoint<>();
    public static void renderSlotInScreen(DrawContext context, HandledScreen<?> renderer, Slot stack, int mouseX, int mouseY){
        if(renderSlot.isEmpty())return;
        Event<DrawContext> contextEvent = new Event<>(context, false, false, renderer, stack, mouseX, mouseY);
        renderSlot.handleValue(contextEvent);
    }

    @Getter
    private static final ListenerPoint<Event<DrawContext>> renderHandledScreen = new ListenerPoint<>();
    public static void renderHandledScreen(DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY, float delta){
        if(renderHandledScreen.isEmpty()){
            return;
        }
        Event<DrawContext> contextEvent = new Event<>(context, false, false, screen, mouseX, mouseY, delta);
        renderHandledScreen.handleValue(contextEvent);
    }

//    @Getter
//    private static final ArgumentListenerPoint<DrawContext> renderToolTips = new ArgumentListenerPoint<>();
//    public static void renderItemTooltipsTasks(DrawContext context, TextRenderer renderer, ItemStack stack, int x, int y){
//        renderToolTips.handleValue(context, renderer, stack, x, y);
//    }

    public static void modelDebug(){
        Debug.info(BakedModelManagerAccess.of(MinecraftClient.getInstance().getBakedModelManager()).getAllBakedModels().keySet());
    }


    static {
        NewVersionModelRender.init();
        SpawnerRender.init();
        EnchantmentRender.init();
        SlimefunRender.init();
    }
}
