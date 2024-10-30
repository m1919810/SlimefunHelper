package me.matl114.SlimefunMixin;

import com.google.common.collect.ImmutableMap;
import me.matl114.Access.BakedModelManagerAccess;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import oshi.annotation.concurrent.Immutable;

import java.util.Collections;
import java.util.Map;

@Mixin(BakedModelManager.class)
public abstract class BakedModelManagerMixin implements BakedModelManagerAccess {
    @Shadow
    private Map<Identifier, BakedModel> models;

    @Shadow public abstract BakedModel getMissingModel();

    @Override @Unique
    public BakedModel getBakedModel(Identifier model) {

        return models.getOrDefault(model, this.getMissingModel());
    }
    @Override @Unique
    public Map<Identifier, BakedModel> getAllBakedModels() {
        return Collections.unmodifiableMap(this.models);
    }

}
