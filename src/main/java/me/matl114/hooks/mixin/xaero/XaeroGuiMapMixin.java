package me.matl114.hooks.mixin.xaero;

import java.util.ArrayList;

import lombok.Getter;
import me.matl114.hooks.XaeroHooks;
import me.matl114.hooks.access.XaeroGuiMapAccess;
import me.matl114.hooks.impl.xaeroworldmap.MapClickContext;
import me.matl114.hooks.impl.xaeroworldmap.RightClickPosOption;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(GuiMap.class)
@Getter
public abstract class XaeroGuiMapMixin implements IRightClickableElement , XaeroGuiMapAccess {
    @Shadow
    private RegistryKey<World> rightClickDim;

    @Shadow
    private int rightClickX;

    @Shadow
    private int rightClickY;

    @Shadow
    private int rightClickZ;


}
