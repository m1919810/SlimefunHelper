package me.matl114.hacks.utils.interact;

import net.minecraft.item.ItemStack;

/** Performs one interaction after the queue has checked and selected its item. */
@FunctionalInterface
public interface InteractRequest {
    TaskAction tickTask(InteractionQueue queue, boolean itemMatched);

    default Double itemScore(InteractionQueue queue, ItemStack itemStack) {
        return 1.0;
    }

    default void successCallback(InteractionQueue queue) {}
}
