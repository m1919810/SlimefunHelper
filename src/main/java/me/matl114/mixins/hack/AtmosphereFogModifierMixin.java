package me.matl114.mixins.hack;

import me.matl114.hacks.RenderTasks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogModifier.class)
public abstract class AtmosphereFogModifierMixin {
    /**
     * Removes the foggy overlay in the Overworld (including rain fog), if
     * NoFog is enabled.
     */
    @Inject(method = "applyStartEndModifier", at = @At("RETURN"))
    private void onApplyStartEndModifier(
        FogData data, Entity cameraEntity, BlockPos cameraPos, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (RenderTasks.getRenderExtra().noEffect.get()) {
            data.environmentalStart = 1000000;
            data.environmentalEnd = 1000000;
        }
    }
}
