package fi.dy.masa.malilib.util;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.BlockPos;

public class LayerRange {
    public static Codec<LayerRange> CODEC = null;

    public boolean isPositionWithinRange(BlockPos pos) {
        return false;
    }

    public boolean isPositionWithinRange(int x, int y, int z) {
        return false;
    }
}
