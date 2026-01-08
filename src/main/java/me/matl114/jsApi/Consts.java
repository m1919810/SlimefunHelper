package me.matl114.jsApi;

import lombok.Getter;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * this provides the common consts which may be used in js Scripts
 * classes will be obfuscated when at runtime,
 * but invocation or newInstance is ok
 */
@ApiMethod
public interface Consts {
    Class<?> Vec3d = net.minecraft.util.math.Vec3d.class;
    Class<?> BlockPos = net.minecraft.util.math.BlockPos.class;
    Class<?> Player = PlayerEntity.class;
    Class<?> Client = MinecraftClient.class;
    Class<?> Entity = net.minecraft.entity.Entity.class;
    Class<?> World = net.minecraft.world.World.class;
    Class<?> ItemStack = net.minecraft.item.ItemStack.class;
    Class<?> Direction = net.minecraft.util.math.Direction.class;
    net.minecraft.util.math.BlockPos BlockPos_ZERO = net.minecraft.util.math.BlockPos.ORIGIN;
    net.minecraft.util.math.Vec3d Vec3d_ZERO = net.minecraft.util.math.Vec3d.ZERO;
    MinecraftClient MC =MinecraftClient.getInstance();

}
