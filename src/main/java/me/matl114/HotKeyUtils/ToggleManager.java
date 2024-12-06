package me.matl114.HotKeyUtils;

import com.google.common.base.Preconditions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public interface ToggleManager {
    public static ToggleManager of(){
        final HashMap<String ,AtomicBoolean> toggles = new LinkedHashMap<>();
        return ()->toggles;
    }
    public static ToggleManager of(HashMap<String,AtomicBoolean> map){
        return ()->map;
    }
    HashMap<String,AtomicBoolean> getRaw();
    @Nonnull
    default AtomicBoolean get(String value){
       return getRaw().computeIfAbsent(value,(val)->new AtomicBoolean(HotKeys.getToggles(value,false)));
    }
    default boolean getState(String value){
        return get(value).get();
    }
    default Runnable getToggle(String value){
        final AtomicBoolean toggle = get(value);
        return ()->{
            boolean result=!toggle.get();
            toggle.set(result);
            HotKeys.setToggles(value,result);
            MinecraftClient.getInstance().player.sendMessage(Text.literal("toggle "+value+(result?" on":" off")));
        };
    }
    default HashMap<String ,Runnable> getToggles(){
        LinkedHashMap<String ,Runnable> toggles = new LinkedHashMap<>();
        for(String toggle : this.getRaw().keySet()){
            toggles.put(toggle,getToggle(toggle));
        }
        return toggles;
    }
    default void register(String value, boolean defaultValue){
        Preconditions.checkArgument(!getRaw().containsKey(value));

        getRaw().put(value, new AtomicBoolean(HotKeys.getToggles(value,defaultValue)));
    }
}
