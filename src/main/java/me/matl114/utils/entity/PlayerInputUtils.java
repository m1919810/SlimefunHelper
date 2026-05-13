package me.matl114.utils.entity;

import lombok.*;
import lombok.experimental.Accessors;
import me.matl114.utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;

public class PlayerInputUtils {
    public static Input of(net.minecraft.client.input.Input input) {
        return new Input(input.playerInput);
    }

    public static Input of(PlayerInput input) {
        return new Input(input);
    }

    public static Input of(GameOptions options) {
        return new Input(
                options.forwardKey.isPressed(),
                options.backKey.isPressed(),
                options.leftKey.isPressed(),
                options.rightKey.isPressed(),
                options.jumpKey.isPressed(),
                options.sneakKey.isPressed(),
                options.sprintKey.isPressed());
    }

    public static Input of(PlayerInputC2SPacket packet) {
        return of(packet.input());
    }

    public static Input tryCorrectMovementInput(Input input, float originalYaw, float currentYaw) {
        float diff = EntityUtils.getSafeYawDiff(originalYaw, currentYaw);
        int forwardSpeed;
        int sidewaySpeed;
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
    public static class Input implements Cloneable {
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

        public PlayerInputC2SPacket toPlayerInputPacket() {
            return new PlayerInputC2SPacket(toPlayerInput());
        }

        public void sendPlayerInputPacket() {
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(toPlayerInputPacket());
        }

        public void sendPlayerSneakUpdatePacket() {
            sendPlayerInputPacket();
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
            input.playerInput = toPlayerInput();
        }

        public void applyInput(GameOptions options) {
            options.forwardKey.setPressed(forward);
            options.backKey.setPressed(backward);
            options.leftKey.setPressed(left);
            options.rightKey.setPressed(right);
            options.jumpKey.setPressed(jump);
            options.sneakKey.setPressed(sneak);
            options.sprintKey.setPressed(sprint);
        }

        public boolean hasMovement() {
            return forward || backward || left || right || jump;
        }

        public boolean hasWASDMovement() {
            return (forward != backward) || (left != right);
        }

        public boolean hasMovementControl() {
            return forward || backward || left || right || jump || sneak;
        }

        @Override
        public Input clone() {
            try {
                Input clone = (Input) super.clone();
                // TODO: copy mutable state here, so the clone can't change the internals of the original
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }

        @Override
        public String toString() {
            return "Input{" + "forward="
                    + forward + ", backward="
                    + backward + ", left="
                    + left + ", right="
                    + right + ", jump="
                    + jump + ", sneak="
                    + sneak + ", sprint="
                    + sprint + '}';
        }
    }
}
