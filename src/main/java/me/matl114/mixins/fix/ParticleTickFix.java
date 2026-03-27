package me.matl114.mixins.fix;

import me.matl114.hacks.RenderTasks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public abstract class ParticleTickFix {
    @Shadow
    protected boolean collidesWithWorld;

    @Shadow
    @Final
    protected ClientWorld world;

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        if (world.isClient()
                && RenderTasks.getRenderOptimize().enableParticleTickOpt.get()) {
            collidesWithWorld = false;
        }
    }
}
