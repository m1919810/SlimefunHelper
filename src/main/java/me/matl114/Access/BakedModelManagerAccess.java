package me.matl114.Access;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.util.Identifier;

import java.util.Map;

public interface BakedModelManagerAccess {
    BakedModel getBakedModel(Identifier model);
    Map<Identifier, BakedModel> getAllBakedModels();
    static BakedModelManagerAccess of(BakedModelManager manager) {
        return (BakedModelManagerAccess) manager;
    }
}
