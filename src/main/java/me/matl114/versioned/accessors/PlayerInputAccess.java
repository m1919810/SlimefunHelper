package me.matl114.versioned.accessors;

import net.minecraft.client.input.KeyboardInput;

public interface PlayerInputAccess {
    boolean isPressingSprint();

    void setPressingSprint(boolean pressingSprint);

    public static PlayerInputAccess of(KeyboardInput input) {
        return (PlayerInputAccess) input;
    }
}
