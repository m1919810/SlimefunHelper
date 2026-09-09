package me.matl114.hacks.modules.move;

import java.util.ArrayList;
import java.util.List;
import lombok.With;
import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Tasks;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerInputManager extends BaseModule implements LegalMovementManager.MovementModifier {
    public static PlayerInputManager INSTANCE;
    public static LegalMovementManager.DelegateMovementModifier instance;

    private final List<TimedModifier> priorityQueue = new ArrayList<>(16);

    public PlayerInputManager() {
        super("PlayerInputManager");
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public void addInputModifier(Modifier modifier) {
        addInputModifier(modifier, 1);
    }

    public void addInputModifier(Modifier modifier, int ticks) {
        addInputModifier(modifier, 0, ticks);
    }

    public void addInputModifier(Modifier modifier, int startTicks, int ticks) {
        if (modifier == null || modifier.isEmpty() || ticks < 0) {
            return;
        }

        addTimedModifier(new TimedModifier(startTicks, ticks, modifier));
    }

    public void addForwardModifier(int priority, boolean forward, int ticks) {
        addForwardModifier(priority, forward, 0, ticks);
    }

    public void addForwardModifier(int priority, boolean forward, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).forward(forward), startTicks, ticks);
    }

    public void addBackwardModifier(int priority, boolean backward, int ticks) {
        addBackwardModifier(priority, backward, 0, ticks);
    }

    public void addBackwardModifier(int priority, boolean backward, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).backward(backward), startTicks, ticks);
    }

    public void addLeftModifier(int priority, boolean left, int ticks) {
        addLeftModifier(priority, left, 0, ticks);
    }

    public void addLeftModifier(int priority, boolean left, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).left(left), startTicks, ticks);
    }

    public void addRightModifier(int priority, boolean right, int ticks) {
        addRightModifier(priority, right, 0, ticks);
    }

    public void addRightModifier(int priority, boolean right, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).right(right), startTicks, ticks);
    }

    public void addJumpModifier(int priority, boolean jump, int ticks) {
        addJumpModifier(priority, jump, 0, ticks);
    }

    public void addJumpModifier(int priority, boolean jump, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).jump(jump), startTicks, ticks);
    }

    public void addSneakModifier(int priority, boolean sneak, int ticks) {
        addSneakModifier(priority, sneak, 0, ticks);
    }

    public void addSneakModifier(int priority, boolean sneak, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).sneak(sneak), startTicks, ticks);
    }

    public void addSprintModifier(int priority, boolean sprint, int ticks) {
        addSprintModifier(priority, sprint, 0, ticks);
    }

    public void addSprintModifier(int priority, boolean sprint, int startTicks, int ticks) {
        addInputModifier(Modifier.empty(priority).sprint(sprint), startTicks, ticks);
    }

    private void addTimedModifier(TimedModifier timedModifier) {
        int index = 0;
        while (index < priorityQueue.size() && priorityQueue.get(index).compareTo(timedModifier) <= 0) {
            index++;
        }
        priorityQueue.add(index, timedModifier);
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE + 1;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        LegalMovementManager.MovementModifier.super.applyPreTickModify(movementManagerEvent);
        if (priorityQueue.isEmpty() || mc.player == null) {
            return;
        }
        boolean rotModify = false;
        for (var re : priorityQueue) {
            boolean val = re.tickRotation(mc.player);
            rotModify |= val;
        }
        if (rotModify) {
            movementManagerEvent.context.markForResetRot();
        }
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (priorityQueue.isEmpty() || mc.player == null) {
            return;
        }
        PlayerInputUtils.Input input = PlayerInputUtils.of(mc.player);
        var iter = priorityQueue.iterator();
        while (iter.hasNext()) {
            var re = iter.next();
            if (re.tickInput(input)) {
                iter.remove();
            }
        }

        input.applyInput(mc.player);
    }

    @With
    public static record Modifier(
            int priority,
            @Nullable Boolean forward,
            @Nullable Boolean backward,
            @Nullable Boolean left,
            @Nullable Boolean right,
            @Nullable Boolean jump,
            @Nullable Boolean sneak,
            @Nullable Boolean sprint,
            @Nullable Float pitch,
            @Nullable Float yaw) {
        public static final Modifier EMPTY = new Modifier(0, null, null, null, null, null, null, null, null, null);

        public static Modifier empty(int priority) {
            return EMPTY.withPriority(priority);
        }

        public Modifier forward(boolean value) {
            return new Modifier(priority, value, backward, left, right, jump, sneak, sprint, pitch, yaw);
        }

        public Modifier backward(boolean value) {
            return new Modifier(priority, forward, value, left, right, jump, sneak, sprint, pitch, yaw);
        }

        public Modifier left(boolean value) {
            return new Modifier(priority, forward, backward, value, right, jump, sneak, sprint, pitch, yaw);
        }

        public Modifier right(boolean value) {
            return new Modifier(priority, forward, backward, left, value, jump, sneak, sprint, pitch, yaw);
        }

        public Modifier jump(boolean value) {
            return new Modifier(priority, forward, backward, left, right, value, sneak, sprint, pitch, yaw);
        }

        public Modifier sneak(boolean value) {
            return new Modifier(priority, forward, backward, left, right, jump, value, sprint, pitch, yaw);
        }

        public Modifier sprint(boolean value) {
            return new Modifier(priority, forward, backward, left, right, jump, sneak, value, pitch, yaw);
        }

        public Modifier pitch(float value) {
            return new Modifier(priority, forward, backward, left, right, jump, sneak, sprint, value, yaw);
        }

        public Modifier yaw(float value) {
            return new Modifier(priority, forward, backward, left, right, jump, sneak, sprint, pitch, value);
        }

        public void modifyInput(PlayerInputUtils.Input input) {
            if (forward != null) {
                input.forward(forward);
            }
            if (backward != null) {
                input.backward(backward);
            }
            if (left != null) {
                input.left(left);
            }
            if (right != null) {
                input.right(right);
            }
            if (jump != null) {
                input.jump(jump);
            }
            if (sneak != null) {
                input.sneak(sneak);
            }
            if (sprint != null) {
                input.sprint(sprint);
            }
        }

        public void modifyRotation(ClientPlayerEntity player) {
            if (player != null) {
                if (pitch != null) {
                    EntityUtils.setEntityPitchSafe(player, pitch);
                }
                if (yaw != null) {
                    EntityUtils.setEntityYawSafe(player, yaw);
                }
            }
        }

        public boolean hasRotation() {
            return pitch != null || yaw != null;
        }

        public boolean isEmpty() {
            return forward == null
                    && backward == null
                    && left == null
                    && right == null
                    && jump == null
                    && sneak == null
                    && sprint == null
                    && pitch == null
                    && yaw == null;
        }
    }

    public static class TimedModifier implements Comparable<TimedModifier> {
        private final int startTicks;
        private final int expireTicks;
        private final Modifier modifier;

        public TimedModifier(int lastTicks, Modifier modifier) {
            this(0, lastTicks, modifier);
        }

        public TimedModifier(int startTicks, int lastTicks, Modifier modifier) {
            this.startTicks = Tasks.getTick() + startTicks;
            this.expireTicks = Tasks.getTick() + startTicks + lastTicks;

            this.modifier = modifier;
        }

        public boolean tickInput(PlayerInputUtils.Input input) {
            if (isExpired()) {
                return true;
            }
            if (Tasks.getTick() >= startTicks) {
                modifier.modifyInput(input);
            }
            return false;
        }

        public boolean tickRotation(ClientPlayerEntity player) {
            if (isExpired()) {
                return false;
            }
            if (Tasks.getTick() >= startTicks) {
                if (modifier.hasRotation()) {
                    modifier.modifyRotation(player);
                    return true;
                }
            }
            return false;
        }

        public boolean isExpired() {
            return Tasks.getTick() > expireTicks;
        }

        @Override
        public int compareTo(@NotNull PlayerInputManager.TimedModifier timedModifier) {
            return Integer.compare(modifier.priority(), timedModifier.modifier.priority());
        }
    }
}
