package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.RenderListener;
import me.matl114.events.Event;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.SingleAtlasSource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mixin(AtlasLoader.class)
public abstract class AtlasLoaderEvents {
    @Inject(method = "of",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/atlas/AtlasLoader;<init>(Ljava/util/List;)V",shift = At.Shift.BEFORE),locals = LocalCapture.CAPTURE_FAILHARD)
    private static void loadSources(ResourceManager resourceManager, Identifier id, CallbackInfoReturnable<AtlasLoader> cir,  @Local List<AtlasSource> list) {
        Event<Set<Identifier>> resourceReloadEvent = new Event<>(new LinkedHashSet<>(), false,false, resourceManager, id);
        RenderListener.getAtlasSourceSupply().handleValue(resourceReloadEvent);
        list.addAll(resourceReloadEvent.context().stream().map(i -> new SingleAtlasSource(i, Optional.empty())).toList());

    }
}
