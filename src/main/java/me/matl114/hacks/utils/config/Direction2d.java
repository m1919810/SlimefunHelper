package me.matl114.hacks.utils.config;

import me.matl114.managers.config.ConfigEnum;

public enum Direction2d implements ConfigEnum {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);
    final int x, y;

    Direction2d(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
