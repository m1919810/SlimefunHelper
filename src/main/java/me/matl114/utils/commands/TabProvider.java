package me.matl114.utils.commands;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.entity.player.PlayerEntity;

public interface TabProvider {
    public Stream<String> getTab(PlayerEntity sender, List<InputArgument> args);
}
