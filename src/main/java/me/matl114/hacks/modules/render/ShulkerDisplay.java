package me.matl114.hacks.modules.render;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.List;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.RenderScreen;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.elements.ColorLabelElement;
import me.matl114.gui.elements.PlateElement;
import me.matl114.gui.elements.SlotElement;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.config.WidgetPos;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.inventory.ItemStackSample;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.TextColor;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

public class ShulkerDisplay extends BaseModule {
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int CONTENT_WIDTH = COLUMNS * SLOT_SIZE;
    private static final int CONTENT_HEIGHT = ROWS * SLOT_SIZE;
    private static final int BORDER_SIZE = 5;
    private static final int PREVIEW_WIDTH = CONTENT_WIDTH + BORDER_SIZE * 2;
    private static final int PREVIEW_HEIGHT = CONTENT_HEIGHT + BORDER_SIZE * 2;

    public final ModulePath root = makePath(Configs.RENDER_CONFIG, "itemstack-display.shulker-display");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final EnumRef<PreviewMode> previewMode = builder(root.add("preview-mode"), PreviewMode.class)
            .defaultValue(PreviewMode.TEXTURE)
            .build();

    public final NBTRef<OptionalPrimitive<TextColor>> color = builder(
                    root.add("color"), OptionalPrimitive.type(TextColor.class))
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.COLOR_TYPE, ColorUtils.color(Formatting.WHITE)))
            .build();

    public final IntRef alpha = builder(root.add("alpha"), IntRef.TYPE)
            .defaultValue(128)
            .validator(Configs.intRange(0, 255))
            .build();

    public final EnumRef<ShowMode> showMode = builder(root.add("show-mode"), ShowMode.class)
            .defaultValue(ShowMode.TOOLTIPS)
            .build();

    public final FlagRef filterEmpty =
            builder(root.add("filter-empty"), Boolean.class).defaultValue(false).build();

    public final NBTRef<WidgetPos> renderPos = builder(root.add("render-pos"), WidgetPos.class)
            .defaultValue(new WidgetPos(0, 0.0D, 0.0D, 0, 0))
            .build();

    public final NBTRef<WidgetPos> followOffset = builder(root.add("follow-offset"), WidgetPos.class)
            .defaultValue(new WidgetPos(1, 0, 0, -PREVIEW_WIDTH / 2, -PREVIEW_HEIGHT - 18))
            .build();

    private final Map<Slot, List<ItemStack>> compressedContents = new IdentityHashMap<>();

    public ShulkerDisplay() {
        super("ShulkerDisplay");
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostTick(), this::prepareCompressedContents);
        registerListener(RenderListener.getRenderScreen().getChannel(HandledScreen.class), this::onRenderScreen);
    }

    private void prepareCompressedContents() {
        if (checkNull()) return;
        compressedContents.clear();
        if (enable.get() && showMode.get().isIn(ShowMode.ALL)) {
            if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
            for (Slot slot : screen.getScreenHandler().slots) {
                ItemStack shulker = slot.getStack();
                if (isShulker(shulker)) compressedContents.put(slot, compressContents(shulker));
            }
        }
    }

    private void onRenderScreen(Event<RenderScreen> event) {
        if (checkNull()) return;
        if (!enable.get()) return;
        var renderEvent = event.context;
        if (renderEvent.stage() != RenderScreen.Stage.POST
                || renderEvent.screen() != mc.currentScreen
                || !(renderEvent.screen() instanceof HandledScreen<?> handled)) return;

        double mouseX = renderEvent.mouseX();
        double mouseY = renderEvent.mouseY();
        HandledScreenAccess screenAccess = HandledScreenAccess.of(handled);
        VDrawContext context = renderEvent.context();

        if (showMode.get() == ShowMode.ALL) {
            renderAll(context, handled, mouseX, mouseY);
            return;
        }

        Slot hovered = screenAccess.reallyGetSlotAt(mouseX, mouseY);
        if (hovered == null || !isShulker(hovered.getStack())) return;
        if (filterEmpty.get() && compressContents(hovered.getStack()).isEmpty()) return;

        double x;
        double y;
        if (showMode.get() == ShowMode.TOOLTIPS) {
            x = mouseX + followOffset.get().getWindowX(mc.getWindow());
            y = mouseY + followOffset.get().getWindowY(mc.getWindow());
        } else {
            x = renderPos.get().getWindowX(mc.getWindow());
            y = renderPos.get().getWindowY(mc.getWindow());
        }
        renderPreview(context, hovered.getStack(), x, y, false);
    }

    private void renderAll(VDrawContext context, HandledScreen<?> screen, double mouseX, double mouseY) {
        List<Slot> shulkers = new ArrayList<>();
        Slot selected = HandledScreenAccess.of(screen).reallyGetSlotAt(mouseX, mouseY);
        for (Slot slot : screen.getScreenHandler().slots) {
            if (isShulker(slot.getStack())) shulkers.add(slot);
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        List<Pair<Slot, List<ItemStack>>> renderData = new ArrayList<>();
        double height = 0;
        for (Slot slot : shulkers) {
            List<ItemStack> contents =
                    compressedContents.computeIfAbsent(slot, (slot1) -> this.compressContents(slot1.getStack()));
            if (contents.isEmpty() && filterEmpty.get()) continue;
            height += previewHeight(contents.size()) + 1;
            renderData.add(Pair.of(slot, contents));
        }
        height = height + 30;
        double scale = 1.0D;
        int expectingColumn = 1;
        double acceptedMaxWith = 1.2D;
        while (true) {
            scale = (screenHeight * 2 * expectingColumn) / height;
            if (scale * (expectingColumn + 1) < acceptedMaxWith) {
                expectingColumn += 1;
                continue;
            } else {
                break;
            }
        }
        scale = Math.clamp(
                (screenHeight * 2 * expectingColumn) / height,
                acceptedMaxWith / ((expectingColumn + 1)),
                acceptedMaxWith / (expectingColumn));
        scale = Math.clamp(scale, 0, 0.8);
        expectingColumn = (int) Math.ceil((scale * height / (screenHeight)));
        int oneSideColumn = (expectingColumn + 1) / 2;
        double scaledHeight = screenHeight / scale;

        context.getMatrices().pushMatrix();
        double totalHeight = 0;
        int totalColumn = 0;
        int width = (COLUMNS * SLOT_SIZE + 2 * BORDER_SIZE) + 1;
        var iter = renderData.iterator();
        {
            context.getMatrices().translate(1, 1);
            context.getMatrices().scale((float) scale, (float) scale);
            while (iter.hasNext()) {
                Pair<Slot, List<ItemStack>> pair = iter.next();
                renderPreview(
                        context,
                        pair.getFirst().getStack(),
                        pair.getSecond(),
                        true,
                        totalColumn * width,
                        (int) totalHeight,
                        selected == pair.getFirst());
                totalHeight += previewHeight(pair.getSecond().size()) + 1;
                if (totalHeight >= scaledHeight - 30) {
                    totalHeight = 0;
                    totalColumn += 1;
                    if (totalColumn >= oneSideColumn) {
                        totalColumn = 0;
                        break;
                    }
                }
            }
        }
        context.getMatrices().popMatrix();
        context.getMatrices().pushMatrix();
        {
            context.getMatrices().translate(screenWidth - 1, 1);
            context.getMatrices().scale((float) scale, (float) scale);
            context.getMatrices().translate(-width, 0);
            while (iter.hasNext()) {
                Pair<Slot, List<ItemStack>> pair = iter.next();
                renderPreview(
                        context,
                        pair.getFirst().getStack(),
                        pair.getSecond(),
                        true,
                        -totalColumn * width,
                        (int) totalHeight,
                        selected == pair.getFirst());
                totalHeight += previewHeight(pair.getSecond().size()) + 1;
                if (totalHeight >= scaledHeight - 30) {
                    totalHeight = 0;
                    totalColumn += 1;
                    if (totalColumn >= oneSideColumn) {
                        totalColumn -= 1;
                    }
                }
            }
        }
        context.getMatrices().popMatrix();
    }

    private boolean isShulker(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private void renderPreview(VDrawContext context, ItemStack shulker, double x, double y, boolean selected) {
        var container = shulker.get(DataComponentTypes.CONTAINER);
        if (container == null) return;
        renderPreview(context, shulker, container.stacks, false, x, y, selected);
    }

    private void renderPreview(
            VDrawContext context,
            ItemStack shulker,
            List<ItemStack> contents,
            boolean compact,
            double x,
            double y,
            boolean selected) {
        int columns = COLUMNS;
        int rows = compact ? Math.max(1, (contents.size() + columns - 1) / columns) : ROWS;
        int previewWidth = columns * SLOT_SIZE + BORDER_SIZE * 2;
        int previewHeight = rows * SLOT_SIZE + BORDER_SIZE * 2;

        ColorSampler sampler = ColorSampler.of(getTint(shulker));
        context.getMatrices().pushMatrix();
        context.pushLayer(300);
        try {
            context.getMatrices().translate((float) x, (float) y);
            switch (previewMode.get()) {
                case TEXTURE -> renderElement(
                        context, new PlateElement(false, sampler), previewWidth, previewHeight, 0, 0);
                case BACKGROUND -> renderElement(
                        context, new ColorLabelElement(sampler), previewWidth, previewHeight, 0, 0);
                case TRANSPARENT -> {}
            }

            int patCount = columns * rows;
            for (int index = 0; index < patCount; index++) {
                ItemStack item = index < contents.size() ? contents.get(index) : ItemStack.EMPTY;
                int column = compact ? index / rows : index % columns;
                int row = compact ? index % rows : index / columns;
                renderElement(
                        context,
                        new SlotElement(item, sampler),
                        SLOT_SIZE,
                        SLOT_SIZE,
                        BORDER_SIZE + column * SLOT_SIZE,
                        BORDER_SIZE + row * SLOT_SIZE);
            }
            if (selected) drawHighlight(context, previewWidth, previewHeight);
        } finally {
            context.popLayer();
            context.getMatrices().popMatrix();
        }
    }

    private List<ItemStack> compressContents(ItemStack shulker) {
        var container = shulker.get(DataComponentTypes.CONTAINER);
        if (container == null) return List.of();
        Map<ItemStackSample, Integer> itemCounts = new LinkedHashMap<>();
        for (ItemStack item : container.stacks) {
            if (!item.isEmpty()) itemCounts.merge(ItemStackSample.of(item), item.getCount(), Integer::sum);
        }
        List<ItemStack> compressed = new ArrayList<>(itemCounts.size());
        itemCounts.forEach((sample, count) -> compressed.add(sample.sample().copyWithCount(count)));
        compressed.sort(Comparator.comparingInt(s -> -s.getCount()));
        return compressed;
    }

    private int previewHeight(int itemCount) {
        int rows = Math.max(1, (itemCount + COLUMNS - 1) / COLUMNS);
        return rows * SLOT_SIZE + BORDER_SIZE * 2;
    }

    private void drawHighlight(VDrawContext context, int previewWidth, int previewHeight) {
        int color = 0xFFFFFFFF;
        context.fill(0, 0, previewWidth, 1, color);
        context.fill(0, previewHeight - 1, previewWidth, previewHeight, color);
        context.fill(0, 0, 1, previewHeight, color);
        context.fill(previewWidth - 1, 0, previewWidth, previewHeight, color);
    }

    private int getTint(ItemStack shulker) {
        OptionalPrimitive<TextColor> override = color.get();
        if (override.isPresent())
            return ColorUtils.withAlphaInt(
                    override.getValue().getRgb(), alpha.get()); //  override.getValue().withAlpha(alpha.get());
        if (shulker.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBlock) {
            DyeColor dyeColor = shulkerBlock.getColor();
            if (dyeColor != null) return ColorUtils.withAlphaInt(dyeColor.getEntityColor(), alpha.get());
        }
        return ColorUtils.withAlphaInt(-1, alpha.get());
    }

    private void renderElement(VDrawContext context, AbstractElement element, int width, int height, int x, int y) {
        DrawableWidget widget = new ExecutableWidget(x, y, width, height).setElementHandler(element);
        widget.render0(context, 0, 0, 0, true);
    }

    public enum PreviewMode implements ConfigEnum {
        TRANSPARENT,
        TEXTURE,
        BACKGROUND;

        @Override
        public String getConfigEnumType() {
            return "shulker_display_preview_mode";
        }
    }

    public enum ShowMode implements ConfigEnum {
        TOOLTIPS,
        HIGHLIGHTED,
        ALL;

        @Override
        public String getConfigEnumType() {
            return "shulker_display_show_mode";
        }
    }
}
