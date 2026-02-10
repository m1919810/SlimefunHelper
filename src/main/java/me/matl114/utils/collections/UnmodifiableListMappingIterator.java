package me.matl114.utils.collections;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class UnmodifiableListMappingIterator<T, W> implements Iterator<W> {
    public int index = 0;
    public final int size;
    public final List<T> value;
    public final Function<T, W> func;

    public UnmodifiableListMappingIterator(List<T> val, Function<T, W> func) {
        this.size = val.size();
        this.func = func;
        this.value = val;
    }

    @Override
    public boolean hasNext() {
        return index < size;
    }

    @Override
    public W next() {
        return (func.apply(value.get(index++)));
    }
}
