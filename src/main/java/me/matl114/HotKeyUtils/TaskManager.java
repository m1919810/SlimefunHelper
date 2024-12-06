package me.matl114.HotKeyUtils;

import com.google.common.base.Preconditions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public interface TaskManager {
    public static TaskManager of(){
        final HashMap<String ,Runnable> toggles = new LinkedHashMap<>();
        return ()->toggles;
    }
    public static TaskManager of(HashMap<String,Runnable> map){
        return ()->map;
    }
    HashMap<String, Runnable> getRaw();
    @Nonnull
    default Runnable getTask(String value){
        Preconditions.checkArgument(getRaw().containsKey(value));
        return getRaw().get(value);
    }
    default HashMap<String ,Runnable> getTasks(){
        return new LinkedHashMap<>(getRaw());
    }
    default void register(String value,Runnable task){
        Preconditions.checkArgument(!getRaw().containsKey(value));

        getRaw().put(value, task);
    }
}
