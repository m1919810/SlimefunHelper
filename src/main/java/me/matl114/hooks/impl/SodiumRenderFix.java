package me.matl114.hooks.impl;

import net.caffeinemc.mods.sodium.client.util.FogParameters;

public class SodiumRenderFix {
    public static FogParameters applyNoFogParameters(FogParameters fogParameters) {
        return new FogParameters(
                fogParameters.red(),
                fogParameters.green(),
                fogParameters.blue(),
                0,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                Float.MAX_VALUE);
    }
}
