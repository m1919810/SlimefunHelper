package me.matl114.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

@Accessors(fluent = true)
@Setter
@Getter
@AllArgsConstructor
public class BlockBreak {
    BlockPos blockPos;
    Stage stage;
    ItemStack stack;
    float progress;

    public enum Stage {
        PRE,
        POST;
    }
}
