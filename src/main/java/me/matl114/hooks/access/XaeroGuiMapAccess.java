package me.matl114.hooks.access;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Shadow;

public interface XaeroGuiMapAccess {

    public RegistryKey<World> getRightClickDim();

    public int getRightClickX();

    public int getRightClickY();

    public int getRightClickZ();
}
