package me.matl114.SlimefunMixin.RenderMixin;

import me.matl114.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

@Environment(value=EnvType.CLIENT)
@Mixin(value = JsonUnbakedModel.class,priority = 990)
public abstract class JsonUnbakedModelMixin implements UnbakedModel {
    //fix ommc wrongly mixin unbakedModel
    @Inject(method = "bake(Lnet/minecraft/client/render/model/Baker;Lnet/minecraft/client/render/model/json/JsonUnbakedModel;Ljava/util/function/Function;Lnet/minecraft/client/render/model/ModelBakeSettings;Lnet/minecraft/util/Identifier;Z)Lnet/minecraft/client/render/model/BakedModel;",
            at = @At(value = "HEAD"), cancellable = true)
    private void generateCustomBakedModel(Baker baker, JsonUnbakedModel parent, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings settings, Identifier id, boolean hasDepth, CallbackInfoReturnable<BakedModel> cir) {
        if(ModConfig.isEnableBlockModelProtect()){
           // Debug.info("Fixing OMMC Model Errors");
            cir.setReturnValue(rewriteSafeBkae(baker, parent, textureGetter, settings, id, hasDepth));
            cir.cancel();
        }
    }
    @Invoker("bake")
    public abstract BakedModel invokeBake(Baker baker, JsonUnbakedModel parent, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings settings, Identifier id, boolean hasDepth);
    private BakedModel rewriteSafeBkae(Baker baker, JsonUnbakedModel parent, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings settings, Identifier id, boolean hasDepth){
        Sprite sprite = (Sprite)textureGetter.apply(this.resolveSprite("particle"));
        if (this.getRootModel() == ModelLoader.BLOCK_ENTITY_MARKER) {
            return new BuiltinBakedModel(this.getTransformations(), this.compileOverrides(baker, parent), sprite, this.getGuiLight().isSide());
        } else {
            BasicBakedModel.Builder builder = (new BasicBakedModel.Builder((JsonUnbakedModel)(Object) this, this.compileOverrides(baker, parent), hasDepth)).setParticle(sprite);
            Iterator var9 = this.getElements().iterator();

            while(var9.hasNext()) {
                ModelElement modelElement = (ModelElement)var9.next();
                Iterator var11 = modelElement.faces.keySet().iterator();

                while(var11.hasNext()) {
                    Direction direction = (Direction)var11.next();
                    ModelElementFace modelElementFace = (ModelElementFace)modelElement.faces.get(direction);
                    Sprite sprite2 = (Sprite)textureGetter.apply(this.resolveSprite(modelElementFace.textureId));
                    if (modelElementFace.cullFace == null) {
                        builder.addQuad(createQuad(modelElement, modelElementFace, sprite2, direction, settings, id));
                    } else {
                        builder.addQuad(Direction.transform(settings.getRotation().getMatrix(), modelElementFace.cullFace), createQuad(modelElement, modelElementFace, sprite2, direction, settings, id));
                    }
                }
            }

            return builder.build();
        }
    }
    @Shadow
    public static BakedQuad createQuad(ModelElement modelElement, ModelElementFace modelElementFace, Sprite sprite2, Direction direction, ModelBakeSettings settings, Identifier id) {
        throw new NullPointerException("not implemented yet");
    }

    @Shadow
    public abstract List<ModelElement> getElements() ;
    @Shadow
    public abstract JsonUnbakedModel.GuiLight getGuiLight() ;

    @Shadow
    public abstract ModelOverrideList compileOverrides(Baker baker, JsonUnbakedModel parent);

    @Shadow
    public abstract ModelTransformation getTransformations() ;

    @Shadow
    public abstract JsonUnbakedModel getRootModel() ;

    @Shadow
    public abstract SpriteIdentifier resolveSprite(String particle) ;
}
