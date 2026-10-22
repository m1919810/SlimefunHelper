package me.matl114.mixins.access;

import java.util.Optional;
import me.matl114.accessors.access.RenderLayerAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(RenderLayer.MultiPhase.class)
public abstract class RenderLayerMixin implements RenderLayerAccess {
    @Unique
    private Optional<Identifier> optionalRenderTexture = Optional.empty();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        optionalRenderTexture = ((RenderLayer.MultiPhase) (Object) this).phases.texture.getId();
    }

    @Override
    public Optional<Identifier> getRenderTexture() {
        return optionalRenderTexture;
    }
}
