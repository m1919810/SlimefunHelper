package me.matl114.events;

import java.util.*;
import lombok.Getter;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.annotations.Cancelable;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.annotations.Modifiable;
import me.matl114.events.channels.EventChannel;
import me.matl114.utils.Debug;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.crash.CrashException;

public class RenderListener {
    public static void init() {}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Getter
    @Modifiable
    @Cancelable
    private static final EventChannel<ItemStack> itemDataOverrideForModel = new EventChannel<>();

    @Getter
    @Modifiable
    @Cancelable
    @ExtraArgs(
            value = {ItemStack.class},
            names = {"originalItemStack"})
    private static final EventChannel<Identifier> customModelOverride = new EventChannel<>();

    public static Identifier wrapAsModModel(Identifier id) {
        return id;
    }

    public static Optional<ItemModel> getModModel(Identifier id) {
        return Optional.ofNullable(getModelOf(wrapAsModModel(id)));
    }

    public static Optional<ItemModel> getOptionalModelOf(Identifier id) {
        return Optional.ofNullable(getModelOf(id));
    }

    public static ItemModel getModelOf(Identifier modeled) {
        return getCustomModelOf(modeled);
    }

    public static ItemModel getCustomModelOf(Identifier identifier) {
        ItemModel model = mc.getBakedModelManager().getItemModel(identifier);
        return model == mc.getBakedModelManager().missingModels.item() ? null : model;
    }

    public static final String RESOURCE_SPECIAL_VARIANT = "fabric_resource";

    @Getter
    @Modifiable
    @Cancelable
    @ExtraArgs(
            value = {ItemStack.class},
            names = {"originItemStack"})
    private static final EventChannel<ItemStack> detachedItemStackInformation = new EventChannel<>();

    public static ItemStack getContainedItemInfo(ItemStack stack) {
        Event<ItemStack> searchEvent = new Event<>(null, true, true, stack);
        detachedItemStackInformation.handleValue(searchEvent);
        if (searchEvent.isCancelled()) {
            return null;
        } else {
            return searchEvent.context();
        }
    }

    // 在屏幕之上渲染的
    @Getter
    @Broadcast
    @ExtraArgs(
            value = {float.class},
            names = {"ticksDelta"})
    private static final EventChannel<MatrixStack> renderLayerTasks = new EventChannel<>();

    public static void renderWorldTasks(MatrixStack stack, float tickDelta) {
        // GL11.glEnable(GL11.GL_LINE_SMOOTH);

        try {
            // This stack start with the position with RenderUtils.getCameraPose();
            Event<MatrixStack> renderEvent = new Event<>(stack, false, false, tickDelta);

            renderLayerTasks.handleValue(renderEvent);
        } catch (ConcurrentModificationException | NullPointerException | CrashException e) {
            Debug.info("Error while handling Render Event:", e.getMessage());
        } finally {
            // GL11.glDisable(GL11.GL_LINE_SMOOTH);
        }
    }

    @Getter
    @Broadcast
    @ExtraArgs(
            value = {HandledScreen.class, Slot.class},
            names = {"renderer", "stack"})
    private static final EventChannel<DrawContext> renderSlot = new EventChannel<>();

    public static void renderSlotInScreen(DrawContext context, HandledScreen<?> renderer, Slot stack) {
        if (renderSlot.isEmpty()) return;
        Event<DrawContext> contextEvent = new Event<>(context, false, false, renderer, stack);
        renderSlot.handleValue(contextEvent);
    }

    @Getter
    @Broadcast
    @ExtraArgs(
            value = {HandledScreen.class, int.class, int.class, float.class},
            names = {"renderer", "mouseX", "mouseY", "delta"})
    private static final EventChannel<DrawContext> renderHandledScreen = new EventChannel<>();

    public static void renderHandledScreen(
            DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY, float delta) {
        if (renderHandledScreen.isEmpty()) {
            return;
        }
        Event<DrawContext> contextEvent = new Event<>(context, false, false, screen, mouseX, mouseY, delta);
        renderHandledScreen.handleValue(contextEvent);
    }

    @Getter
    @Broadcast
    private static final EventChannel<ResourceManager> resourceReload = new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs(value = {ResourceManager.class})
    private static final EventChannel<Set<Identifier>> asyncItemModelSupply = new EventChannel<>();

    public static void onResourceReload(ResourceManager manager) {
        Event<ResourceManager> resourceReloadEvent = new Event<>(manager, false, false);
        resourceReload.handleValue(resourceReloadEvent);
    }

    public static Collection<Identifier> getReloadingResources(ResourceManager manager) {
        Event<Set<Identifier>> resourceReloadEvent = new Event<>(new LinkedHashSet<>(), false, false, manager);
        asyncItemModelSupply.handleValue(resourceReloadEvent);
        return resourceReloadEvent.context();
    }

    @Getter
    @Broadcast
    @ExtraArgs(value = {ResourceManager.class, Identifier.class})
    private static final EventChannel<Set<Identifier>> atlasSourceSupply = new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs(
            value = {ItemStack.class, boolean.class, boolean.class},
            names = {"itemStack", "advance", "creative"})
    private static final EventChannel<List<Text>> tooltipShow = new EventChannel<>();

    @Getter
    @Cancelable
    private static final EventChannel<Entity> entityRenderListener = new EventChannel<>();

    @Getter
    @Cancelable
    private static final EventChannel<BlockEntity> blockEntityRenderListener = new EventChannel<>();

    @Getter
    @Cancelable
    private static final EventChannel<Particle> particleRenderListener = new EventChannel<>();
}
