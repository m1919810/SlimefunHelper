package xaeroplus.feature.highlights;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SavableHighlightCacheInstance {
    private static final Set<SavableHighlightCacheInstance> INSTANCES = ConcurrentHashMap.newKeySet();
    private ChunkHighlightCache cache;
    private final String dbName;

    public SavableHighlightCacheInstance(String dbName) {
        this.dbName = dbName;
        // always starts as a local cache
        // to switch to disk cache call setDiskCache
        this.cache = new ChunkHighlightLocalCache();
    }

    public static Set<SavableHighlightCacheInstance> instances() {
        return INSTANCES;
    }

    public ChunkHighlightCache get() {
        return cache;
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
}
