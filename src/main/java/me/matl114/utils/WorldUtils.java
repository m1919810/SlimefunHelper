package me.matl114.utils;

import java.util.Objects;
import net.minecraft.client.world.ClientWorld;

public class WorldUtils {
    public static boolean areWorldEquals(ClientWorld world1, ClientWorld world2) {
        return world1 == world2
                || (world1 != null
                        && world2 != null
                        && Objects.equals(
                                world1.getRegistryKey().getValue(),
                                world2.getRegistryKey().getValue()));
    }
}
