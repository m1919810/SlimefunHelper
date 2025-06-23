package me.matl114.utils.UtilClass;

import java.util.ArrayList;
import java.util.List;

public abstract class EntryPoint<T extends Object,W extends Object> {
    protected List<T> handlers = new ArrayList<>();
    public void registerHandler(T val){
        handlers.add(val);
    }
    public abstract boolean handleValue(W express, Object... arguments);
}
