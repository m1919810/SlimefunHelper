package me.matl114.hooks.mixin.litematica;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(PlacementManagerDaemonHandler.class)
@Environment(EnvType.CLIENT)
public abstract class PlacementManagerDaemonFixMixin {
    @WrapMethod(
            method = "Lfi/dy/masa/litematica/schematic/placement/PlacementManagerDaemonHandler;ensureThreadSafety()V",
            require = 0,
            remap = false)
    private void fix(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable e) {
            // do not crash my fucking client during shutdown, pls
            if (MinecraftClient.getInstance().isRunning()) {
                throw e;
            }
        }
    }
}
