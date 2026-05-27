package me.matl114.utils.config.kv;

import java.util.ArrayList;
import java.util.List;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.lists.StringListModifyScreen;
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
                .addDrawableChild(McWidgetHelpers.createTextFieldEditBox(
                        0,
                        0,
                        inputDx - dy,
                        dy,
                        s,
                        s.getValue(),
                        McWidgetHelpers.getWrongRedTextBoxColorProvider(s::isValidate)))
                .addDrawableChild(ExecutableWidget.instance(inputDx - dy + 1, 0, dy - 1, dy)
                        .setElementHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(() -> {
                                    ScreenAccess.of(new StringListModifyScreen(
                                                    (ListAttrKeyValue) s, listAttrKeyValue -> {
                                                        s.setOriginValue((List<String>)
                                                                ((ListAttrKeyValue) listAttrKeyValue).getOriginValue());
                                                    }))
                                            .openFromCurrent();
                                }))
                                .withTooltips(TooltipHandler.of(Constants.OPEN_LIST_EDIT_TOOLTIPS))));
    };
}
