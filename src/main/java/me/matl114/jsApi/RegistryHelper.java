package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.Objects;

@ApiMethod
public class RegistryHelper {
    private static MinecraftClient mc = MinecraftClient.getInstance();
    public static <T> Registry<T> getRegistry(String resourceKey){
        return (Registry<T>) mc.getNetworkHandler().getRegistryManager().getOptional( RegistryKey.ofRegistry(Identifier.tryParse(resourceKey))).orElseThrow();
    }

    public static <T> T getInRegistry(Registry<T> registry, String key){
        return registry.get(Identifier.tryParse(key));
    }

    public static <T> String getIdInRegistry(Registry<T> registry, T value){
        return Objects.requireNonNull(registry.getId(value)).toString();
    }


}
