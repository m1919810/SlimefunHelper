package me.matl114.gui.complex.invcache;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.IntStream;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.GridSubScreen;
import me.matl114.gui.basic.*;
import me.matl114.hacks.InvTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class InventoryViewScreen extends GenericBackGroundScreen {
    protected BlockPos blockPos;
    protected ClientWorld blockWorld;
    //    protected HandledScreen<?> handledScreen;
    protected final GridSubScreen<DrawableWidget> grid;
    protected ContentDelegateWidget<GridSubScreen<DrawableWidget>> gridDelegate;
    protected final int DATA_OCCUPIED = 40;
    protected SlotElement iconStack;

    public InventoryViewScreen(HandledScreen<?> handledScreen) {
        this(
                handledScreen.getScreenHandler().slots.stream()
                        .filter(i -> !(i.inventory instanceof PlayerInventory))
                        .toList(),
                handledScreen.getTitle(),
                InvTasks.generateIconForScreen(handledScreen));
        if (handledScreen instanceof TileInventoryScreen tile && !tile.isVirtual()) {
            blockPos = tile.getPos();
            this.blockWorld = tile.getWorld();
        }
    }

    private static List<Slot> streamInventoryToSlot(Inventory inventory) {
        return IntStream.range(0, inventory.size())
                .mapToObj((i) -> new Slot(inventory, i, 0, 0))
                .toList();
    }

    public InventoryViewScreen(Inventory inventory, Text title, ItemStack icon) {
        this(streamInventoryToSlot(inventory), title, icon);
    }

    public InventoryViewScreen(List<Slot> list, Text title, ItemStack icon) {
        super(title, 240, 320);
        this.grid = new GridSubScreen<>(40, TITLE_OCCUPIED + DATA_OCCUPIED, 160, 120, 16, 16);

        this.grid.refreshPage(list, this::makeIcon, 1);
        this.iconStack = SlotElement.instance(icon);
    }

    protected static final List<Text> CLICK_COPY_TOOLTIPS = List.of(Text.literal("点击拷贝坐标"));
    protected static List<Text> CLICK_ITEM_TOOLTIPS =
            List.of(Text.literal("左键物品栏中的物品执行选中后操作(可能不存在选中后操作)"), Text.literal("右键物品栏中的物品以打开物品编辑界面"));

    @Override
    protected void init() {
        super.init();
        ExecutableWidget.instance(this.x + 40, this.y + TITLE_OCCUPIED + 2, 16, 16)
                .setElementHandler(this.iconStack)
                .addTo(this);
        ElementHandler labelElement;
        if (blockPos != null) {
            labelElement = LabelElement.instance(Text.literal("%s [%d, %d, %d] (鼠标悬浮以查看说明)"
                            .formatted(
                                    this.blockWorld.getRegistryKey().getValue().toString(),
                                    blockPos.getX(),
                                    blockPos.getY(),
                                    blockPos.getZ())))
                    .withInputHandler(InputHandler.run(() -> {
                        MinecraftClient.getInstance()
                                .keyboard
                                .setClipboard("%d %d %d".formatted(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                    }))
                    .withTooltips(
                            TooltipHandler.of(Streams.concat(CLICK_ITEM_TOOLTIPS.stream(), CLICK_COPY_TOOLTIPS.stream())
                                    .toList()));
        } else {
            labelElement = LabelElement.instance(Text.literal("虚拟容器(鼠标悬浮以查看说明)"))
                    .withTooltips(TooltipHandler.of(CLICK_ITEM_TOOLTIPS));
        }

        ExecutableWidget.instance(this.x + 60, this.y + TITLE_OCCUPIED, 140, 20)
                .setElementHandler(labelElement)
                .addTo(this);
        this.gridDelegate = new ContentDelegateWidget<>(this.x, this.y, 0, 0)
                .setContentDelegate(this.grid)
                .addTo(this);
    }

    protected static final ItemStack ICON_UNKNOWN = new ItemStack(Items.BARRIER);

    protected DrawableWidget makeIcon(Slot screen) {
        return ExecutableWidget.instance(0, 0, 16, 16)
                .setElementHandler(new SlotElement(screen.inventory, screen.getIndex(), (stack, i) -> {
                    if (i == 1) {
                        rightClickItem(stack);
                        return true;
                    } else if (i == 0) {
                        leftClickItem(stack);
                        return true;
                    }
                    return false;
                }));
    }

    protected void leftClickItem(ItemStack stack) {}

    protected void rightClickItem(ItemStack stack) {
        InvTasks.openEditScreen(stack, null);
    }
}
