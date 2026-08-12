package me.matl114.utils.config.kv;

import java.util.ArrayList;
import java.util.List;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;

public class IntListAttrKeyValue extends ListAttrKeyValue<Integer> {
    public IntListAttrKeyValue(String key, List<Integer> value, WrapperFactory<String, List<Integer>> wrapperFactory) {
        super(key, value, LIST_WIDGET_FACTORY, wrapperFactory);
    }

    @Override
    public List<AttrKeyValue<Integer>> createAttrKeyValueForElements() {
        var list = getOriginValue();
        var size = list.size();
        List<AttrKeyValue<Integer>> res = new ArrayList<>();
        for (int i = 0; i < size; ++i) {
            BaseAttrKeyValue<Integer> str = AttrKeyValue.integer(this.getKeyName(), list.get(i));
            str.getValidators().addAll(elementValidators);
            res.add(str);
        }
        return res;
    }

    @Override
    public AttrKeyValue<Integer> createNewAttrKeyValueElement() {
        BaseAttrKeyValue<Integer> str = AttrKeyValue.integer(this.getKeyName(), 0);
        str.getValidators().addAll(elementValidators);
        return str;
    }

    public static final CustomWidgetFactory<List<Integer>> LIST_WIDGET_FACTORY = (s, x, y, inputDx, dy) -> {
        return new SubScreenWidget(x, y, inputDx, dy)
                .addDrawableChild(McWidgetHelpers.createAttrValueEditBox(s, 0, 0, inputDx - dy, dy))
                .addDrawableChild(WidgetUtils.createOpenListModifyScreenButton(
                        (IntListAttrKeyValue) s, inputDx - dy + 1, 0, dy - 1, dy));
    };
}
