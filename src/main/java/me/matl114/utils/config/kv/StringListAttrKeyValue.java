package me.matl114.utils.config.kv;

import java.util.ArrayList;
import java.util.List;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;

public class StringListAttrKeyValue extends ListAttrKeyValue<String> {

    public List<AttrKeyValue<String>> createAttrKeyValueForElements() {
        var list = getOriginValue();
        var size = list.size();
        List<AttrKeyValue<String>> res = new ArrayList<>();
        for (int i = 0; i < size; ++i) {
            BaseAttrKeyValue<String> str = AttrKeyValue.str(this.getKeyName(), list.get(i));
            str.getValidators().addAll(elementValidators);
            res.add(str);
        }
        return res;
    }

    public AttrKeyValue<String> createNewAttrKeyValueElement() {
        BaseAttrKeyValue<String> str = AttrKeyValue.str(this.getKeyName(), "");
        str.getValidators().addAll(elementValidators);
        return str;
    }

    public StringListAttrKeyValue(String key, List<String> value) {
        super(key, value, LIST_WIDGET_FACTORY, AttrKeyValues.STR_LIST_FACTORY);
    }

    public static final CustomWidgetFactory<List<String>> LIST_WIDGET_FACTORY = (s, x, y, inputDx, dy) -> {
        return new SubScreenWidget(x, y, inputDx, dy)
                .addDrawableChild(McWidgetHelpers.createAttrValueEditBox(s, 0, 0, inputDx - dy, dy))
                .addDrawableChild(WidgetUtils.createOpenListModifyScreenButton(
                        (StringListAttrKeyValue) s, inputDx - dy + 1, 0, dy - 1, dy));
    };
}
