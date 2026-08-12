package me.matl114.accessors.access;

import net.minecraft.client.sound.AbstractSoundInstance;

public interface SoundInstanceAccess {
    public void setScale(double scale);

    public static SoundInstanceAccess of(AbstractSoundInstance instance) {
        return (SoundInstanceAccess) instance;
    }
}
