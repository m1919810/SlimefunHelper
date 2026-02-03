package me.matl114.utils.commands;

import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

public interface TabProvider {
    public List<String> getTab(PlayerEntity player);
}
