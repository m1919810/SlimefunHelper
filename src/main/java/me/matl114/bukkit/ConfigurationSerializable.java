package me.matl114.bukkit;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

public interface ConfigurationSerializable {
    @NotNull
    Map<String, Object> serialize();
}
