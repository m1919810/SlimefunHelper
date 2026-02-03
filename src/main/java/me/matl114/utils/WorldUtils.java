package me.matl114.utils;

import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class WorldUtils {
    public static boolean areWorldEquals(ClientWorld world1, ClientWorld world2){
        return world1 == world2 || (world1 != null && world2 != null && Objects.equals(world1.getRegistryKey().getValue() , world2.getRegistryKey().getValue()));
    }


}
