package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Map;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.minecraft.client.network.ClientRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientRegistries.class)
public abstract class ClientRegistriesEvents {
    @ModifyExpressionValue(
            method = "startTagReload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/registry/tag/TagPacketSerializer$Serialized;toRegistryTags(Lnet/minecraft/registry/Registry;)Lnet/minecraft/registry/tag/TagGroupLoader$RegistryTags;"))
    private static <T> TagGroupLoader.RegistryTags<T> onRegistryTagload(
            TagGroupLoader.RegistryTags<T> original,
            @Local(argsOnly = true) RegistryKey<? extends Registry<? extends T>> registryKey) {
        Map<TagKey<T>, List<RegistryEntry<T>>> tagMap = original.tags();
        Event<Map<TagKey<T>, List<RegistryEntry<T>>>> event = new Event<>(tagMap, false, true, original.key());
        Listener.getRegistryTagKeyReload().handleValue((Event) event);
        if (event.context != tagMap) {
            return new TagGroupLoader.RegistryTags<>(original.key(), event.context);
        }
        return original;
    }
}
