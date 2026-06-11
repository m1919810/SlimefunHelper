package me.matl114.mixins.gui;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import me.matl114.hacks.ModelTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value = EnvType.CLIENT)
@Mixin(value = JsonUnbakedModel.class, priority = 990)
public abstract class JsonUnbakedModelMixin implements UnbakedModel {

    // fix ommc wrongly mixin unbakedModel
    @Inject(
            method =
                    "bake(Lnet/minecraft/client/render/model/Baker;Lnet/minecraft/client/render/model/json/JsonUnbakedModel;Ljava/util/function/Function;Lnet/minecraft/client/render/model/ModelBakeSettings;Z)Lnet/minecraft/client/render/model/BakedModel;",
            at = @At(value = "HEAD"),
            cancellable = true)
    private void generateCustomBakedModel(
            Baker baker,
            JsonUnbakedModel parent,
            Function<SpriteIdentifier, Sprite> textureGetter,
            ModelBakeSettings settings,
            boolean bl,
            CallbackInfoReturnable<BakedModel> cir) {
        if (ModelTasks.getModelExtra().enableProtect.get()) {
            // Debug.info("Fixing OMMC Model Errors");
            cir.setReturnValue(rewriteSafeBkae(baker, parent, textureGetter, settings, bl));
            cir.cancel();
        }
    }

    public BakedModel rewriteSafeBkae(
            Baker baker,
            JsonUnbakedModel parent,
            Function<SpriteIdentifier, Sprite> textureGetter,
            ModelBakeSettings settings,
            boolean bl) {
        Sprite sprite = (Sprite) textureGetter.apply(this.resolveSprite("particle"));
        if (this.getRootModel() == ModelLoader.BLOCK_ENTITY_MARKER) {
            return new BuiltinBakedModel(
                    this.getTransformations(),
                    this.compileOverrides(baker, parent),
                    sprite,
                    this.getGuiLight().isSide());
        } else {
            BasicBakedModel.Builder builder = (new BasicBakedModel.Builder(
                            (JsonUnbakedModel) (Object) this, this.compileOverrides(baker, parent), bl))
                    .setParticle(sprite);
            Iterator var8 = this.getElements().iterator();

            while (var8.hasNext()) {
                ModelElement modelElement = (ModelElement) var8.next();
                Iterator var10 = modelElement.faces.keySet().iterator();

                while (var10.hasNext()) {
                    Direction direction = (Direction) var10.next();
                    ModelElementFace modelElementFace = (ModelElementFace) modelElement.faces.get(direction);
                    Sprite sprite2 = (Sprite) textureGetter.apply(this.resolveSprite(modelElementFace.textureId()));
                    if (modelElementFace.cullFace() == null) {
                        builder.addQuad(createQuad(modelElement, modelElementFace, sprite2, direction, settings));
                    } else {
                        builder.addQuad(
                                Direction.transform(settings.getRotation().getMatrix(), modelElementFace.cullFace()),
                                createQuad(modelElement, modelElementFace, sprite2, direction, settings));
                    }
                }
            }

            return builder.build();
        }
    }

    @Shadow
    public static BakedQuad createQuad(
            ModelElement element,
            ModelElementFace elementFace,
            Sprite sprite,
            Direction side,
            ModelBakeSettings settings) {
        throw new NullPointerException("not implemented yet");
    }

    @Shadow
    public abstract List<ModelElement> getElements();

    @Shadow
    public abstract JsonUnbakedModel.GuiLight getGuiLight();

    @Shadow
    public abstract ModelOverrideList compileOverrides(Baker baker, JsonUnbakedModel parent);

    @Shadow
    public abstract ModelTransformation getTransformations();

    @Shadow
    public abstract JsonUnbakedModel getRootModel();

    @Shadow
    public abstract SpriteIdentifier resolveSprite(String particle);
}
