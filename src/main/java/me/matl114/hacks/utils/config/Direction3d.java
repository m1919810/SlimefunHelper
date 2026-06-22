package me.matl114.hacks.utils.config;

import me.matl114.managers.config.ConfigEnum;
import net.minecraft.util.math.Direction;

public enum Direction3d implements ConfigEnum {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);

    final Direction delegate;

    Direction3d(Direction delegate) {
        this.delegate = delegate;
    }

    public Direction to() {
        return delegate;
    }

    private static final Direction3d[] ALL = values();

    public static Direction3d from(Direction delegate) {
        return ALL[delegate.ordinal()];
    }
}
