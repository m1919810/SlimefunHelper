package me.matl114.gui.invcache;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.FilterService;
import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.grids.GridSelectSubScreen;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.world.ContainerPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class InventorySelectScreen extends GenericBackGroundScreen {
    private static final Text TITLE = Text.literal("缓存物品界面预览");
    private static final List<Text> TITLE_RULE_TOOLTIPS = java.util.List.of();

    private final GridSelectSubScreen<HandledScreen<?>> grid;
    ContentDelegateWidget<GridSelectSubScreen<HandledScreen<?>>> gridDelegate;
    private static final int PAGE_LABEL_HEIGHT = 12;

    public InventorySelectScreen(Supplier<List<HandledScreen<?>>> handledScreens) {
        super(TITLE, 240, 320);
        this.grid = new GridSelectSubScreen<>(
                0,
                TITLE_OCCUPIED,
                this.backgroundWidth,
                PAGE_LABEL_HEIGHT,
                0,
                this.backgroundHeight - LABEL_OCCUPIED,
                -4,
                16,
                16,
                16,
                handledScreens,
                this::filterInventory,
                this::makeIcon);
    }

    private static final List<Text> RULE_ACCEPT_VIRTUAL =
            List.of(Text.literal("点击切换容器过滤规则"), Text.empty(), Text.literal("当前过滤规则: 接受虚拟容器(即不存在实体方块的容器)"));
    private static final List<Text> RULE_REJECT_VIRTUAL =
            List.of(Text.literal("点击切换容器过滤规则"), Text.empty(), Text.literal("当前过滤规则: 拒绝虚拟容器(即不存在实体方块的容器)"));
    private boolean filterVirtual = true;

    protected List<Text> provideTitleTooltips(DrawableWidget widget) {
        return filterVirtual ? RULE_REJECT_VIRTUAL : RULE_ACCEPT_VIRTUAL;
    }

    protected void runClickTitle(boolean isLeft) {
        filterVirtual = !filterVirtual;
        this.grid.refresh();
    }

    protected DrawableWidget makeIcon(HandledScreen<?> screen) {
        ItemStack icon = null;
        List<Text> description = new ArrayList<>();
        description.add(Text.literal("容器标题: ").append(screen.getTitle()));
        description.add(Text.literal("左键点击预览容器内容"));
        description.add(Text.literal("右键点击渲染容器位置(如果有)"));
        description.add(Text.empty());
        if (screen instanceof TileInventoryScreen tile && !tile.isVirtual()) {
            icon = InvTasks.generateIconForScreen(screen);

            BlockPos pos = tile.getPos();
            description.add(Text.literal("记录位置: ")
                    .append(Text.literal("[%d, %d, %d]".formatted(pos.getX(), pos.getY(), pos.getZ()))
                            .formatted(Formatting.GREEN)));
            description.add(Text.literal("记录世界: ")
                    .append(Text.literal(
                            tile.getWorld().getRegistryKey().getValue().toString())));
        } else {
            description.add(Text.literal("虚拟容器").formatted(Formatting.YELLOW));
        }
        return ExecutableWidget.instance(0, 0, 16, 16)
                .setElementHandler(SlotElement.instance(icon == null ? InvTasks.INV_ICON_UNKNOWN : icon)
                        .setSlotFrame(false)
                        .withInputHandler(InputHandler.isLeft((l) -> {
                            if (l) {
                                openInventoryViewScreen(screen);
                            } else if (screen instanceof TileInventoryScreen tile
                                    && !tile.isVirtual()
                                    && WorldUtils.areWorldEquals(
                                            MinecraftClient.getInstance().world, tile.getWorld())) {
                                ContainerPosition pos = tile.getContainerPosition();
                                RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                                        120,
                                        new RenderTasks.BoxObject(pos.getBoundingBox(), Color.GREEN),
                                        new RenderTasks.LineToTargetObject(pos.getCenterPosition(), Color.RED)));
                                this.close();
                            }
                        }))
                        .withTooltips(TooltipHandler.of(description)));
    }

    protected void openInventoryViewScreen(HandledScreen<?> screen) {
        ScreenAccess.of(new InventoryViewScreen(screen)).openFromCurrent();
    }

    protected boolean filterInventory(String value, HandledScreen<?> screen) {
        return (!filterVirtual || (screen instanceof TileInventoryScreen til && !til.isVirtual()))
                && (FilterService.nameMatch(screen.getTitle().getString().replace("§.", ""), value)
                        || screen.getScreenHandler().slots.stream()
                                .filter(slot -> !(slot.inventory instanceof PlayerInventory))
                                .map(Slot::getStack)
                                .filter(i -> !i.isEmpty())
                                .map(i -> i.getName().getString().replace("§.", ""))
                                .anyMatch(i -> FilterService.nameMatch(i, value)));
    }

    protected void init() {
        super.init();
        int availableRenderSpace = this.backgroundHeight - LABEL_OCCUPIED;
        this.grid.resetGridHeightAndRefresh(availableRenderSpace);
        this.gridDelegate = new ContentDelegateWidget<>(this.x, this.y, 0, 0)
                .setContentDelegate(this.grid)
                .addTo(this);
    }
}
