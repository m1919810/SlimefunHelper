package me.matl114.accessors.access;

import net.minecraft.client.MinecraftClient;

public interface ClientAccess {
    static ClientAccess of(MinecraftClient client) {
        return (ClientAccess) client;
    }

    public ClientAccess clone();

    public void setItemUseCooldown(int cooldown);

    public void setAttackCooldown(int cooldown);

    public int getAttackCooldown();

    public int getItemUseCooldown();
}
