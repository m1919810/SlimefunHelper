package me.matl114.hooks.mixin.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.hacks.modules.render.NoRender;
import me.matl114.hooks.impl.sodium.SodiumRenderFix;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(SodiumWorldRenderer.class)
public abstract class SodiumWorldRendererMixin {
    @Inject(
            method =
                    "Lnet/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;)V",
            at = @At("HEAD"),
            require = 0,
            expect = 0)
    private void onSodiumWorldRenderFog(
            Camera camera,
            Viewport viewport,
            FogParameters fogParameters,
            boolean spectator,
            boolean updateChunksImmediately,
            ChunkRenderMatrices matrices,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<FogParameters> fogParametersLocalRef) {
        if (NoRender.INSTANCE.noDistanceFog()) {
            fogParametersLocalRef.set(SodiumRenderFix.applyNoFogParameters(fogParameters));
        }
    }
}
