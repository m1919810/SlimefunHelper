package me.matl114.hacks.modules.render;

import java.util.Map;
import java.util.Set;
import me.matl114.accessors.access.SoundInstanceAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public class NoSound extends BaseModule {
    public NoSound() {
        super("NoSound");
        bindFlag(enable);
    }

    public ModulePath root = makePath(Configs.RENDER_CONFIG, "sounds.no-sounds");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef cancelPlay = flagBuilder(root.add("cancel-sound-play")).build();

    public final FlagRef cancelHudDisplay =
            flagBuilder(root.add("cancel-hud-display")).build();

    public final NBTRef<EntrySet<SoundEvent>> noSounds = builder(
                    root.add("no-sounds"), EntrySet.<SoundEvent>parameter())
            .defaultValue(new EntrySet<>(
                    Registries.SOUND_EVENT,
                    Set.of(
                            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(),
                            SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND.value())))
            .build();

    public final NBTRef<EntryPrimitiveMap<SoundEvent, Double>> soundVolumeOverride = builder(
                    root.add("volume-override"), EntryPrimitiveMap.<SoundEvent, Double>parameter())
            .defaultValue(new EntryPrimitiveMap<>(Registries.SOUND_EVENT, NBTTypes.DOUBLE_TYPE, Map.of()))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getSoundPlayEvent(), this::onSoundPlay);
        registerListener(Listener.getSoundAddToHudEvent(), this::onSoundAddToHud);
    }

    public void onSoundPlay(Event<SoundInstance> event) {
        if (cancelPlay.get() && shouldCancel(event.context)) {
            event.cancel();
        } else {
            modifyVolume(event.context);
        }
    }

    public void onSoundAddToHud(Event<SoundInstance> event) {
        if (cancelHudDisplay.get() && shouldCancel(event.context)) {
            event.cancel();
        }
    }

    public boolean shouldCancel(SoundInstance soundInstance) {
        if (checkNull() || !enable.get()) {
            return false;
        }
        Identifier id = soundInstance.getId();
        if (id == null) {
            return false;
        }
        SoundEvent soundEvent = Registries.SOUND_EVENT.get(id);
        return soundEvent != null && noSounds.get().set().contains(soundEvent);
    }

    public void modifyVolume(SoundInstance instance) {
        Identifier id = instance.getId();
        SoundEvent soundEvent = Registries.SOUND_EVENT.get(id);
        if (soundEvent != null && instance instanceof AbstractSoundInstance instance1) {
            Double volume = soundVolumeOverride.get().getEntryValue(soundEvent);
            if (volume != null) {
                SoundInstanceAccess.of(instance1).setScale(volume);
            }
        }
    }
}
