package me.matl114.events.impl;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

@Data
@AllArgsConstructor
@Getter
@Accessors(fluent = true, chain = true)
public class UseItemOnBlock implements SequencedAction {
    @Setter
    BlockHitResult hitResult;

    @Setter
    ActionResult actionResult;

    Optional<BlockPos> placingBlockPos;

    final Hand hand;

    ItemStack handItem;
}
