package me.matl114.mixins.events;

import me.matl114.events.RenderListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemAsset;
import net.minecraft.client.item.ItemAssetsLoader;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Environment(EnvType.CLIENT)
@Mixin(ItemAssetsLoader.class)
public abstract class ItemAssetsLoaderEvents {
    @Inject(method = "load", at = @At("RETURN"), cancellable = true)
    private static void onItemAssetLoad(ResourceManager resourceManager, Executor executor, CallbackInfoReturnable<CompletableFuture<ItemAssetsLoader.Result>> cir){
        CompletableFuture<ItemAssetsLoader.Result> future = cir.getReturnValue();
        CompletableFuture<Map<Identifier, ItemAsset>> customLoadingAssets = CompletableFuture.supplyAsync(()->{
            Collection<Identifier> ids = RenderListener.getReloadingResources(resourceManager);
            Map<Identifier, ItemAsset> autoAssets = new HashMap<>(ids.size());
            for(Identifier id : ids){
                autoAssets.put(id, new ItemAsset(new BasicItemModel.Unbaked(id, new ArrayList<>()), new ItemAsset.Properties(true, false)));
            }
            return autoAssets;
        }, executor);
        cir.setReturnValue(CompletableFuture.allOf(future, customLoadingAssets).thenApplyAsync((async)->{
            ItemAssetsLoader.Result result = future.join();
            Map<Identifier, ItemAsset> autoAssets = customLoadingAssets.join();
            result.contents().putAll(autoAssets);
            return result;
        }));
    }
}
