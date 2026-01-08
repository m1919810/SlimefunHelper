package me.matl114.gui.config;

import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.utils.UtilClass.AttrKeyValue;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListModifyScreen extends ConfirmingBigScreen{
    AttrKeyValue.ListAttrKeyValue listAttrKeyValue;
    List<AttrKeyValue<String>> list;
    Consumer<AttrKeyValue.ListAttrKeyValue> consumer;
    ListEntryWidgetController controller;
    private static final int WIDTH = 240;

    protected ListModifyScreen(AttrKeyValue.ListAttrKeyValue list, Consumer<AttrKeyValue.ListAttrKeyValue> consumer) {
        super(Text.empty());
        setTitleLabel(Text.literal("列表编辑界面").formatted(Formatting.GREEN));
        this.listAttrKeyValue = list;
        this.list =new ArrayList<>(this.listAttrKeyValue.createAttrKeyValueForElements());
        this.consumer = consumer;
        this.controller = ListEntryWidgetController.mutable(
            this.list,
            this.listAttrKeyValue::createNewAttrKeyValueElement,
            stringAttrKeyValue -> McWidgetHelpers.createTextFieldEditBox(
                0, 1, WIDTH , 18, stringAttrKeyValue, stringAttrKeyValue.getValue(), McWidgetHelpers.getWrongRedTextBoxColorProvider(stringAttrKeyValue::isValidate)
            ),
            20,
            WIDTH
            );

    }


    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return this.listAttrKeyValue.isValidate();
    }

    @Override
    protected void onConfirmButton() {
        this.close();
        this.listAttrKeyValue.valueChangeInternal(this, this.list.stream().map(AttrKeyValue::getOriginValue).toList());
        if(this.listAttrKeyValue.isValidate()) {
            consumer.accept(this.listAttrKeyValue);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.listAttrKeyValue.valueChangeInternal(this, this.list.stream().map(AttrKeyValue::getOriginValue).toList());
    }

    @Override
    protected void init() {
        super.init();
        int listWidth = WIDTH + 80;

        new ListModifyWidget(this.controller, this.x +  (this.backgroundWidth - listWidth)/2, CONTENT_START_Y, listWidth, content_end_y)
            .addTo(this);
    }
}
