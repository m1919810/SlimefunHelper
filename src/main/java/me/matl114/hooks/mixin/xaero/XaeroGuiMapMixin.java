package me.matl114.hooks.mixin.xaero;

import lombok.Getter;
import me.matl114.hooks.access.XaeroGuiMapAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(GuiMap.class)
@Getter
public abstract class XaeroGuiMapMixin implements IRightClickableElement, XaeroGuiMapAccess {
    @Shadow
    private RegistryKey<World> rightClickDim;

    @Shadow
    private int rightClickX;

    @Shadow
    private int rightClickY;

    @Shadow
    private int rightClickZ;
}
