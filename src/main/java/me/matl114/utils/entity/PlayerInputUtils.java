package me.matl114.utils.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.GameOptions;

public class PlayerInputUtils {
    public static Input of(net.minecraft.client.input.Input input) {
        return new Input(
                input.pressingForward,
                input.pressingBack,
                input.pressingLeft,
                input.pressingRight,
                input.jumping,
                input.sneaking);
    }

    public static Input of(GameOptions options) {
        return new Input(
                options.forwardKey.isPressed(),
                options.backKey.isPressed(),
                options.leftKey.isPressed(),
                options.rightKey.isPressed(),
                options.jumpKey.isPressed(),
                options.sneakKey.isPressed());
    }

    @AllArgsConstructor
    @Accessors(fluent = true, chain = true)
    @Setter
    @Getter
    public static class Input {
        boolean forward;
        boolean backward;
        boolean left;
        boolean right;
        boolean jump;
        boolean sneak;

        public Input(boolean forward, boolean backward, boolean left, boolean right) {
            this(forward, backward, left, right, false, false);
        }

        public int forwardSpeed() {
            return this.forward == this.backward ? 0 : (this.forward ? 1 : -1);
        }

        public int sidewaysSpeed() {
            return this.left == this.right ? 0 : (this.left ? 1 : -1);
        }

        public int upwardSpeed() {
            return this.jump == this.sneak ? 0 : (this.jump ? 1 : -1);
        }

        public void applyInput(net.minecraft.client.input.Input input) {
            input.pressingForward = forward;
            input.pressingBack = backward;
            input.pressingLeft = left;
            input.pressingRight = right;
            input.sneaking = sneak;
            input.jumping = jump;
        }
    }
}
