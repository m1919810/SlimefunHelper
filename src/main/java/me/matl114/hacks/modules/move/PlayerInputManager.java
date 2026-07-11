package me.matl114.hacks.modules.move;

import java.util.ArrayList;
import java.util.List;
import lombok.With;
import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerInputManager extends BaseModule implements LegalMovementManager.MovementModifier {
    public static PlayerInputManager INSTANCE;
    public static LegalMovementManager.DelegateMovementModifier instance;

    private final List<TimedInputModifier> priorityQueue = new ArrayList<>(16);

    public PlayerInputManager() {
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public void addInputModifier(InputModifier modifier) {
        addInputModifier(modifier, 1);
    }

    public void addInputModifier(InputModifier modifier, int ticks) {
        addInputModifier(modifier, 0, ticks);
    }

    public void addInputModifier(InputModifier modifier, int startTicks, int ticks) {
        if (modifier == null || modifier.isEmpty() || ticks < 0) {
            return;
        }

        addTimedModifier(new TimedInputModifier(startTicks, ticks, modifier));
    }

    public void addForwardModifier(int priority, boolean forward, int ticks) {
        addForwardModifier(priority, forward, 0, ticks);
    }

    public void addForwardModifier(int priority, boolean forward, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).forward(forward), startTicks, ticks);
    }

    public void addBackwardModifier(int priority, boolean backward, int ticks) {
        addBackwardModifier(priority, backward, 0, ticks);
    }

    public void addBackwardModifier(int priority, boolean backward, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).backward(backward), startTicks, ticks);
    }

    public void addLeftModifier(int priority, boolean left, int ticks) {
        addLeftModifier(priority, left, 0, ticks);
    }

    public void addLeftModifier(int priority, boolean left, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).left(left), startTicks, ticks);
    }

    public void addRightModifier(int priority, boolean right, int ticks) {
        addRightModifier(priority, right, 0, ticks);
    }

    public void addRightModifier(int priority, boolean right, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).right(right), startTicks, ticks);
    }

    public void addJumpModifier(int priority, boolean jump, int ticks) {
        addJumpModifier(priority, jump, 0, ticks);
    }

    public void addJumpModifier(int priority, boolean jump, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).jump(jump), startTicks, ticks);
    }

    public void addSneakModifier(int priority, boolean sneak, int ticks) {
        addSneakModifier(priority, sneak, 0, ticks);
    }

    public void addSneakModifier(int priority, boolean sneak, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).sneak(sneak), startTicks, ticks);
    }

    public void addSprintModifier(int priority, boolean sprint, int ticks) {
        addSprintModifier(priority, sprint, 0, ticks);
    }

    public void addSprintModifier(int priority, boolean sprint, int startTicks, int ticks) {
        addInputModifier(InputModifier.empty(priority).sprint(sprint), startTicks, ticks);
    }

    private void addTimedModifier(TimedInputModifier timedModifier) {
        int index = 0;
        while (index < priorityQueue.size() && priorityQueue.get(index).compareTo(timedModifier) <= 0) {
            index++;
        }
        priorityQueue.add(index, timedModifier);
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (priorityQueue.isEmpty()) {
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
    public static record InputModifier(
            int priority,
            @Nullable Boolean forward,
            @Nullable Boolean backward,
            @Nullable Boolean left,
            @Nullable Boolean right,
            @Nullable Boolean jump,
            @Nullable Boolean sneak,
            @Nullable Boolean sprint) {
        public static final InputModifier EMPTY = new InputModifier(0, null, null, null, null, null, null, null);

        public static InputModifier empty(int priority) {
            return EMPTY.withPriority(priority);
        }

        public InputModifier forward(boolean value) {
            return new InputModifier(priority, value, backward, left, right, jump, sneak, sprint);
        }

        public InputModifier backward(boolean value) {
            return new InputModifier(priority, forward, value, left, right, jump, sneak, sprint);
        }

        public InputModifier left(boolean value) {
            return new InputModifier(priority, forward, backward, value, right, jump, sneak, sprint);
        }

        public InputModifier right(boolean value) {
            return new InputModifier(priority, forward, backward, left, value, jump, sneak, sprint);
        }

        public InputModifier jump(boolean value) {
            return new InputModifier(priority, forward, backward, left, right, value, sneak, sprint);
        }

        public InputModifier sneak(boolean value) {
            return new InputModifier(priority, forward, backward, left, right, jump, value, sprint);
        }

        public InputModifier sprint(boolean value) {
            return new InputModifier(priority, forward, backward, left, right, jump, sneak, value);
        }

        public void modify(PlayerInputUtils.Input input) {
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

        public boolean isEmpty() {
            return forward == null
                    && backward == null
                    && left == null
                    && right == null
                    && jump == null
                    && sneak == null
                    && sprint == null;
        }
    }

    public static class TimedInputModifier implements Comparable<TimedInputModifier> {
        private final int startTicks;
        private final int expireTicks;
        private final InputModifier modifier;

        public TimedInputModifier(int lastTicks, InputModifier modifier) {
            this(0, lastTicks, modifier);
        }

        public TimedInputModifier(int startTicks, int lastTicks, InputModifier modifier) {
            this.startTicks = Tasks.getTick() + startTicks;
            this.expireTicks = Tasks.getTick() + startTicks + lastTicks;

            this.modifier = modifier;
        }

        public boolean tickInput(PlayerInputUtils.Input input) {
            if (isExpired()) {
                return true;
            }
            if (Tasks.getTick() >= startTicks) {
                modifier.modify(input);
            }
            return false;
        }

        public boolean isExpired() {
            return Tasks.getTick() > expireTicks;
        }

        @Override
        public int compareTo(@NotNull PlayerInputManager.TimedInputModifier timedInputModifier) {
            return Integer.compare(modifier.priority(), timedInputModifier.modifier.priority());
        }
    }
}
