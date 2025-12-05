package me.matl114.utils.UtilClass;

import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public abstract class EntryPoint<T extends Object,W extends Object> {
    @AllArgsConstructor
    protected static class H<T> implements Comparable<H<T>>{
        int priority;
        T value;

        @Override
        public int compareTo(@NotNull EntryPoint.H<T> th) {
            return this.priority - th.priority;
        }
    }
    protected List<H<T>> handlers = new ArrayList<>();
    public void registerHandler(T val){
        registerHandler(val, 0);
    }
    public void registerHandler(T val, int p){
        H<T> newHandler = new H<>(p, val);

        int index = 0;
        while (index < handlers.size() && handlers.get(index).priority <= p) {
            index++;
        }

        handlers.add(index, newHandler);
    }
    public abstract boolean handleValue(W express, Object... arguments);

    public boolean isEmpty(){
        return this.handlers.isEmpty();
    }
}
