package me.matl114.gui.presets.lists;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.presets.choices.ConfirmingBigScreen;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WidgetFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class NBTBoundedListScreen<W, T> extends ConfirmingBigScreen {
    final Predicate<Map<W, T>> validator;
    List<Pair<W, AttrKeyValue<T>>> list;
    Consumer<Map<W, T>> callback;
    final BiFunction<String, T, AttrKeyValue<T>> attrFactory;
    int widgetDkey;
    int widgetDx;
    int widgetDy;
    ListEntryWidgetController fuckController;
    // modifiable
    public NBTBoundedListScreen(
            AttrKeyValue<Map<W, T>> attrKeyValue,
            NBTType<T> type,
            WidgetFactory<W> keyWidgetFactory,
            Consumer<Map<W, T>> callback,
            int dkey,
            int dx,
            int dy) {
        this(
                attrKeyValue.getOriginValue(),
                attrKeyValue::isValueValid,
                type::createAttrKeyValue,
                keyWidgetFactory,
                type::generateValueWidget,
                callback,
                dkey,
                dx,
                dy);
    }

    // immutable
    public NBTBoundedListScreen(
            Map<W, T> list,
            Predicate<Map<W, T>> listValidator,
            BiFunction<String, T, AttrKeyValue<T>> attrElementFactory,
            WidgetFactory<W> keyWidgetFactory,
            WidgetFactory<AttrKeyValue<T>> valueWidgetFactory,
            Consumer<Map<W, T>> callback,
            int dkey,
            int dx,
            int dy) {
        super(Text.literal("列表编辑界面").formatted(Formatting.GREEN));
        validator = listValidator;
        this.attrFactory = attrElementFactory;
        this.list = list.entrySet().stream()
                .map(s -> Pair.of(s.getKey(), attrFactory.apply("", s.getValue())))
                .collect(Collectors.toCollection(ArrayList::new));
        this.callback = callback;
        this.widgetDkey = dkey;
        this.widgetDx = dx;
        this.widgetDy = dy;
        this.fuckController = ListEntryWidgetController.immutable(
                this.list,
                (w) -> {
                    return new SubScreenWidget(0, 0, widgetDx, widgetDy)
                            .addDrawableChild(keyWidgetFactory.generateWidget(w.getFirst(), 0, 0, widgetDkey, widgetDy))
                            .addDrawableChild(valueWidgetFactory.generateWidget(
                                    w.getSecond(), widgetDkey, 0, widgetDx - widgetDkey, widgetDy));
                },
                widgetDy,
                widgetDx);
    }

    @Override
    protected void init() {
        super.init();
        int listWidth = this.widgetDx;

        new ListUnmodifiableWidget(
                        this.fuckController,
                        this.x + (this.backgroundWidth - listWidth) / 2,
                        CONTENT_START_Y,
                        listWidth,
                        content_end_y - CONTENT_START_Y)
                .addTo(this);
    }

    private Map<W, T> listMap() {
        LinkedHashMap<W, T> map = new LinkedHashMap<>();
        for (var re : this.list) {
            map.put(re.getFirst(), re.getSecond().getOriginValue());
        }
        return map;
        // return list.stream().map(Pair::getSecond).map(AttrKeyValue::getOriginValue).collect(Collectors.toList());
    }

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        Map<W, T> lst = new LinkedHashMap<>();
        for (var re : list) {
            if (re.getSecond().isValidate()) {
                lst.put(re.getFirst(), re.getSecond().getOriginValue());
            } else return false;
        }
        return validator.test(lst);
    }

    @Override
    protected void onConfirmButton() {
        var list = this.listMap();
        if (validator.test(list)) {
            callback.accept(list);
            close();
        }
    }
}
