package me.matl114.gui.presets.lists;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.complex.config.ListModifyWidget;
import me.matl114.gui.presets.choices.ConfirmingBigScreen;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WidgetFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class NBTListModifyScreen<T> extends ConfirmingBigScreen {
    final Predicate<List<T>> validator;
    List<AttrKeyValue<T>> list;
    Consumer<List<T>> callback;
    final BiFunction<String, T, AttrKeyValue<T>> attrFactory;
    int widgetDx;
    int widgetDy;
    ListEntryWidgetController fuckController;
    // modifiable
    public NBTListModifyScreen(
            AttrKeyValue<List<T>> attrKeyValue,
            NBTType<T> type,
            Supplier<T> newElement,
            Consumer<List<T>> callback,
            int dx,
            int dy) {
        this(
                attrKeyValue.getOriginValue(),
                attrKeyValue::isValueValid,
                type::createAttrKeyValue,
                type::generateValueWidget,
                newElement,
                callback,
                dx,
                dy);
    }

    public NBTListModifyScreen(
            List<T> list,
            Predicate<List<T>> listValidator,
            BiFunction<String, T, AttrKeyValue<T>> attrElementFactory,
            WidgetFactory<AttrKeyValue<T>> customWidgetFactory,
            Supplier<T> newElement,
            Consumer<List<T>> callback,
            int dx,
            int dy) {
        super(Text.translatable("widget.gui.nbt-list-modify-screen.title").formatted(Formatting.GREEN));
        validator = listValidator;
        this.attrFactory = attrElementFactory;
        this.list = list.stream().map(s -> attrFactory.apply("", s)).collect(Collectors.toCollection(ArrayList::new));
        this.callback = callback;

        this.widgetDx = dx;
        this.widgetDy = dy;
        this.fuckController = ListEntryWidgetController.mutable(
                this.list,
                () -> attrFactory.apply("", newElement.get()),
                (w) -> customWidgetFactory.generateWidget(w, 0, 0, widgetDx, widgetDy),
                widgetDy,
                widgetDx);
    }

    //    //immutable
    //    public NBTListModifyScreen(
    //        List<T> list,
    //        Predicate<List<T>> listValidator,
    //        BiFunction<String, T, AttrKeyValue<T>> attrElementFactory,
    //        WidgetFactory<AttrKeyValue<T>> customWidgetFactory,
    //        Consumer<List<T>> callback,
    //        int dx,
    //        int dy){
    //        super(Text.literal("列表编辑界面").formatted(Formatting.GREEN));
    //        validator = listValidator;
    //        this.attrFactory = attrElementFactory;
    //        this.list = list.stream()
    //            .map(s -> attrFactory.apply("", s))
    //            .collect(Collectors.toCollection(ArrayList::new));
    //        this.callback = callback;
    //        this.fuckController = ListEntryWidgetController.immutable(
    //            this.list,
    //            (w) -> customWidgetFactory.generateWidget(w, 0, 0, widgetDx, widgetDy),
    //            widgetDy,
    //            widgetDx);
    //    }

    @Override
    protected void init() {
        super.init();
        int listWidth = this.widgetDx + 80;

        new ListModifyWidget(
                        this.fuckController,
                        this.x + (this.backgroundWidth - listWidth) / 2,
                        this.y + CONTENT_START_Y,
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
        return validator.test(lst);
    }

    @Override
    protected void onConfirmButton() {
        var list = this.list();
        if (validator.test(list)) {
            callback.accept(list);
            close();
        }
    }
}
