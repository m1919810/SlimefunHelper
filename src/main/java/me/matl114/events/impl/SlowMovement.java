package me.matl114.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
@Accessors(fluent = true)
@AllArgsConstructor
public class SlowMovement {
    Vec3d movementModifier;
    final BlockPos pos;
    final BlockState state;
}
