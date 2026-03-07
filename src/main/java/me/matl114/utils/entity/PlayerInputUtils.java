package me.matl114.utils.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.PlayerInput;

public class PlayerInputUtils {

    public static Input of(PlayerInput input) {
        return new Input(input);
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
        boolean sprint;

        public Input(PlayerInput input) {
            this(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    input.sneak(),
                    input.sprint());
        }

        public Input(boolean forward, boolean backward, boolean left, boolean right) {
            this(forward, backward, left, right, false, false, false);
        }

        public PlayerInput toPlayerInput() {
            return new PlayerInput(
                    this.forward, this.backward, this.left, this.right, this.jump, this.sneak, this.sprint);
        }

        public int forwardSpeed() {
            return this.forward == this.backward ? 0 : (this.forward ? 1 : -1);
        }

        public int sidewaysSpeed() {
            return this.left == this.right ? 0 : (this.left ? 1 : -1);
        }
    }
}
