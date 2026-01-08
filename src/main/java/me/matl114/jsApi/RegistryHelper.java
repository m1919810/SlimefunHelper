package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

@ApiMethod
public class RegistryHelper {
    private static MinecraftClient mc = MinecraftClient.getInstance();
    public static <T> Registry<T> getRegistry(String resourceKey){
        return mc.getNetworkHandler().getRegistryManager().get( RegistryKey.ofRegistry(Identifier.tryParse(resourceKey)));
    }

    public static <T> T getInRegistry(Registry<T> registry, String key){
        return registry.get(Identifier.tryParse(key));
    }
}
