package me.matl114.mixins.access;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.matl114.accessors.access.SoundInstanceAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractSoundInstance.class)
public abstract class SoundInstanceMixin implements SoundInstanceAccess {
    @Unique
    Float scaleVolume;

    @ModifyReturnValue(method = "getVolume", at = @At("RETURN"))
    private float getVolume(float volumn) {
        if (scaleVolume == null) {
            return volumn;
        }
        return volumn * scaleVolume;
    }

    @Unique
    public void setScale(double scale) {
        this.scaleVolume = (float) scale;
    }
}
