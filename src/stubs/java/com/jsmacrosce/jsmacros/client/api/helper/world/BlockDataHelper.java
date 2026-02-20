package com.jsmacrosce.jsmacros.client.api.helper.world;

import com.jsmacrosce.jsmacros.core.helpers.BaseHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class BlockDataHelper extends BaseHelper<BlockState> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public BlockDataHelper(BlockState b, BlockEntity e, BlockPos bp) {
        super(b);
    }
}
