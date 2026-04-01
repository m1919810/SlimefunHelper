package me.matl114.utils.entity;

import lombok.*;
import lombok.experimental.Accessors;
import me.matl114.utils.EntityUtils;
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

    public static Input tryCorrectMovementInput(Input input, float originalYaw, float currentYaw) {
        float diff = EntityUtils.getSafeYawDiff(originalYaw, currentYaw);
        int forwardSpeed;
        int sidewaySpeed;
        boolean w, a, s, d;
        int movementForward = input.forwardSpeed();
        int movementSideways = input.sidewaysSpeed();
        if (diff < 22.5 && diff >= -22.5) {
            // do nothing
            return input;
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
            return input;
        }
        // sync values
        return new Input(
                forwardSpeed > 0,
                forwardSpeed < 0,
                sidewaySpeed > 0,
                sidewaySpeed < 0,
                input.jump(),
                input.sneak(),
                input.sprint());
    }

    public static final Input EMPTY = new Input(false, false, false, false, false, false, false);

    @AllArgsConstructor
    @Accessors(fluent = true, chain = true)
    @Setter
    @Getter
    @With
    @ToString
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
