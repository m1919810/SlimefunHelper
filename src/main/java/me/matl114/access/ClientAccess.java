package me.matl114.access;

import net.minecraft.client.MinecraftClient;

public interface ClientAccess {
    static ClientAccess of(MinecraftClient client){
        return (ClientAccess) client;
    }
    public ClientAccess clone();

    public void setCooldown(int cooldown);

    public int getCooldown();
}
