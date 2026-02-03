package me.matl114.utils.collections;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HolderWithState<T> {
    public HolderWithState(T val){
        this(val, false);
    }
    public static HolderWithState EMPTY_COMPLETE= new HolderWithState(null, true);

    public T val;
    public boolean state;
    public boolean isNull(){
        return val ==null;
    }
    public boolean has(){
        return state;
    }
    public boolean hasNot(){
        return !state;
    }
}
