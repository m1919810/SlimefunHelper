package me.matl114.gui.slimefun;

import com.google.common.base.Preconditions;
import me.matl114.gui.basic.*;
import me.matl114.hackUtils.InvTasks;
import me.matl114.hackUtils.ItemEditTasks;
import me.matl114.hackUtils.SlimefunTasks;
import me.matl114.utils.UtilClass.MyIngredientImmutableInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.BiConsumer;

public class SlimefunRecipeWidget extends SubScreenWidget {
    ItemStack rtypeIcon;
    String rid ;
    ItemStack output;
    Inventory input;
    //change it later
    // 14， 11- 29 16 - 119 +4， 30 +4
    protected static final int DX = 144;
    protected static final int DY = 64;
    BiConsumer<ItemStack,Boolean> callback1;
    BiConsumer<String,Boolean> callback2;
    Ingredient[] ingredients;
    public SlimefunRecipeWidget(int x, int y, SlimefunTasks.RecipeEntry entry, BiConsumer<ItemStack,Boolean> itemClickEvent, BiConsumer<String,Boolean> rtypeClickEvent) {
        super(x, y, DX, DY);
        this.rid = entry.rid();
        this.rtypeIcon = SlimefunTasks.getRecipeTypeIcon(rid);
        this.output = entry.output();
        this.ingredients = entry.ingredient();
        Preconditions.checkArgument(this.ingredients.length ==9);
        this.input = new MyIngredientImmutableInventory(entry.ingredient());
        this.callback1 = itemClickEvent;
        this.callback2 = rtypeClickEvent;
        init0();
    }
    private static final List<Text> CREATIVEGIVE_TOOLTIPS = List.of(
        Text.literal("获得物品"),
        Text.literal(""),
        Text.literal("仅在创造模式可用")
    );
    private static final List<Text> OPENEDITOR_TOOLTIPS = List.of(
        Text.literal("在物品编辑器中打开该物品")
    );
    private static final List<Text> SAVEITEM_TOOLTIPS = List.of(
        Text.literal("将该物品加入保存物品")
    );
    private final void init0(){
        DisplayWidget.instance(0,0,DX, DY)
            .setRenderHandler(PlateElement.instance())
            .addToSub(this);
        for (int i=0; i<3; ++i){
            for (int j=0; j<3; ++j){
                final int index = 3*i + j;
                ExecutableWidget.instance(15 + 18*j, 5 + 18*i, 18,18)
                    .setElementHandler(new SlotElement(input, index).withMouseHandler(MouseHandler.isLeft((t)->callback1.accept(input.getStack(index),t))))
                    .addToSub(this);
            }
        }
        //
        ExecutableWidget.instance(123 - 14, 23, 18, 18)
            .setElementHandler(new OutputSlotElement(output).withMouseHandler(MouseHandler.isLeft((t)->callback1.accept(output, t))))
            .addToSub(this)
        ;
        ExecutableWidget.instance(78, 23, 18, 18)
            .setElementHandler(
                SlotElement.instance(rtypeIcon)
                    .setSlotFrame(false)
                    .withMouseHandler(MouseHandler.isLeft(t->callback2.accept(rid, t)))
            )
            .addToSub(this);
        //save item
        //ExecutableWidget.instance()
        // creative give
        boolean displayGive = MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isCreative();
        int buttonAmount =  2 + (displayGive?1:0);
        //中心在 123 - 14 + 9 =118
        // buttonAmount个, 相当于
        //button间隔3
        // 12 buttonAmount - 3
        //横向长度 12buttonAMount - 3
        //起点 -6 buttonAmount +1
        int startX = 119 - 6* buttonAmount ;
        int index = 0;
        if(displayGive){
            ExecutableWidget.instance(startX + index *12, 44, 9, 9)
                .setElementHandler(
                        new  ButtonElement(TextProvider.of(Text.literal("G")), ButtonAction.run(()->{
                            InvTasks.creativeAddItem(output.copy(), 64);
                        })
                    )
                        .withTooltips(TooltipHandler.of(CREATIVEGIVE_TOOLTIPS))
                )
                .addToSub(this);
            index += 1;
        }
        ExecutableWidget.instance(startX + index * 12 , 44, 9, 9)
            .setElementHandler(
                new  ButtonElement(TextProvider.of(Text.literal("E")),
                    ButtonAction.run(()->{
                        ItemEditTasks.openEditScreen(output.copy(), null);
                    })
                )
                    .withTooltips(TooltipHandler.of(OPENEDITOR_TOOLTIPS))
            )
            .addToSub(this)
            ;
        index ++;
        ExecutableWidget.instance(startX + index * 12 , 44, 9,9)
            .setElementHandler(
                new  ButtonElement(TextProvider.of(Text.literal("+")),
                    ButtonAction.run(()->{
                        SlimefunTasks.handleSaveItem(output.copy());
                    })
                )
                    .withTooltips(TooltipHandler.of(SAVEITEM_TOOLTIPS))
            )
            .addToSub(this)
            ;

    }
}
