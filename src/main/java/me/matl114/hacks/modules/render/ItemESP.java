package me.matl114.hacks.modules.render;

import java.util.*;
import javax.swing.*;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.ComponentChanges;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.predicate.NbtPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ItemESP extends BaseModule {
    public final ModulePath detectEntity = makePath(Configs.RENDER_CONFIG, "detect-entity");
    public final ModulePath itemEsp = detectEntity.add("item-esp");

    public ItemESP() {}

    public boolean enable() {
        return enableItem.get() || enableFrame.get();
    }
    // 启用开关

    boolean pendingUpdateEntities = false;
    public List<NbtPredicate> predicate;
    public Set<Item> itemSet = new HashSet<>();

    public void updatePredicate(List<NbtCompound> compound) {
        if (compound == null || compound.isEmpty()) {
            predicate = null;
        } else {
            predicate = compound.stream().map(NbtPredicate::new).toList();
        }
        launchDelayUpdateTask();
    }

    public void updateSet(RegistryRegex<Item> reg) {
        Set<Item> set = reg.getFilterValue();
        if (!Objects.equals(set, itemSet)) {
            itemSet = set;
            launchDelayUpdateTask();
        }
    }

    public FlagRef enableItem = flagBuilder(itemEsp.add("enable-item"))
            .updateListener(s -> launchDelayUpdateTask())
            .build();
    public FlagRef enableFrame = flagBuilder(itemEsp.add("enable-frame"))
            .updateListener(s -> launchDelayUpdateTask())
            .build();
    // NBT 谓词（字符串格式，默认为空）
    public NBTRef<PrimitiveList<NbtCompound>> nbtPredicate = builder(
                    itemEsp.add("nbt-predicate"), PrimitiveList.<NbtCompound>parameter())
            .defaultValue(new PrimitiveList<>(NBTTypes.NBT_COMPOUND_TYPE, List.of()))
            .updateListener(s -> updatePredicate(s.list()))
            .build();

    // 物品类型过滤器（默认识别所有物品）
    public NBTRef<RegistryRegex<Item>> itemType = builder(itemEsp.add("item-type"), RegistryRegex.<Item>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("^(wither_skeleton_skull)$"), Registries.ITEM))
            .updateListener(this::updateSet)
            .build();

    // 颜色（使用 WrapColor，默认绿色）
    public NBTRef<WrapColor> color = builder(itemEsp.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.YELLOW)))
            .build();

    public NBTRef<TracingOption> option = builder(itemEsp.add("options"), TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    // 可选：热键（若需要可取消注释，并实现对应的 KeyBindRef）
    // public KeyBindRef hotkey = keyBindBuilder(Configs.RENDER_CONFIG, HOTKEY).build();

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        launchDelayUpdateTask();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.ITEM), this::handleItemEntityItemData);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.ITEM_FRAME), this::handleItemFrameItemData);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.GLOW_ITEM_FRAME),
                this::handleItemFrameItemData);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRenderEntity);
    }

    public boolean testItem(ItemStack stack) {
        return itemType.get().test(stack.getItem()) || (predicate != null && testItemData(stack));
    }

    private boolean testItemData(ItemStack stack) {
        ComponentChanges changes = stack.getComponentChanges();
        try {
            NbtCompound nbtCompound = changes.isEmpty()
                    ? new NbtCompound()
                    : (NbtCompound) ComponentChanges.CODEC
                            .encodeStart(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), changes)
                            .getOrThrow();
            for (var re : predicate) {
                if (re.test(nbtCompound)) return true;
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    private static final String ITEM_ESP_METADATA_KEY = "slimefunhelper:item_esp_show_key";

    public void markItemToRender(Entity entity) {
        EntityAccess.of(entity).getMetadata().put(this, ITEM_ESP_METADATA_KEY, Unit.INSTANCE);
    }

    public void removeItemFromRender(Entity entity) {
        var access = EntityAccess.of(entity);
        if (!access.isMetaEmpty()) {
            access.getMetadata().put(this, ITEM_ESP_METADATA_KEY, null);
        }
    }

    public void launchDelayUpdateTask() {
        if (!pendingUpdateEntities) {
            pendingUpdateEntities = true;
            Tasks.scheduleRepeated(
                    () -> {
                        if (pendingUpdateEntities) {
                            if (!checkNull()
                                    && (mc.currentScreen == null || mc.currentScreen instanceof HandledScreen<?>)) {
                                pendingUpdateEntities = false;
                                if (enable()) {
                                    for (var entity : mc.world.getEntities()) {
                                        if (entity instanceof ItemEntity item && enableItem.get()) {
                                            onItemEntity(item, item.getStack());
                                        } else if (entity instanceof ItemFrameEntity frame && enableFrame.get()) {
                                            onItemEntity(frame, frame.getHeldItemStack());
                                        }
                                    }
                                }

                                return true;
                            }
                            return false;
                        }
                        return true;
                    },
                    1,
                    1);
        }
    }

    public void onItemEntity(Entity itemEntity, ItemStack stack) {
        if (!stack.isEmpty() && testItem(stack)) {
            markItemToRender(itemEntity);
        } else {
            removeItemFromRender(itemEntity);
        }
    }

    public void handleItemEntityItemData(Event<DataTracker.SerializedEntry<?>> entryUpdateEvent) {
        if (enableItem.get()) {
            var entry = entryUpdateEvent.context();
            if (entry.id() == VDataFlag.ID_ITEM_ITEMSTACK
                    && (entry.value()) instanceof ItemStack stack
                    && entryUpdateEvent.getArgs(0) instanceof ItemEntity item) {
                onItemEntity(item, stack);
            }
        }
    }

    public void handleItemFrameItemData(Event<DataTracker.SerializedEntry<?>> entryUpdateEvent) {
        if (enableFrame.get()) {
            var entry = entryUpdateEvent.context();
            if (entry.id() == VDataFlag.ID_ITEM_FRAME_ITEMSTACK
                    && entry.value() instanceof ItemStack stack
                    && entryUpdateEvent.getArgs(0) instanceof ItemFrameEntity item) {
                onItemEntity(item, stack);
            }
        }
    }

    public void onRenderEntity(Event<MatrixStack> event) {
        if (enable()) {
            MatrixStack stack = event.context();
            List<Box> boxes = new ArrayList<>();
            Vec3d cameraPos = RenderUtils.getCameraPos().negate();
            for (var entity : mc.world.getEntities()) {
                if ((entity instanceof ItemEntity i || entity instanceof ItemFrameEntity)
                        && entity instanceof EntityAccess<?> access
                        && !access.isMetaEmpty()
                        && access.getMetadata().get(this, ITEM_ESP_METADATA_KEY) != null) {
                    boxes.add(entity.getBoundingBox().offset(cameraPos));
                }
            }
            TracingOption op = option.get();
            int color = this.color.get().asRGB();
            if (!boxes.isEmpty()) {
                RenderUtils.startDrawVirtual(stack);
                try {
                    VRender.getInstance().createLinesLayer(((operation, vertexConsumer) -> {
                        if (op.box()) {
                            for (var box2 : boxes) {
                                operation.drawOutlinedBox(
                                        stack,
                                        vertexConsumer,
                                        box2.getMinPos(),
                                        box2.getMaxPos(),
                                        ColorUtils.withAlphaInt(color, 1.0F));
                            }
                        }
                        if (op.line()) {
                            Vec3d traceOrigin = RenderUtils.getTracerOrigin(0.0F);
                            for (var box : boxes) {
                                operation.drawLine(
                                        stack,
                                        vertexConsumer,
                                        traceOrigin,
                                        box.getCenter(),
                                        ColorUtils.withAlphaInt(color, 1.0F));
                            }
                        }
                    }));
                } finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }
}
