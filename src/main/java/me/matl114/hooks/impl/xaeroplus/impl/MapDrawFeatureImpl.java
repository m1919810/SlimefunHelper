package me.matl114.hooks.impl.xaeroplus.impl;

import me.matl114.hooks.impl.xaeroplus.IMapDrawFeature;
import xaeroplus.Globals;
import xaeroplus.feature.render.DrawFeature;

public record MapDrawFeatureImpl(String id, DrawFeature feature) implements IMapDrawFeature {
    public MapDrawFeatureImpl(DrawFeature feature) {
        this(feature.id(), feature);
    }

    @Override
    public void register() {
        Globals.drawManager.registry().register(feature);
    }

    @Override
    public void unregister() {
        Globals.drawManager.registry().unregister(id);
    }
}
