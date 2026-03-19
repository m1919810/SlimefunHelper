package me.matl114.utils.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import me.matl114.events.Event;
import me.matl114.utils.EntityUtils;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;

public class LegalMovementManager implements ProgressWrapper<ClientPlayerEntity> {
    final List<MovementModifier> hacks = new ArrayList<>();
    List<MovementModifier> currentTickEnableHacks = new ArrayList<>();
    public EntityMovementStatus<ClientPlayerEntity> playerStatus;
    public EntityMovementStatus<ClientPlayerEntity> playerPostHackStatus;

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
        float currentYaw = EntityUtils.getSafeYaw(player, playerStatus.yaw);
        float previousYaw = EntityUtils.getSafeYaw(player, playerPostHackStatus.yaw);

        // 计算两个角度之间的最小差值（处理360度环绕）
        float diff = Math.abs(currentYaw - previousYaw);
        float wrappedDiff = Math.min(diff, 360.0f - diff);

        // 设置一个合理的阈值（例如5度）
        return wrappedDiff > 2.0f;
    }

    public boolean pitchModified() {
        return Math.abs(EntityUtils.getSafePitch(playerStatus.pitch)
                        - EntityUtils.getSafePitch(playerPostHackStatus.pitch))
                > 2.0F;
    }

    @Override
    public void preProgress(ClientPlayerEntity args) {
        this.playerStatus = new EntityMovementStatus<>(args);
        this.currentTickEnableHacks = new ArrayList<>();
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
    @Override
    public void postProgress(ClientPlayerEntity args) {
        if (this.playerStatus == null) {
            // illegal status
            return;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        Iterator<MovementModifier> iter = this.hacks.iterator();
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
            }
        }
        return;
    }

    @Override
    public final boolean stillWrap(ClientPlayerEntity args) {
        return true;
    }

    // functions:
    public void tryCorrectMovementInput() {

        if (yawModified()) {
            ClientPlayerEntity player = playerStatus.entity;
            float originYaw = playerStatus.yaw;
            // rotated
            float diff = EntityUtils.getSafeYawDiff(originYaw, player.getYaw());
            Input input = player.input;
            int forwardSpeed;
            int sidewaySpeed;
            boolean w, a, s, d;
            int movementForward = (input.playerInput.forward() ? 1 : 0) + (input.playerInput.backward() ? -1 : 0);
            int movementSideways = (input.playerInput.left() ? 1 : 0) + (input.playerInput.right() ? -1 : 0);
            if (diff < 22.5 && diff >= -22.5) {
                // do nothing
                return;
            } else if (diff < 67.5 && diff >= 22.5) {
                // turn to
                forwardSpeed = (movementForward - movementSideways);
                sidewaySpeed = (movementForward + movementSideways);
            } else if (diff >= 67.5 && diff < 90.0F + 22.5F) {
                forwardSpeed = -movementSideways;
                sidewaySpeed = movementForward;
            } else if (diff >= 90.0F + 22.5F && diff < 90.0F + 67.5F) {
                forwardSpeed = (-movementForward - movementSideways);
                sidewaySpeed = (movementForward - movementSideways);
            } else if (diff >= 90.0F + 67.5F || diff < -90.0F - 67.5F) {
                forwardSpeed = -movementForward;
                sidewaySpeed = -movementSideways;
            } else if (diff >= -90.0F - 67.5F && diff < -90.0F - 22.5F) {
                forwardSpeed = (-movementForward + movementSideways);
                sidewaySpeed = (-movementForward - movementSideways);
            } else if (diff >= -90.0F - 22.5F && diff < -90.0F + 22.5F) {
                forwardSpeed = movementSideways;
                sidewaySpeed = -movementForward;
            } else if (diff >= -90.0F + 22.5F && diff < -22.5F) {
                forwardSpeed = (movementForward + movementSideways);
                sidewaySpeed = (-movementForward + movementSideways);
            } else {
                return;
            }
            // sync values
            input.playerInput = new PlayerInput(
                    forwardSpeed > 0,
                    forwardSpeed < 0,
                    sidewaySpeed > 0,
                    sidewaySpeed < 0,
                    input.playerInput.jump(),
                    input.playerInput.sneak(),
                    input.playerInput.sprint());
        }
    }

    public static interface MovementModifier extends Comparable<MovementModifier> {
        default int priority() {
            return 0;
        }

        default boolean mayModify() {
            return mayModifyPos() || mayModifyRotation();
        }

        default boolean mayModifyPos() {
            return false;
        }

        default boolean mayModifyRotation() {
            return false;
        }

        default boolean conflictCheck(LegalMovementManager movementManager) {
            //            if (mayModifyPos()
            //                    &&
            // movementManager.currentTickEnableHacks.stream().anyMatch(MovementModifier::mayModifyPos)) {
            //                return false;
            //            }
            //            if (mayModifyRotation()
            //                    &&
            // movementManager.currentTickEnableHacks.stream().anyMatch(MovementModifier::mayModifyRotation)) {
            //                return false;
            //            }
            return true;
        }

        // return if available for current tick modify
        default boolean shouldApply(Event<LegalMovementManager> movementManagerEvent) {
            // two modifying hacks may clash with each other, so we
            preTick(movementManagerEvent);
            LegalMovementManager movementManager = movementManagerEvent.context();
            if (!conflictCheck(movementManager)) {
                return false;
            }
            applyPreTickModify(movementManagerEvent);
            return true;
        }

        default void preTick(Event<LegalMovementManager> movementManagerEvent) {}

        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent);

        default void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {}

        default void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {}

        default void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {}

        default void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

        default void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

        // return for removal after player tick
        // return if this hack is still valid, if return false, we will remove it from hack list
        // tick both
        //
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick);

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
        public boolean mayModify() {
            return delegate.get().mayModify();
        }

        @Override
        public boolean mayModifyPos() {
            return delegate.get().mayModifyPos();
        }

        @Override
        public boolean mayModifyRotation() {
            return delegate.get().mayModifyRotation();
        }

        @Override
        public boolean conflictCheck(LegalMovementManager movementManager) {
            return delegate.get().conflictCheck(movementManager);
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
        public boolean mayModifyPos() {
            for (var pi : pipeline) {
                if (pi.mayModifyPos()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mayModifyRotation() {
            for (var pi : pipeline) {
                if (pi.mayModifyRotation()) {
                    return true;
                }
            }
            return false;
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
