package me.matl114.mixins.versioned;

import me.matl114.versioned.accessors.PlayerInputAccess;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Input.class)
public abstract class PlayerInputMixin implements PlayerInputAccess {

    @Unique
    boolean pressingSprint;

    @Unique
    public boolean isPressingSprint() {
        return pressingSprint;
    }

    @Unique
    public void setPressingSprint(boolean pressingSprint) {
        this.pressingSprint = pressingSprint;
    }
}
