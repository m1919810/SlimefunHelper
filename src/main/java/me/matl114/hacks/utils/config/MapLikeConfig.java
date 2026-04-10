package me.matl114.hacks.utils.config;

import java.util.Map;
import lombok.AllArgsConstructor;
import me.matl114.managers.config.NBTParsable;

@AllArgsConstructor
public abstract class MapLikeConfig<T, W> implements NBTParsable<MapLikeConfig<T, W>> {
    Map<T, W> map;
}
