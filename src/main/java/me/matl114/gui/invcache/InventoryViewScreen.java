package me.matl114.gui.invcache;

import me.matl114.access.TileInventoryScreen;
import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.GridSubScreen;
import me.matl114.gui.basic.*;
import me.matl114.hackUtils.InvTasks;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class InventoryViewScreen extends GenericBackGroundScreen {
    protected BlockPos blockPos;
    protected ClientWorld blockWorld;
    protected HandledScreen<?> handledScreen;
    protected final GridSubScreen<DrawableWidget> grid;
    protected ContentDelegateWidget<GridSubScreen<DrawableWidget>> gridDelegate;
    protected final int DATA_OCCUPIED = 40;
    protected SlotElement iconStack;
    protected InventoryViewScreen(HandledScreen<?> handledScreen) {
        super(handledScreen.getTitle(), 240, 320);
        this.handledScreen = handledScreen;
        this.grid = new GridSubScreen<>(
            40, TITLE_OCCUPIED + DATA_OCCUPIED, 160, 120, 16, 16
        );

        this.grid.refreshPage(handledScreen.getScreenHandler().slots
                .stream()
                .filter(i->!(i.inventory instanceof PlayerInventory))
                .toList()
            , this::makeIcon, 1);
        this.iconStack = SlotElement.instance( InvTasks.generateInvIcon(handledScreen));
        if(handledScreen instanceof TileInventoryScreen tile && !tile.isVirtual()){
            blockPos = tile.getPos();
            this.blockWorld = tile.getWorld();
        }
    }
    protected static final List<Text> CLICK_COPY_TOOLTIPS = List.of(
        Text.literal("点击拷贝坐标")
    );

    @Override
    protected void init() {
        super.init();
         ExecutableWidget.instance(this.x + 40, this.y + TITLE_OCCUPIED + 2, 16 , 16)
            .setElementHandler(this.iconStack)
            .addTo(this);
         ElementHandler labelElement;
         if(blockPos !=null){
             labelElement = LabelElement.instance(
                 Text.literal("%s [%d, %d, %d]".formatted(this.blockWorld.getRegistryKey().getValue().toString(), blockPos.getX(), blockPos.getY(), blockPos.getZ()))
             )
                 .withMouseHandler(MouseHandler.run(()->{
                     MinecraftClient.getInstance().keyboard.setClipboard("%d %d %d".formatted(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                 }))
                 .withTooltips(TooltipHandler.of(CLICK_COPY_TOOLTIPS));
         }else {
             labelElement = LabelElement.instance(
                 Text.literal("虚拟容器")
             );
         }

         ExecutableWidget.instance(this.x + 80, this.y + TITLE_OCCUPIED , 120, 20)
            .setElementHandler(labelElement)
             .addTo(this);
        this.gridDelegate = new ContentDelegateWidget<>(this.x, this.y, 0,0)
            .setContentDelegate(this.grid)
            .addTo(this);
    }

    protected static final ItemStack ICON_UNKNOWN = new ItemStack(Items.BARRIER);
    protected DrawableWidget makeIcon(Slot screen){
        return ExecutableWidget.instance(0,0,16, 16)
            .setElementHandler(
                new SlotElement(screen.inventory, screen.getIndex())
            );
    }
}
