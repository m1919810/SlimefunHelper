package me.matl114.utils.structures;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EndCityTemplateData {
    private static final EndCityTemplateData VANILLA = new EndCityTemplateData(Map.of(
            "fat_tower_top", List.of(
                    new EndCityMarker("fat_tower_top", EndCityMarker.Type.CHEST, 3, 2, 11),
                    new EndCityMarker("fat_tower_top", EndCityMarker.Type.CHEST, 5, 2, 13)),
            "third_floor_2", List.of(
                    new EndCityMarker("third_floor_2", EndCityMarker.Type.CHEST, 6, 6, 2)),
            "ship", List.of(
                    new EndCityMarker("ship", EndCityMarker.Type.CHEST, 5, 5, 7),
                    new EndCityMarker("ship", EndCityMarker.Type.ELYTRA, 6, 5, 7),
                    new EndCityMarker("ship", EndCityMarker.Type.CHEST, 7, 5, 7))));

    private final Map<String, List<EndCityMarker>> markers;

    private EndCityTemplateData(Map<String, List<EndCityMarker>> markers) {
        this.markers = markers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }

    public static EndCityTemplateData vanilla() {
        return VANILLA;
    }

    public static EndCityTemplateData of(Map<String, List<EndCityMarker>> markers) {
        Objects.requireNonNull(markers);
        return new EndCityTemplateData(markers);
    }

    public List<EndCityMarker> markers(String template) {
        return markers.getOrDefault(template, List.of());
    }
}
