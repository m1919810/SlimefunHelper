package xaeroplus.feature.render;

import java.util.function.IntSupplier;
import xaeroplus.feature.render.ellipse.EllipseSupplier;
import xaeroplus.feature.render.ellipse.MultiColorEllipseColorFunction;
import xaeroplus.feature.render.ellipse.MultiColorEllipseSupplier;
import xaeroplus.feature.render.highlight.AsyncChunkHighlightSupplier;
import xaeroplus.feature.render.highlight.DirectChunkHighlightSupplier;
import xaeroplus.feature.render.highlight.MultiColorHighlightColorFunction;
import xaeroplus.feature.render.line.LineSupplier;
import xaeroplus.feature.render.line.MultiColorLineColorFunction;
import xaeroplus.feature.render.line.MultiColorLineSupplier;
import xaeroplus.feature.render.text.TextSupplier;
import xaeroplus.util.FloatSupplier;

public interface DrawFeatureFactory {
    /**
     * Refreshed on MC render thread
     * Single color across all ellipses
     * Color function is called each frame
     */
    static DrawFeature ellipses(
            String id,
            EllipseSupplier ellipseSupplier,
            IntSupplier colorSupplier,
            FloatSupplier thicknessSupplier,
            int refreshIntervalMs) {
        return null;
    }

    /**
     * Refreshed on MC render thread
     * Color function per-ellipse
     * Color function is called only on refresh
     */
    static DrawFeature multiColorEllipses(
            String id,
            MultiColorEllipseSupplier ellipseSupplier,
            MultiColorEllipseColorFunction colorFunction,
            FloatSupplier thicknessSupplier,
            int refreshIntervalMs) {
        return null;
    }

    /**
     * Refreshed on MC render thread
     * Single color across all highlights
     * Color function is called each frame
     */
    static DrawFeature chunkHighlights(
            String id,
            DirectChunkHighlightSupplier chunkHighlightSupplier,
            IntSupplier colorSupplier,
            int refreshIntervalMs) {
        return null;
    }

    /**
     * Refreshed on MC render thread
     * Color function per-chunk
     * Color function is called only on refresh
     */
    static DrawFeature multiColorChunkHighlights(
            String id,
            DirectChunkHighlightSupplier chunkHighlightSupplier,
            MultiColorHighlightColorFunction colorFunction,
            int refreshIntervalMs) {
        return null;
    }

    /**
     * Refreshed async, not on the MC render thread
     * Single color across all highlights
     * Color function is called each frame
     */
    static DrawFeature asyncChunkHighlights(
            String id, AsyncChunkHighlightSupplier chunkHighlightSupplier, IntSupplier colorSupplier) {
        return null;
    }

    /**
     * Refreshed async, not on the MC render thread
     * Color function per-chunk
     * Color function is called only on refresh
     */
    static DrawFeature multiColorAsyncChunkHighlights(
            String id,
            AsyncChunkHighlightSupplier chunkHighlightSupplier,
            MultiColorHighlightColorFunction colorFunction) {
        return null;
    }

    /**
     * Refreshed on MC render thread
     * Single color across all lines
     * Color function is called each frame
     */
    static DrawFeature lines(
            String id,
            LineSupplier lineSupplier,
            IntSupplier colorSupplier,
            FloatSupplier lineWidthSupplier,
            int refreshIntervalMs) {
        return null;
    }

    /**
     * Refreshed on MC render thread
     * Color function per-line
     * Color function is called only on refresh
     */
    static DrawFeature multiColorLines(
            String id,
            MultiColorLineSupplier lineSupplier,
            MultiColorLineColorFunction colorFunction,
            FloatSupplier lineWidthSupplier,
            int refreshIntervalMs) {
        return null;
    }

    /**
     * Refreshed every frame on MC render thread
     */
    static DrawFeature text(String id, TextSupplier textSupplier) {
        return null;
    }

    /**
     * Refreshed async, not on the MC render thread
     */
    static DrawFeature asyncText(String id, TextSupplier textSupplier, int refreshIntervalMs) {
        return null;
    }
}
