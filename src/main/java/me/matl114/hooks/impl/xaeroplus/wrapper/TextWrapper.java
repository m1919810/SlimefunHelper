package me.matl114.hooks.impl.xaeroplus.wrapper;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TextWrapper<T> extends ElementWrapper<T> {
    public String value;
    public int x;
    public int z;
    public int color;
    public float scale;
}
