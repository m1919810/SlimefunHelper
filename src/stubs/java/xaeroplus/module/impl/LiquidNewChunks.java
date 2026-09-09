package xaeroplus.module.impl;

import xaeroplus.feature.highlights.SavableHighlightCacheInstance;
import xaeroplus.module.Module;

public class LiquidNewChunks extends Module {
    public final SavableHighlightCacheInstance newChunksCache = new SavableHighlightCacheInstance("XaeroPlusNewChunks");
    // chunks where liquid was already flowing or flowed when we loaded it
    public final SavableHighlightCacheInstance inverseNewChunksCache =
            new SavableHighlightCacheInstance("XaeroPlusNewChunksLiquidInverse");
}
