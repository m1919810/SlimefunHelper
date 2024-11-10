package me.matl114.Access;

import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.util.Identifier;

import java.util.List;

public interface ModelOverrideListAccess {
    public List<Identifier> getConditions();
    public List<ModelOverrideList.BakedOverride> getOverrides();
    static ModelOverrideListAccess of(ModelOverrideList manager) {
        return (ModelOverrideListAccess) manager;
    }
}
