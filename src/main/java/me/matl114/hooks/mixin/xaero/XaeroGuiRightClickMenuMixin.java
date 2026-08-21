package me.matl114.hooks.mixin.xaero;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.element.HoveredMapElementHolder;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;

import java.util.ArrayList;

@Environment(EnvType.CLIENT)
@Mixin(GuiRightClickMenu.class)
public abstract class XaeroGuiRightClickMenuMixin {
    @WrapOperation(method = "Lxaero/map/gui/dropdown/rightclick/GuiRightClickMenu;getMenu(Lxaero/map/gui/IRightClickableElement;Lxaero/map/gui/GuiMap;III)Lxaero/map/gui/dropdown/rightclick/GuiRightClickMenu;", at = @At(value = "INVOKE", target = "Lxaero/map/gui/IRightClickableElement;getRightClickOptions()Ljava/util/ArrayList;"), remap = false)
    private static ArrayList<RightClickOption> onRightClickOptionsAdd(IRightClickableElement rightClickable, Operation<ArrayList<RightClickOption>> original, @Local(argsOnly = true) GuiMap screen) {
        var contexts = original.call(rightClickable);
        if(screen instanceof XaeroGuiMapAccess access){
            ArrayList<MapClickContext> list = new ArrayList<>();
            RegistryKey<World> world = access.getRightClickDim();
            BlockPos pos;
            if(rightClickable instanceof HoveredMapElementHolder<?,?> hoveredMapElementHolder && hoveredMapElementHolder.getElement() instanceof Waypoint waypoint){
                pos = new BlockPos(waypoint.getX(), waypoint.getY(), waypoint.getZ());
            }else{
                pos = new BlockPos(access.getRightClickX(), access.getRightClickY(), access.getRightClickZ());
            }
            XaeroHooks.getWorldMapRightClickOption().broadcast(list, world, pos);
            if (!list.isEmpty()) {
                for (var re : list) {
                    contexts.add(new RightClickPosOption(re, world, pos, contexts.size(), screen));
                }
            }
        }
        return contexts;
    }
}
