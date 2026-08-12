package me.matl114.hooks.impl.xaeroplus;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import me.matl114.hooks.impl.xaeroplus.wrapper.ElementSupplier;
import me.matl114.hooks.impl.xaeroplus.wrapper.EllipseWrapper;
import me.matl114.hooks.impl.xaeroplus.wrapper.LineWrapper;
import me.matl114.hooks.impl.xaeroplus.wrapper.TextWrapper;

public interface IMapDrawFactory {
    /**
     * Refreshed on MC render thread
     * Single color across all ellipses
     * Color function is called each frame
     */
    public IMapDrawFeature ellipses(
            String id,
            ElementSupplier<List<EllipseWrapper<?>>> ellipseSupplier,
            IntSupplier colorSupplier,
            Supplier<Float> thicknessSupplier,
            int refreshIntervalMs);

    /**
     * Refreshed on MC render thread
     * Single color across all highlights
     * Color function is called each frame
     */
    public IMapDrawFeature chunkHighlights(
            String id,
            ElementSupplier<Long2LongMap> chunkHighlightSupplier,
            IntSupplier colorSupplier,
            int refreshIntervalMs);
    /**
     * Refreshed async, not on the MC render thread
     * Single color across all highlights
     * Color function is called each frame
     */
    public IMapDrawFeature asyncChunkHighlights(
            String id, ElementSupplier<Long2LongMap> chunkHighlightSupplier, IntSupplier colorSupplier);

    /**
     * Refreshed on MC render thread
     * Single color across all lines
     * Color function is called each frame
     */
    public IMapDrawFeature lines(
            String id,
            ElementSupplier<List<LineWrapper<?>>> lineSupplier,
            IntSupplier colorSupplier,
            Supplier<Float> lineWidthSupplier,
            int refreshIntervalMs);

    /**
     * Refreshed every frame on MC render thread
     */
    public IMapDrawFeature text(String id, ElementSupplier<Long2ObjectMap<TextWrapper<?>>> textSupplier);

    /**
     * Refreshed async, not on the MC render thread
     */
    public IMapDrawFeature asyncText(
            String id, ElementSupplier<Long2ObjectMap<TextWrapper<?>>> textSupplier, int refreshIntervalMs);

    public void unregisterId(String id);
}
