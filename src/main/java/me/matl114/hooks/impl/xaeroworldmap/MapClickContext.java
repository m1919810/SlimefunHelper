package me.matl114.hooks.impl.xaeroworldmap;

import java.util.List;
import java.util.function.BiConsumer;
import me.matl114.hacks.utils.config.StringFormat;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MapClickContext {
    StringFormat formatter;
    BiConsumer<RegistryKey<World>, BlockPos> function;

    public MapClickContext(String format, BiConsumer<RegistryKey<World>, BlockPos> function) {
        this.formatter = new StringFormat(List.of("pos", "x", "y", "z"), format);
        this.function = function;
    }
}
