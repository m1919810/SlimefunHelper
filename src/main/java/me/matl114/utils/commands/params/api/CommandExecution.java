package me.matl114.utils.commands.params.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.commands.interruption.InvalidExecutorError;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

public interface CommandExecution {
    @Nullable
    public PlayerEntity getExecutor();

    boolean hasPermission(String permission);

    default boolean isPlayer() {
        return getExecutor() instanceof PlayerEntity;
    }

    public static CommandExecution sender(@Nonnull PlayerEntity sender) {
        return new Sender(sender);
    }

    public void sendMessage(@Nonnull String message);

    public void sendMessage(Text message);

    @Nonnull
    public Vector3d getExecutePos();

    @Nonnull
    public World getExecuteWorld();

    @Nonnull
    default PlayerEntity getExecutorPlayer() {
        if (isPlayer()) {
            return (PlayerEntity) getExecutor();
        } else {
            throw new InvalidExecutorError(false);
        }
    }

    public CommandExecution EMPTY = new Sender(null);

    public record Sender(PlayerEntity sender) implements CommandExecution {

        @org.jetbrains.annotations.Nullable
        @Override
        public PlayerEntity getExecutor() {
            return sender;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void sendMessage(@NotNull String message) {
            if (sender != null) {
                Debug.sendPlayer(ChatUtils.stringToText(message));
            }
        }

        @Override
        public void sendMessage(Text message) {
            if (sender != null) {
                Debug.sendPlayer(message);
            }
        }

        @Override
        public Vector3d getExecutePos() {
            if (sender instanceof PlayerEntity p) {
                return new Vector3d(p.getX(), p.getY(), p.getZ());
            } else {
                return new Vector3d(0, 0, 0);
            }
        }

        @Override
        public World getExecuteWorld() {
            return sender instanceof PlayerEntity player
                    ? player.getEntityWorld()
                    : MinecraftClient.getInstance().world;
        }
    }

    public record System(boolean sout) implements CommandExecution {

        @org.jetbrains.annotations.Nullable
        @Override
        public PlayerEntity getExecutor() {
            return null;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void sendMessage(@NotNull String message) {
            if (sout) {
                Debug.info(message);
            }
        }

        @Override
        public void sendMessage(Text message) {
            if (sout) {
                Debug.info(message);
            }
        }

        @Override
        public Vector3d getExecutePos() {
            return new Vector3d(0, 0, 0);
        }

        @Override
        public World getExecuteWorld() {
            return MinecraftClient.getInstance().world;
        }
    }
}
