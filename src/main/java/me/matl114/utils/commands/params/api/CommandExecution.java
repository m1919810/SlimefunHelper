package me.matl114.matlib.utils.command.params.api;

import me.matl114.matlib.utils.Debug;
import me.matl114.matlib.utils.command.interruption.InvalidExecutorError;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface CommandExecution {
    @Nullable
    public CommandSender getExecutor();

    boolean hasPermission(String permission);

    default boolean isPlayer(){
        return getExecutor() instanceof Player;
    }

    public static CommandExecution sender(@Nonnull CommandSender sender) {
        return new Sender(sender);
    }

    public void sendMessage(@Nonnull String message);

    public void sendMessage(Component message);

    @Nonnull
    public Vector3d getExecutePos();
    @Nonnull
    public World getExecuteWorld();

    @Nonnull
    default Player getExecutorPlayer(){
        if(isPlayer()){
            return (Player) getExecutor();
        }else{
            throw new InvalidExecutorError(false);
        }
    }

    public record Sender(CommandSender sender) implements CommandExecution {

        @org.jetbrains.annotations.Nullable
        @Override
        public CommandSender getExecutor() {
            return sender;
        }

        @Override
        public boolean hasPermission(String permission) {
            return sender != null && sender.hasPermission(permission);
        }

        @Override
        public void sendMessage(@NotNull String message) {
            if(sender != null){
                sender.sendMessage(message);
            }
        }

        @Override
        public void sendMessage(Component message) {
            if(sender != null){
                sender.sendMessage(message);
            }
        }

        @Override
        public Vector3d getExecutePos() {
            if(sender instanceof Player p){
                Location loc = p.getLocation();
                return new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            }else {
                return new Vector3d(0,0,0);
            }
        }

        @Override
        public World getExecuteWorld() {
            return sender instanceof Player player ? player.getLocation().getWorld() : Bukkit.getWorlds().stream().findFirst().orElseThrow();
        }


    }

    public record System(boolean sout) implements CommandExecution {

        @org.jetbrains.annotations.Nullable
        @Override
        public CommandSender getExecutor() {
            return null;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void sendMessage(@NotNull String message) {
            if(sout){
                Debug.logger(message);
            }
        }

        @Override
        public void sendMessage(Component message) {
            if(sout){
                Debug.logger(message);
            }
        }

        @Override
        public Vector3d getExecutePos() {
            return new Vector3d(0,0,0);
        }

        @Override
        public World getExecuteWorld() {
            return Bukkit.getWorlds().stream().findFirst().orElseThrow();
        }
    }
}
