package me.matl114.utils.collections;

import java.util.Optional;
import lombok.AllArgsConstructor;
import net.minecraft.component.ComponentType;

@AllArgsConstructor
public class MutableComponent<T> {

    public ComponentType<T> type;
    public Optional<T> value;
}
