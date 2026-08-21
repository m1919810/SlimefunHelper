package me.matl114.events.impl;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

public record BlockUpdate(ClientWorld world, BlockPos pos, BlockState oldState, BlockState newState) {}
