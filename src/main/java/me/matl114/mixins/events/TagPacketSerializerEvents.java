package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Map;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.TagPacketSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(TagPacketSerializer.Serialized.class)
public abstract class TagPacketSerializerEvents {
    @ModifyArg(
            method = "loadTo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/Registry;populateTags(Ljava/util/Map;)V"))
    public <T> Map<TagKey<T>, List<RegistryEntry<T>>> modifyTagLoad(
            Map<TagKey<T>, List<RegistryEntry<T>>> tagEntries, @Local(argsOnly = true) Registry<T> registry) {
        Event<Map<TagKey<T>, List<RegistryEntry<T>>>> event = new Event<>(tagEntries, false, true, registry.getKey());
        Listener.getRegistryTagKeyReload().handleValue((Event) event);
        if (event.context != tagEntries) {
            return event.context;
        }
        return tagEntries;
    }
}
