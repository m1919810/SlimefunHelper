package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.NoRender;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    @Shadow
    @Final
    private Identifier id;

    @Inject(method = "tickAnimatedSprites", at = @At("HEAD"), cancellable = true)
    private void noBlockAnimation(CallbackInfo ci) {
        if (NoRender.INSTANCE.noBlockAnimation() && SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE.equals(this.id)) {
            ci.cancel();
        }
    }
}
