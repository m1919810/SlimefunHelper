package me.matl114.utils.commands;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;

public interface TabProvider {
    public List<String> getTab(PlayerEntity player);
}
