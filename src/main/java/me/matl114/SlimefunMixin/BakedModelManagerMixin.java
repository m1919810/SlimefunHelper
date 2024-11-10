package me.matl114.SlimefunMixin;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import me.matl114.Access.BakedModelManagerAccess;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import oshi.annotation.concurrent.Immutable;

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
    private Map<Identifier, BakedModel> models;
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
    public Map<Identifier, BakedModel> getAllBakedModels() {
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
    public final CompletableFuture<Void> myReload(ResourceReloader.Synchronizer synchronizer, ResourceManager manager, Profiler prepareProfiler, Profiler applyProfiler, Executor prepareExecutor, Executor applyExecutor) {
        Debug.info("execute MyReload");
        prepareProfiler.startTick();
        CompletableFuture<Map<Identifier, JsonUnbakedModel>> completableFuture = BakedModelManagerMixin. reloadModels(manager, prepareExecutor);
        CompletableFuture<Map<Identifier, List<ModelLoader.SourceTrackedData>>> completableFuture2 =BakedModelManagerMixin. reloadBlockStates(manager, prepareExecutor);
        CompletableFuture<ModelLoader> completableFuture3 = completableFuture.thenCombineAsync(completableFuture2, (jsonUnbakedModels, blockStates) -> {
            return new ModelLoader(this.colorMap, prepareProfiler, jsonUnbakedModels, blockStates);
        }, prepareExecutor);
        Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>> map = this.atlasManager.reload(manager, this.mipmapLevels, prepareExecutor);
        CompletableFuture var10000 = CompletableFuture.allOf((CompletableFuture[]) Stream.concat(map.values().stream(), Stream.of(completableFuture3)).toArray((i) -> {
            return new CompletableFuture[i];
        })).thenApplyAsync((void1) -> {
            return this.bake(prepareProfiler, (Map)map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> {
                return (SpriteAtlasManager.AtlasPreparation)((CompletableFuture)entry.getValue()).join();
            })), (ModelLoader)completableFuture3.join());
        }, prepareExecutor).thenCompose((result) -> {
            return result.readyForUpload.thenApply((void_) -> {
                return result;
            });
        });
        Objects.requireNonNull(synchronizer);
        return var10000.thenCompose(synchronizer::whenPrepared).thenAcceptAsync((result) -> {
            this.upload((BakedModelManager.BakingResult) result, applyProfiler);
        }, applyExecutor);
    }
    @Shadow
    protected abstract BakedModelManager.BakingResult bake(Profiler prepareProfiler, Map collect, ModelLoader join) ;

    private static CompletableFuture<Map<Identifier, JsonUnbakedModel>> reloadModels(ResourceManager resourceManager, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            return ModelLoader.MODELS_FINDER.findResources(resourceManager);
        }, executor).thenCompose((models) -> {
            List<CompletableFuture<Pair<Identifier, JsonUnbakedModel>>> list = new ArrayList(models.size());
            Iterator var3 = models.entrySet().iterator();

            while(var3.hasNext()) {
                Map.Entry<Identifier, Resource> entry = (Map.Entry)var3.next();
                list.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        Reader reader = ((Resource)entry.getValue()).getReader();

                        Pair var2;
                        try {
                            var2 = Pair.of((Identifier)entry.getKey(), JsonUnbakedModel.deserialize(reader));
                        } catch (Throwable var5) {
                            if (reader != null) {
                                try {
                                    reader.close();
                                } catch (Throwable var4) {
                                    var5.addSuppressed(var4);
                                }
                            }

                            throw var5;
                        }

                        if (reader != null) {
                            reader.close();
                        }

                        return var2;
                    } catch (Exception var6) {
                        Exception exception = var6;
                        Debug.info("Failed to load model {}", entry.getKey(), exception);
                        return null;
                    }
                }, executor));
            }

            return Util.combineSafe(list).thenApply((modelsx) -> {
                return (Map)modelsx.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
            });
        });
    }

    private static CompletableFuture<Map<Identifier, List<ModelLoader.SourceTrackedData>>> reloadBlockStates(ResourceManager resourceManager, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            return ModelLoader.BLOCK_STATES_FINDER.findAllResources(resourceManager);
        }, executor).thenCompose((blockStates) -> {
            List<CompletableFuture<Pair<Identifier, List<ModelLoader.SourceTrackedData>>>> list22 = new ArrayList(blockStates.size());
            Iterator var33 = blockStates.entrySet().iterator();

            while(var33.hasNext()) {
                Map.Entry<Identifier, List<Resource>> entry = (Map.Entry)var33.next();
                list22.add(CompletableFuture.supplyAsync(() -> {
                    List<Resource> list = (List)entry.getValue();
                    List<ModelLoader.SourceTrackedData> list2 = new ArrayList(list.size());
                    Iterator var3 = list.iterator();

                    while(var3.hasNext()) {
                        Resource resource = (Resource)var3.next();

                        try {
                            Reader reader = resource.getReader();

                            try {
                                JsonObject jsonObject = JsonHelper.deserialize(reader);
                                list2.add(new ModelLoader.SourceTrackedData(resource.getResourcePackName(), jsonObject));
                            } catch (Throwable var9) {
                                if (reader != null) {
                                    try {
                                        reader.close();
                                    } catch (Throwable var8) {
                                        var9.addSuppressed(var8);
                                    }
                                }

                                throw var9;
                            }

                            if (reader != null) {
                                reader.close();
                            }
                        } catch (Exception var10) {
                            Exception exception = var10;
                            Debug.info("Failed to load blockstate {} from pack {}", new Object[]{entry.getKey(), resource.getResourcePackName(), exception});
                        }
                    }

                    return Pair.of((Identifier)entry.getKey(), list2);
                }, executor));
            }

            return Util.combineSafe(list22).thenApply((blockStatesx) -> {
                return (Map)blockStatesx.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
            });
        });
    }

    @Shadow
    public abstract void upload(BakedModelManager.BakingResult bakingResult, Profiler profiler);


}
