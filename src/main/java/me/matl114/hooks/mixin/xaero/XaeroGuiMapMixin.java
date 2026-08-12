package me.matl114.hooks.mixin.xaero;

import java.util.ArrayList;
import me.matl114.hooks.XaeroHooks;
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
public abstract class XaeroGuiMapMixin implements IRightClickableElement {
    @Shadow
    private RegistryKey<World> rightClickDim;

    @Shadow
    private int rightClickX;

    @Shadow
    private int rightClickY;

    @Shadow
    private int rightClickZ;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"))
    private void onRightClickOptionsAdd(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        ArrayList<MapClickContext> list = new ArrayList<>();
        RegistryKey<World> world = this.rightClickDim;
        BlockPos pos = new BlockPos(this.rightClickX, this.rightClickY, this.rightClickZ);
        XaeroHooks.getWorldMapRightClickOption().broadcast(list, world, pos);
        if (!list.isEmpty()) {
            ArrayList<RightClickOption> contexts = cir.getReturnValue();
            for (var re : list) {
                contexts.add(new RightClickPosOption(re, world, pos, contexts.size(), this));
            }
        }
    }
}
