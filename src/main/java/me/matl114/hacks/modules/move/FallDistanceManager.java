package me.matl114.hacks.modules.move;

import it.unimi.dsi.fastutil.doubles.DoubleArrayFIFOQueue;
import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.entity.LegalMovementManager;

public class FallDistanceManager extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public FallDistanceManager() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    DoubleArrayFIFOQueue heightQueue = new DoubleArrayFIFOQueue();

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return false;
    }
}
