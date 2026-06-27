package me.matl114.versioned.accessors;

import net.minecraft.client.input.Input;

public interface PlayerInputAccess {
    boolean isPressingSprint();

    void setPressingSprint(boolean pressingSprint);

    public static PlayerInputAccess of(Input input) {
        return (PlayerInputAccess) input;
    }
}
