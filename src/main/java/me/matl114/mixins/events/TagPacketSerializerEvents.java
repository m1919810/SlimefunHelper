package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Map;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.TagPacketSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TagPacketSerializer.class)
public abstract class TagPacketSerializerEvents {
    @WrapOperation(
            method = "toRegistryTags",
            at =
                    @At(
                            value = "NEW",
                            target =
                                    "(Lnet/minecraft/registry/RegistryKey;Ljava/util/Map;)Lnet/minecraft/registry/tag/TagGroupLoader$RegistryTags;"))
    private static <T> TagGroupLoader.RegistryTags<T> onRegistryTagLoad(
            RegistryKey<T> key,
            Map<TagKey<T>, List<RegistryEntry<T>>> tags,
            Operation<TagGroupLoader.RegistryTags<T>> original) {
        Map<TagKey<T>, List<RegistryEntry<T>>> tagMap = tags;
        Event<Map<TagKey<T>, List<RegistryEntry<T>>>> event = new Event<>(tagMap, false, true, key);
        Listener.getRegistryTagKeyReload().handleValue((Event) event);
        return original.call(key, event.context);
    }
}
