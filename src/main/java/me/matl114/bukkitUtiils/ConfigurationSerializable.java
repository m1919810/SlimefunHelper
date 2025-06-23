package me.matl114.bukkitUtiils;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface ConfigurationSerializable {
    @NotNull
    Map<String, Object> serialize();
}
