package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Iterator;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.SubtitlesHud;
import net.minecraft.client.sound.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SoundSystem.class)
public abstract class SoundSystemEvents {
    @Shadow
    @Final
    private List<SoundInstanceListener> listeners;

    @Shadow
    @Final
    private SoundManager loader;

    @WrapOperation(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/sound/SoundInstanceListener;onSoundPlayed(Lnet/minecraft/client/sound/SoundInstance;Lnet/minecraft/client/sound/WeightedSoundSet;F)V"))
    private void play(
            SoundInstanceListener instance,
            SoundInstance soundInstance,
            WeightedSoundSet weightedSoundSet,
            float v,
            Operation<Void> original) {
        if (instance instanceof SubtitlesHud hud) {
            Event<SoundInstance> event = new Event<>(soundInstance, true, false);
            Listener.getSoundAddToHudEvent().handleValue(event);
            if (event.isCancelled()) {
                return;
            } else {
                original.call(instance, event.context, weightedSoundSet, v);
            }
        } else {
            original.call(instance, soundInstance, weightedSoundSet, v);
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onInterceptPlay(
            SoundInstance sound, CallbackInfo ci, @Local(argsOnly = true) LocalRef<SoundInstance> args) {
        Event<SoundInstance> event = new Event<>(sound, true, true);
        Listener.getSoundPlayEvent().handleValue(event);
        if (event.isCancelled()) {
            ci.cancel();
            onSoundPlayed(event.context);
            return;
        }
        if (sound != event.context) {
            args.set(event.context);
        }
    }

    @Unique
    private void onSoundPlayed(SoundInstance sound) {
        // need getSoundSet to initialize getSound , wtf mojang pieces of shit
        WeightedSoundSet weightedSoundSet = sound.getSoundSet(this.loader);
        if (weightedSoundSet == null) {
            return;
        }
        Sound sound2 = sound.getSound();
        if (sound2 == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            return;
        } else if (sound2 == SoundManager.MISSING_SOUND) {
            return;
        }
        if (!this.listeners.isEmpty()) {

            boolean bl = sound.isRelative();
            SoundInstance.AttenuationType attenuationType = sound.getAttenuationType();
            float f = sound.getVolume();
            float g = Math.max(f, 1.0F) * (float) sound2.getAttenuation();
            float j = !bl && attenuationType != SoundInstance.AttenuationType.NONE ? g : Float.POSITIVE_INFINITY;
            Iterator var13 = this.listeners.iterator();

            while (var13.hasNext()) {
                SoundInstanceListener soundInstanceListener = (SoundInstanceListener) var13.next();
                if (soundInstanceListener instanceof SubtitlesHud) {
                    Event<SoundInstance> event = new Event<>(sound, true, true);
                    Listener.getSoundAddToHudEvent().handleValue(event);
                    if (event.isCancelled()) {
                        continue;
                    }
                }
                soundInstanceListener.onSoundPlayed(sound, weightedSoundSet, j);
            }
        }
    }
}
