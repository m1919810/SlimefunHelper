package me.matl114.hooks.impl.xaeroplus.impl;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import me.matl114.hooks.impl.xaeroplus.IMapDrawFactory;
import me.matl114.hooks.impl.xaeroplus.IMapDrawFeature;
import me.matl114.hooks.impl.xaeroplus.wrapper.*;
import xaeroplus.Globals;
import xaeroplus.feature.render.DrawFeatureFactory;
import xaeroplus.feature.render.ellipse.Ellipse;
import xaeroplus.feature.render.line.Line;
import xaeroplus.feature.render.text.Text;

public class MapDrawFactoryImpl implements IMapDrawFactory {
    public static final MapDrawFactoryImpl INSTANCE = new MapDrawFactoryImpl();

    @Override
    public IMapDrawFeature ellipses(
            String id,
            ElementSupplier<List<EllipseWrapper<?>>> ellipseSupplier,
            IntSupplier colorSupplier,
            Supplier<Float> thicknessSupplier,
            int refreshIntervalMs) {
        return new MapDrawFeatureImpl(DrawFeatureFactory.ellipses(
                id,
                ((windowRegionX, windowRegionZ, windowRegionSize, dimension) -> {
                    List<EllipseWrapper<?>> wrapperList =
                            ellipseSupplier.supplyElement(windowRegionX, windowRegionZ, windowRegionSize, dimension);
                    return wrapperList.stream()
                            .map(wrapper -> wrapper.<Ellipse, EllipseWrapper<Ellipse>>inject(
                                    s -> new Ellipse(s.centerX, s.centerZ, s.radiusX, s.radiusZ)))
                            .map(ElementWrapper::getElement)
                            .toList();
                }),
                colorSupplier,
                thicknessSupplier::get,
                refreshIntervalMs));
    }

    @Override
    public IMapDrawFeature chunkHighlights(
            String id,
            ElementSupplier<Long2LongMap> chunkHighlightSupplier,
            IntSupplier colorSupplier,
            int refreshIntervalMs) {
        return new MapDrawFeatureImpl(DrawFeatureFactory.chunkHighlights(
                id, (key) -> chunkHighlightSupplier.supplyElement(0, 0, 0, key), colorSupplier, refreshIntervalMs));
    }

    @Override
    public IMapDrawFeature asyncChunkHighlights(
            String id, ElementSupplier<Long2LongMap> chunkHighlightSupplier, IntSupplier colorSupplier) {
        return new MapDrawFeatureImpl(
                DrawFeatureFactory.asyncChunkHighlights(id, chunkHighlightSupplier::supplyElement, colorSupplier));
    }

    @Override
    public IMapDrawFeature lines(
            String id,
            ElementSupplier<List<LineWrapper<?>>> lineSupplier,
            IntSupplier colorSupplier,
            Supplier<Float> lineWidthSupplier,
            int refreshIntervalMs) {
        return new MapDrawFeatureImpl(DrawFeatureFactory.lines(
                id,
                ((windowRegionX, windowRegionZ, windowRegionSize, dimension) -> {
                    return lineSupplier
                            .supplyElement(windowRegionX, windowRegionZ, windowRegionSize, dimension)
                            .stream()
                            .map(wrapper ->
                                    wrapper.<Line, LineWrapper<Line>>inject(s -> new Line(s.x1, s.z1, s.x2, s.z2)))
                            .map(ElementWrapper::getElement)
                            .toList();
                }),
                colorSupplier,
                lineWidthSupplier::get,
                refreshIntervalMs));
    }

    @Override
    public IMapDrawFeature text(String id, ElementSupplier<Long2ObjectMap<TextWrapper<?>>> textSupplier) {
        return new MapDrawFeatureImpl(
                DrawFeatureFactory.text(id, ((windowRegionX, windowRegionZ, windowRegionSize, dimension) -> {
                    var long2ObjectMap =
                            textSupplier.supplyElement(windowRegionX, windowRegionZ, windowRegionSize, dimension);
                    Long2ObjectMap<Text> map = new Long2ObjectOpenHashMap<>();
                    for (var key : long2ObjectMap.long2ObjectEntrySet()) {
                        map.put(
                                key.getLongKey(),
                                key.getValue()
                                        .<Text, TextWrapper<Text>>inject(
                                                s -> new Text(s.value, s.x, s.z, s.color, s.scale))
                                        .getElement());
                    }
                    return map;
                })));
    }

    @Override
    public IMapDrawFeature asyncText(
            String id, ElementSupplier<Long2ObjectMap<TextWrapper<?>>> textSupplier, int refreshIntervalMs) {
        return new MapDrawFeatureImpl(DrawFeatureFactory.asyncText(
                id,
                ((windowRegionX, windowRegionZ, windowRegionSize, dimension) -> {
                    var long2ObjectMap =
                            textSupplier.supplyElement(windowRegionX, windowRegionZ, windowRegionSize, dimension);
                    Long2ObjectMap<Text> map = new Long2ObjectOpenHashMap<>();
                    for (var key : long2ObjectMap.long2ObjectEntrySet()) {
                        map.put(
                                key.getLongKey(),
                                key.getValue()
                                        .<Text, TextWrapper<Text>>inject(
                                                s -> new Text(s.value, s.x, s.z, s.color, s.scale))
                                        .getElement());
                    }
                    return map;
                }),
                refreshIntervalMs));
    }

    @Override
    public void unregisterId(String id) {
        Globals.drawManager.registry().unregister(id);
    }
}
