package me.matl114.utils.UtilClass;

import java.util.function.Consumer;

public class ListenerPoint<W extends Object> extends EntryPoint<Consumer<W>, W>{
    @Override
    public boolean handleValue(W express, Object... arguments) {
        this.handlers.forEach(i->i.value.accept(express));
        return true;
    }
}
