package me.matl114.hacks.utils.interact;

import me.matl114.utils.collections.FlagEntry;
import net.minecraft.util.hit.HitResult;

/** Result of running one interaction task. */
public record TaskAction(Action action, FlagEntry<? extends HitResult> hitResult) {
    public enum Action {
        SUCCESS,
        PAUSE,
        DROP,
        TERMINATE
    }
}
