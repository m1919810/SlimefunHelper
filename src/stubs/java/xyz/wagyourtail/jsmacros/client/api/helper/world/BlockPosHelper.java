package xyz.wagyourtail.jsmacros.client.api.helper.world;

import net.minecraft.util.math.BlockPos;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;

public class BlockPosHelper extends BaseHelper<BlockPos> {
    public BlockPosHelper(BlockPos b) {
        super(b);
    }

    public BlockPosHelper(int x, int y, int z) {
        super(new BlockPos(x, y, z));
    }

    public int getX() {
        return base.getX();
    }

    /**
     * @return the {@code y} value of the block.
     * @since 1.2.6
     */
    public int getY() {
        return base.getY();
    }

    /**
     * @return the {@code z} value of the block.
     * @since 1.2.6
     */
    public int getZ() {
        return base.getZ();
    }
}
