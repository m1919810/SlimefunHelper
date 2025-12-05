package me.matl114.jsApi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/**
 * this provides the common consts which may be used in js Scripts
 * classes will be obfuscated when at runtime,
 * but invocation or newInstance is ok
 */
public interface Consts {
    Class<?> Vec3d = net.minecraft.util.math.Vec3d.class;
    Class<?> BlockPos = net.minecraft.util.math.BlockPos.class;
    Class<?> Player = PlayerEntity.class;
    Class<?> Client = MinecraftClient.class;
    MinecraftClient MC =MinecraftClient.getInstance();

}
