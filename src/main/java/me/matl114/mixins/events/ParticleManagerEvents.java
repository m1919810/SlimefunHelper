package me.matl114.mixins.events;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ParticleManager.class)
public abstract class ParticleManagerEvents {
    @Inject(method = "createParticle", at = @At("RETURN"), cancellable = true)
    private void createParticleEvent(
            ParticleEffect parameters,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfoReturnable<Particle> cir) {
        if (parameters == null) return;
        Particle particle = cir.getReturnValue();
        if (particle != null) {
            Event<Particle> eventParticle = new Event<>(particle, true, true, parameters);
            Listener.getParticleCreateListener().handleValue(eventParticle);
            if (eventParticle.isCancelled()) {
                cir.setReturnValue(null);
            } else {
                if (particle != eventParticle.context) {
                    cir.setReturnValue(eventParticle.context);
                }
            }
        }
    }
}
