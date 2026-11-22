package me.matl114.hacks.utils.interact;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.managers.Tasks;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

public final class EntityInteractionTracker {
    private static final int RESPONSE_TIMEOUT_TICKS = 40;

    private final Map<Entity, InteractionEntry> interactions = new WeakHashMap<>();

    public void postInteract(Entity entity) {
        if (!EntityUtils.isEntityValid(entity)) return;

        SequencedActionManager actionManager = SequencedActionManager.INSTANCE;
        IndexEntry<?> previousItemUse = actionManager == null ? null : actionManager.lastItemUse;
        interactions.remove(entity);

        InteractionTasks.interactItem(Hand.OFF_HAND, true, false);

        if (actionManager != null) {
            IndexEntry<?> currentItemUse = actionManager.lastItemUse;
            if (currentItemUse != null && currentItemUse != previousItemUse) {
                interactions.put(entity, new InteractionEntry(currentItemUse.index(), Tasks.getTick()));
            }
        }
    }

    public boolean isWaitingResponse(Entity entity) {
        discardCompletedInteractions();
        return entity != null && interactions.containsKey(entity);
    }

    public boolean isWaitingResponse(Predicate<Entity> entityPredicate) {
        discardCompletedInteractions();
        return interactions.keySet().stream().anyMatch(entity -> entity != null && entityPredicate.test(entity));
    }

    public boolean isWaitingResponse() {
        discardCompletedInteractions();
        return !interactions.isEmpty();
    }

    private void discardCompletedInteractions() {
        SequencedActionManager actionManager = SequencedActionManager.INSTANCE;
        int currentTick = Tasks.getTick();
        Iterator<Map.Entry<Entity, InteractionEntry>> iterator =
                interactions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, InteractionEntry> entry = iterator.next();
            Entity entity = entry.getKey();
            InteractionEntry interaction = entry.getValue();
            if (entity == null
                    || currentTick - interaction.postedTick() >= RESPONSE_TIMEOUT_TICKS
                    || !EntityUtils.isEntityValid(entity)
                    || actionManager == null
                    || !actionManager.isWaitingItemUseResponse(interaction.sequenceId())) {
                iterator.remove();
            }
        }
    }

    private record InteractionEntry(int sequenceId, int postedTick) {}
}
