package me.matl114.gui;

import me.matl114.gui.basic.*;
import me.matl114.gui.config.*;
import me.matl114.gui.slimefun.SlimefunDispensorSuggestBookWidget;
import me.matl114.gui.slimefun.SlimefunScreen;
import me.matl114.hackUtils.SlimefunTasks;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.AttrKeyValue;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.ArrayList;

public class TestingScreen2 extends SlimefunScreen {
    public TestingScreen2(Text text){
        super(text);
    }
    DrawableWidget element1;
    DrawableWidget element2;
    DrawableWidget element3;
    DrawableWidget element4;
    DrawableWidget element5;
    DrawableWidget element6;
    DrawableWidget element7;
    DrawableWidget element8;
    DrawableWidget element9;
    DrawableWidget element10;
    DrawableWidget element11;
    DrawableWidget element12;
    @Override
    protected void init() {
        init0();
//        element1 = new ScrollableWidget(this.x+ 30, this.y+ 30, this.backgroundWidth - 60, this.backgroundHeight - 60)
//            .addScrollingWidget(
//                DisplayWidget.instance(10, 0, this.backgroundWidth - 80, 400)
//                    .setRenderHandler(SlotElement.instance(new ItemStack(Items.FURNACE)))
//            )
//            .addTo(this);
//        element1 = new DraggableExecutableWidget(this.x + 30, this.y + 100, 40, this.backgroundHeight - 130)
//            .setElementHandler(ScrollElement.instance(PropertyTracker.empty()))
//            .addTo(this);
//        element2 = new DraggableExecutableWidget(this.x+30, this.y+30, this.backgroundWidth - 60, 60)
//            .setElementHandler(
//                ScrollElement.instance(PropertyTracker.empty())
//                    .setDraggingY(false)
//            )
//            .addTo(this);
//        element3 = McWidgetDelegates.createMultiLineEditBox(this.x+80, this.y+110, 80,90, PropertyTracker.event((val, str)->{
//                int len = str.length();
//            }), "byd")
//            .addTo(this);

//        element4 = new SlimefunDispensorSuggestBookWidget(this.x , this.y, null,  (shift, recipe)->{
//            Debug.info("click callback");
//        }).addTo(this);
        int width = 200;
        int height = 20;
        element5 = new ListModifyWidget(
            ListEntryWidgetController.mutable(new ArrayList<>(),()-> AttrKeyValue.registry("-", Registries.ITEM, Items.AIR), (str)->new KeyValueInputWidget<>(0,0, width - 4* height, height, 20, str),height, width- 4* height),
            this.x , this.y, width , 10 * height
        )
            .addTo(this);
//        element6 = new ListRegistrySelectScreen<>(Registries.ITEM, (item)->item.getName().getString(), (triplet)->{
//            return new RegistryDisplayRender(new ItemStack(triplet.getC()), triplet.getC().getName(), triplet.getB());
//        },this.x, this.y, width, height * 10, height)
//            .addTo(this);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
