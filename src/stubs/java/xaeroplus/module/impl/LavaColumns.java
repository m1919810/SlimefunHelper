package xaeroplus.module.impl;

import xaeroplus.feature.highlights.SavableHighlightCacheInstance;
import xaeroplus.module.Module;

public class LavaColumns extends Module {
    public final SavableHighlightCacheInstance lavaColumnsCache =
            new SavableHighlightCacheInstance("XaeroPlusLavaColumns");
}
