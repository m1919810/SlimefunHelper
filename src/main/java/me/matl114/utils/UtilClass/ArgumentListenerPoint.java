package me.matl114.utils.UtilClass;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ArgumentListenerPoint<W extends Object> extends EntryPoint<BiConsumer<W,Object[]>,W> {
    @Override
    public boolean handleValue(W express, Object... arguments) {
        this.handlers.forEach(wBiConsumer -> wBiConsumer.accept(express,arguments));
        return true;
    }
    public void registerSimple(Consumer<W> val){
        registerHandler(((w, objects) -> val.accept(w)));
    }
}
