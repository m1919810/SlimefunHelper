package me.matl114.hooks.impl.xaeroplus.wrapper;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class EllipseWrapper<T> extends ElementWrapper<T> {
    public int centerX;
    public int centerZ;
    public int radiusX;
    public int radiusZ;
}
