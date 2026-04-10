package me.matl114.gui.presets.lists;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.config.ListModifyWidget;
import me.matl114.gui.presets.choices.ConfirmingBigScreen;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class NBTListModifyScreen<T> extends ConfirmingBigScreen {
    AttrKeyValue<List<T>> validator;
    List<AttrKeyValue<T>> list;
    Consumer<List<T>> callback;
    NBTType<T> type;
    int widgetDx;
    int widgetDy;
    ListEntryWidgetController fuckController;

    public NBTListModifyScreen(
            AttrKeyValue<List<T>> attrKeyValue,
            NBTType<T> type,
            Supplier<T> newElement,
            Consumer<List<T>> callback,
            int dx,
            int dy) {
        super(Text.literal("列表编辑界面").formatted(Formatting.GREEN));
        validator = attrKeyValue;
        this.list = attrKeyValue.getOriginValue().stream()
                .map(s -> type.createAttrKeyValue("", s))
                .collect(Collectors.toCollection(ArrayList::new));
        this.callback = callback;
        this.type = type;
        this.widgetDx = dx;
        this.widgetDy = dy;
        this.fuckController = ListEntryWidgetController.mutable(
                list,
                () -> this.type.createAttrKeyValue("", newElement.get()),
                (w) -> this.type.generateValueWidget(w, 0, 0, widgetDx, widgetDy),
                widgetDy,
                widgetDx);
    }

    @Override
    protected void init() {
        super.init();
        int listWidth = this.widgetDx + 80;

        new ListModifyWidget(
                        this.fuckController,
                        this.x + (this.backgroundWidth - listWidth) / 2,
                        CONTENT_START_Y,
                        listWidth,
                        content_end_y - CONTENT_START_Y)
                .addTo(this);
    }

    private List<T> list() {
        return list.stream().map(AttrKeyValue::getOriginValue).collect(Collectors.toList());
    }

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        var lst = new ArrayList<T>();
        for (var re : list) {
            if (re.isValidate()) {
                lst.add(re.getOriginValue());
            } else return false;
        }
        return validator.isValueValid(lst);
    }

    @Override
    protected void onConfirmButton() {
        var list = this.list();
        if (validator.isValueValid(list)) {
            callback.accept(list);
            close();
        }
    }
}
