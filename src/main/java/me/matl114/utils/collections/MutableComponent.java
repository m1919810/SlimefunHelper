package me.matl114.utils.collections;

import lombok.AllArgsConstructor;
import net.minecraft.component.ComponentType;

import java.util.Optional;

@AllArgsConstructor
public class MutableComponent<T> {

    public ComponentType<T> type;
    public Optional<T> value;
}
