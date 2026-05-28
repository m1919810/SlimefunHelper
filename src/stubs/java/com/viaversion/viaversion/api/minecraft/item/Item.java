package com.viaversion.viaversion.api.minecraft.item;

public interface Item {
    default short data() {
        return 0;
    }

    default void setData(short data) {
        throw new UnsupportedOperationException();
    }

    Item copy();
}
