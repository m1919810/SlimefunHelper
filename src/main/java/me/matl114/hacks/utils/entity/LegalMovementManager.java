package me.matl114.hacks.utils.entity;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.function.Supplier;
import lombok.Getter;
import me.matl114.events.Event;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class LegalMovementManager {
    final List<MovementModifier> hacks = new ArrayList<>();
    List<MovementModifier> currentTickEnableHacks = new ArrayList<>();
    public EntityMovementStatus<ClientPlayerEntity> playerStatus;
    public EntityMovementStatus<ClientPlayerEntity> playerPostHackStatus;
    public Deque<Pair<Float, Float>> importantRotationStatePreserve;

    @Getter
    boolean moveFix = false;

    @Getter
    boolean resetPos = false;

    @Getter
    boolean resetRot = false;

    public void markForMoveFix() {
        moveFix = true;
    }

    public void markForResetPos() {
        resetPos = true;
    }

    public void markForResetRot() {
        resetRot = true;
    }

    public boolean hasImportantRotation() {
        return importantRotationStatePreserve != null && !importantRotationStatePreserve.isEmpty();
    }

    public boolean hasImportantPitch() {
        if (hasImportantRotation()) {
            return importantRotationStatePreserve.stream().anyMatch(pair -> pair.getFirst() != null);
        }
        return false;
    }

    public boolean hasImportantYaw() {
        if (hasImportantRotation()) {
            return importantRotationStatePreserve.stream().anyMatch(pair -> pair.getSecond() != null);
        }
        return false;
    }

    public void pushImportantRotation(boolean hasPitch, boolean hasYaw) {
        if (!hasPitch && !hasYaw) {
            return;
        }
        if (importantRotationStatePreserve == null) {
            importantRotationStatePreserve = new ArrayDeque<>();
        }
        importantRotationStatePreserve.addLast(Pair.of(
                hasPitch ? playerStatus.entity.getPitch() : null, hasYaw ? playerStatus.entity.getYaw() : null));
    }
    // this should not be called
    protected void popImportantRotation(boolean apply) {
        if (importantRotationStatePreserve != null) {
            var entry = importantRotationStatePreserve.removeLast();
            if (apply && entry != null) {
                if (entry.getFirst() != null) {
                    EntityUtils.setEntityPitchSafe(playerStatus.entity, entry.getFirst());
                }
                if (entry.getSecond() != null) {
                    PlayerStateManager.setPlayerYawSafe(playerStatus.entity, entry.getSecond());
                }
            }
        }
    }

    public void addMovementModifier(MovementModifier movementModifier) {
        int p = movementModifier.priority();
        int index = 0;
        while (index < hacks.size() && hacks.get(index).priority() <= p) {
            index++;
        }

        hacks.add(index, movementModifier);
    }

    public boolean yawModified() {
        ClientPlayerEntity player = playerStatus.entity;

        // 获取安全的角度（处理NaN等异常情况）
        float currentYaw = EntityUtils.getSafeYaw(player.getYaw(), playerStatus.yaw);
        float previousYaw = EntityUtils.getSafeYaw(player.getYaw(), player.getYaw());

        // 计算两个角度之间的最小差值（处理360度环绕）
        float diff = Math.abs(currentYaw - previousYaw);
        float wrappedDiff = Math.min(diff, 360.0f - diff);

        // 设置一个合理的阈值（例如5度）
        return wrappedDiff > 2.0f;
    }

    public boolean pitchModified() {
        return Math.abs(EntityUtils.getSafePitch(playerStatus.pitch)
                        - EntityUtils.getSafePitch(playerStatus.entity.getPitch()))
                > 2.0F;
    }

    public void preProgress(ClientPlayerEntity args) {
        this.playerStatus = new EntityMovementStatus<>(args);
        this.currentTickEnableHacks = new ArrayList<>();
        this.importantRotationStatePreserve = null;
        this.resetPos = false;
        this.resetRot = false;
        this.moveFix = false;
        // start new tick, removing contents and replace with new
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, false, false);
        for (var hack : hacks) {
            if (hack.shouldApply(movementManagerEvent)) {
                this.currentTickEnableHacks.add(hack);
            }
        }
        this.playerPostHackStatus = new EntityMovementStatus<>(args);
    }

    public void postInputTick(ClientPlayerEntity player) {
        if (this.playerStatus == null) {
            // illegal status
            return;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, false, false);
        for (var hack : this.currentTickEnableHacks) {
            hack.applyAfterInputTick(movementManagerEvent);
        }
        if (moveFix) {
            var input = PlayerInputUtils.of(player);
            input = PlayerInputUtils.tryCorrectMovementInput(
                    input, movementManagerEvent.context.playerStatus.yaw, player.getYaw());
            // one cannot sprint if forward is not pressed
            if (input.forwardSpeed() < 1E-2 && (input.sprint() || player.isSprinting())) {
                input.sprint(false);
                player.setSprinting(false);
            }
            input.applyInput(player);
        }
    }

    public boolean preTravelTick(ClientPlayerEntity args, Event<Vec3d> movementInput) {
        if (this.playerStatus == null) {
            return true;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        for (var hack : this.currentTickEnableHacks) {
            hack.applyBeforeTravelTick(movementManagerEvent, movementInput);
        }
        return !movementManagerEvent.isCancelled();
    }

    public void postTravelTick(ClientPlayerEntity args, Event<Vec3d> movementInput) {
        if (this.playerStatus == null) {
            return;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, false, false);
        for (var hack : this.currentTickEnableHacks) {
            hack.applyAfterTravelTick(movementManagerEvent, movementInput);
        }
    }

    // invoke before the boat packets
    public boolean preInputProgress(ClientPlayerEntity args) {
        if (this.playerStatus == null) {
            // illegal status
            return true;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        for (var hack : this.currentTickEnableHacks) {
            hack.applyBeforeInputPacketModify(movementManagerEvent);
        }
        return !movementManagerEvent.isCancelled();
    }
    // invoke before the movement packets
    public boolean preMovementProgress(ClientPlayerEntity args) {
        if (this.playerStatus == null) {
            // illegal status
            return true;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        for (var hack : this.currentTickEnableHacks) {
            hack.applyBeforeMovementPacketModify(movementManagerEvent);
        }
        return !movementManagerEvent.isCancelled();
    }
    // after player tick
    public void postProgress(ClientPlayerEntity args) {
        if (this.playerStatus == null) {
            // illegal status
            return;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        // in case someone add a task during a destroying postModify
        Iterator<MovementModifier> iter = new ArrayList<>(this.hacks).iterator();
        int goingCurrentTickEnables = 0;
        while (iter.hasNext()) {
            var hack = iter.next();
            // in same seq, so match one by one
            boolean enabled = goingCurrentTickEnables < this.currentTickEnableHacks.size()
                    && this.currentTickEnableHacks.get(goingCurrentTickEnables) == hack;
            if (enabled) {
                goingCurrentTickEnables += 1;
            }
            if (!hack.postModify(movementManagerEvent, enabled)) {
                iter.remove();
                this.hacks.remove(hack);
            }
        }
        if (resetPos) {
            playerStatus.restorePos();
        }
        if (resetRot) {
            playerStatus.restoreRotation();
        }
        return;
    }

    // functions:
    public void tryMarkForMoveFix() {
        if (yawModified()) {
            markForMoveFix();
        }
    }

    public static interface MovementModifier extends Comparable<MovementModifier> {
        // tasks, attacks
        public static int PRIORITY_LOW = -100000;
        // movement hacks
        public static int PRIORITY_COMMON = 0;
        public static int PRIORITY_HIGH = 100000;
        public static int PRIORITY_HIGHEST = 10000000;
        public static int PRIORITY_MONITOR = Integer.MAX_VALUE - 1;

        default int priority() {
            return 0;
        }

        // return if available for current tick modify
        default boolean shouldApply(Event<LegalMovementManager> movementManagerEvent) {
            // two modifying hacks may clash with each other, so we
            preTick(movementManagerEvent);
            LegalMovementManager movementManager = movementManagerEvent.context();
            if (false) {
                return false;
            }
            applyPreTickModify(movementManagerEvent);
            return true;
        }

        default void preTick(Event<LegalMovementManager> movementManagerEvent) {}

        default void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

        default void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {}

        default void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {}

        default void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {}

        default void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

        default void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

        // return for removal after player tick
        // return if this hack is still valid, if return false, we will remove it from hack list
        // tick both
        //
        default boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            return true;
        }

        default int compareTo(MovementModifier var1) {
            return this.priority() - var1.priority();
        }
    }

    public static class DelegateMovementModifier implements MovementModifier {
        public Supplier<MovementModifier> getDelegate() {
            return delegate;
        }

        public void setDelegate(Supplier<MovementModifier> movementModifierSupplier) {
            this.delegate = movementModifierSupplier;
        }

        public Supplier<MovementModifier> delegate;

        public DelegateMovementModifier(Supplier<MovementModifier> delegate) {
            this.delegate = delegate;
        }

        @Override
        public int priority() {
            return delegate.get().priority();
        }

        @Override
        public boolean shouldApply(Event<LegalMovementManager> movementManagerEvent) {
            return delegate.get().shouldApply(movementManagerEvent);
        }

        @Override
        public void preTick(Event<LegalMovementManager> movementManagerEvent) {
            delegate.get().preTick(movementManagerEvent);
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            delegate.get().applyPreTickModify(movementManagerEvent);
        }

        @Override
        public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
            delegate.get().applyAfterInputTick(movementManagerEvent);
        }

        public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
            delegate.get().applyBeforeTravelTick(movementManagerEvent, moveEvent);
        }

        public void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
            delegate.get().applyAfterTravelTick(movementManagerEvent, moveEvent);
        }

        @Override
        public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            delegate.get().applyBeforeInputPacketModify(movementManagerEvent);
        }

        @Override
        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            delegate.get().applyBeforeMovementPacketModify(movementManagerEvent);
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            return delegate.get().postModify(movementManagerEvent, enabledThisTick);
        }

        @Override
        public int compareTo(MovementModifier var1) {
            return delegate.get().compareTo(var1);
        }
    }

    public static class ModifierPipeline implements MovementModifier {

        public ModifierPipeline(int p) {
            this.priority = p;
        }

        private ClientPlayerEntity current;
        private final int priority;
        private final List<Supplier<MovementModifier>> factories = new ArrayList<>();
        private final List<MovementModifier> pipeline = new ArrayList<>();

        public void resetForNewPlayer(ClientPlayerEntity currentEntity) {
            pipeline.clear();
            current = currentEntity;
            for (var factory : factories) {
                MovementModifier movementModifier = factory.get();
                addPipelineInternal(movementModifier);
            }
        }

        private void addPipelineInternal(MovementModifier movementModifier) {
            if (movementModifier == null || current == null) {
                return;
            }
            int p = movementModifier.priority();
            int index = 0;
            while (index < pipeline.size() && pipeline.get(index).priority() <= p) {
                index++;
            }

            pipeline.add(index, movementModifier);
        }
        // NOTE: we should assume that pipeline modifiers do not conflict with each other
        // NOTE: the pipeline's checkConflict will NOT be INVOKED
        // NOTE: the modifers in pipeline will not be removed
        // NOTE: the pipeline will not be removed
        public void addMovementModifierFactory(Supplier<MovementModifier> movementModifier) {
            factories.add(movementModifier);
            // be safe to add, because it will be
            addPipelineInternal(movementModifier.get());
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            for (var re : pipeline) {
                re.applyPreTickModify(movementManagerEvent);
            }
        }

        @Override
        public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
            for (var re : pipeline) {
                re.applyAfterInputTick(movementManagerEvent);
            }
        }

        public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
            for (var re : pipeline) {
                re.applyBeforeTravelTick(movementManagerEvent, moveEvent);
            }
        }

        public void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
            for (var re : pipeline) {
                re.applyAfterTravelTick(movementManagerEvent, moveEvent);
            }
        }

        @Override
        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            for (var re : pipeline) {
                re.applyBeforeMovementPacketModify(movementManagerEvent);
            }
        }

        @Override
        public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            for (var re : pipeline) {
                re.applyBeforeInputPacketModify(movementManagerEvent);
            }
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            for (var re : pipeline) {
                re.postModify(movementManagerEvent, enabledThisTick);
            }
            return true;
        }
    }
}
