package me.matl114.hooks.impl.xaeroplus.wrapper;

import java.util.function.Function;

public abstract class ElementWrapper<T> {
    T element;
    Function<? extends ElementWrapper<T>, T> elementGenerator;

    public T getElement() {
        if (element == null) {
            if (elementGenerator != null) {
                element = (T) ((Function) elementGenerator).apply(this);
            }
        }
        return element;
    }

    public <W, R extends ElementWrapper<W>> ElementWrapper<W> inject(Function<R, W> function) {
        this.elementGenerator = (Function) function;
        return (ElementWrapper<W>) this;
    }
}
