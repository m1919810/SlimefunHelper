package me.matl114.utils.config.kv;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;

public abstract class ListAttrKeyValue<T> extends BaseAttrKeyValue<List<T>> {

    @Getter
    protected List<Predicate<T>> elementValidators = new ArrayList<>();

    public ListAttrKeyValue(
            String key,
            List<T> value,
            CustomWidgetFactory<List<T>> customWidgetFactory,
            WrapperFactory<String, List<T>> wrapperFactory) {
        super(key, value, customWidgetFactory, wrapperFactory);
    }

    public <W extends AttrKeyValue<List<T>>> W copy() {
        ListAttrKeyValue<T> val = super.copy();
        val.elementValidators = new ArrayList<>(val.elementValidators);
        return (W) val;
    }

    public abstract List<AttrKeyValue<T>> createAttrKeyValueForElements();

    public abstract AttrKeyValue<T> createNewAttrKeyValueElement();
}
