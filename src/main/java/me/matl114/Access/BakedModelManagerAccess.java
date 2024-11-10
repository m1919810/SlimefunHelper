package me.matl114.Access;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.util.Identifier;

import java.util.Map;

public interface BakedModelManagerAccess {
    BakedModel getBakedModel(Identifier model);
    BakedModel getThisMissingModel();
    Map<Identifier, BakedModel> getAllBakedModels();
    SpriteAtlasManager getSpriteAtlasManager();
    static BakedModelManagerAccess of(BakedModelManager manager) {
        return (BakedModelManagerAccess) manager;
    }
}
