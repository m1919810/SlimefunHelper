package me.matl114.SlimefunMixin;

import me.matl114.Access.ModelOverrideListAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.json.ModelOverride;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ModelOverrideList.class)
public abstract class ModelOverrideListMixin implements ModelOverrideListAccess {
    @Final
    @Shadow
    private Identifier[] conditionTypes;
//    @Final
//    @Shadow
//    private final ModelOverrideList.BakedOverride[] overrides;
    @Override  @Unique
    public List<Identifier> getConditions(){
        return List.of(conditionTypes);
    }
    @Override @Unique
    public List<ModelOverrideList.BakedOverride> getOverrides(){
        return List.of(((ModelOverrideList)(Object)this).overrides);
    }

}
