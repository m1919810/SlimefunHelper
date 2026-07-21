package me.matl114.hooks.impl;

import net.caffeinemc.mods.sodium.client.util.FogParameters;

public class SodiumRenderFix {
    public static FogParameters applyNoFogParameters(FogParameters fogParameters) {
        return new FogParameters(
                fogParameters.red(),
                fogParameters.green(),
                fogParameters.blue(),
                fogParameters.alpha(),
                fogParameters.environmentalEnd(),
                fogParameters.environmentalEnd(),
                fogParameters.renderEnd() * 2,
                fogParameters.renderEnd() * 2,
                fogParameters.cullDistance() * 2);
    }
}
