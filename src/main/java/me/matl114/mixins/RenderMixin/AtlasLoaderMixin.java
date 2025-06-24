package me.matl114.mixins.RenderMixin;

import me.matl114.utils.Debug;
import me.matl114.renders.SlimefunCustomModelManager;
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

import java.util.List;
import java.util.Optional;

@Mixin(AtlasLoader.class)
public abstract class AtlasLoaderMixin {
    private static Identifier targetIdentifier = new Identifier("minecraft","blocks");
    @Inject(method = "of",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/atlas/AtlasLoader;<init>(Ljava/util/List;)V",shift = At.Shift.BEFORE),locals = LocalCapture.CAPTURE_FAILHARD)
    private static void loadSources(ResourceManager resourceManager, Identifier id, CallbackInfoReturnable<AtlasLoader> cir, Identifier identifier, List<AtlasSource> list) {
        if(targetIdentifier.equals(id)){
            Debug.info("Loading blocks atlases");
            Debug.info("Appending our textures automatically");
           list.addAll(  SlimefunCustomModelManager.loadOurselvesCustomModelTexture(resourceManager).stream().map(i->new SingleAtlasSource(i, Optional.empty())).toList() );
        }
    }
}
