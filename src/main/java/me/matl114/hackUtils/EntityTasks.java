package me.matl114.hackUtils;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;
import me.matl114.utils.UtilClass.ArgumentListenerPoint;
import me.matl114.utils.UtilClass.ListenerPoint;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EntityTasks {
//    private static final Int2ObjectOpenHashMap<List<Consumer<Entity>>> trackingEntityId = new Int2ObjectOpenHashMap<>();
    //private static final List<Consumer<Entity>> entityUpdateListener = new ArrayList<>();
    public static void trackEntity(int i, Consumer<Entity> entityConsumer){

    }
//    public static void registerEntityUpdateListener(Consumer<Entity> entityConsumer){
//        entityUpdateListener.add(entityConsumer);
//    }
    @Getter
    private static final ListenerPoint<Entity> entityTickListener = new ListenerPoint<>();
    public static void onEntityUpdate(Entity entity){
        int id = entity.getId();
//        var re = trackingEntityId.get(id);
//        if(re != null && !re.isEmpty()){
//            re.forEach(e -> e.accept(entity));
//        }
        entityTickListener.handleValue(entity);
    }
    @Getter
    private static final ListenerPoint<Entity> entityDataListener = new ListenerPoint<>();
    public static void onEntityDataUpdate(Entity entity,@Nullable List<DataTracker.SerializedEntry<?>> lst){
        entityDataListener.handleValue(entity);
    }

}
