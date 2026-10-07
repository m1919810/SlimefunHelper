package me.matl114.mixins.access;

import java.util.Optional;
import me.matl114.accessors.access.RenderLayerAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin implements RenderLayerAccess {
    @Shadow
    @Final
    private RenderSetup renderSetup;

    @Unique
    Optional<Identifier> optionalRenderTexture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        optionalRenderTexture =
                this.renderSetup.textures.values().stream().findFirst().map(RenderSetup.TextureSpec::location);
    }

    public Optional<Identifier> getRenderTexture() {
        return optionalRenderTexture;
    }
}
