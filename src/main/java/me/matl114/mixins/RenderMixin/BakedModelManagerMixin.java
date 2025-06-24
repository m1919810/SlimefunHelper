package me.matl114.mixins.RenderMixin;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import me.matl114.access.BakedModelManagerAccess;
import me.matl114.utils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.Reader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
@Mixin(BakedModelManager.class)
public abstract class BakedModelManagerMixin implements BakedModelManagerAccess {
    @Shadow
    private Map<ModelIdentifier, BakedModel> models;
    @Shadow
    @Final
    private SpriteAtlasManager atlasManager;
    @Final
    @Shadow
    private BlockColors colorMap;
    @Shadow
    private int mipmapLevels;
    @Shadow
    private BakedModel missingModel;
    @Shadow public abstract BakedModel getMissingModel();
    public BakedModel getThisMissingModel(){
        return this.missingModel;
    }

    @Override @Unique
    public BakedModel getBakedModel(Identifier model) {

        return models.getOrDefault(model, this.getMissingModel());
    }
    @Override @Unique
    public Map<ModelIdentifier, BakedModel> getAllBakedModels() {
        return Collections.unmodifiableMap(this.models);
    }

    @Override @Unique
    public SpriteAtlasManager  getSpriteAtlasManager(){
        return this.atlasManager;
    }
//    @Inject(method = "reload",at = @At("HEAD"), cancellable = true)
//    public final void reloadRewrite(ResourceReloader.Synchronizer synchronizer, ResourceManager manager, Profiler prepareProfiler, Profiler applyProfiler, Executor prepareExecutor, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir){
//
//    }




}
