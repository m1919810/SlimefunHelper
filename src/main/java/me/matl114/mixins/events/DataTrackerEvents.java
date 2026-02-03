package me.matl114.mixins.events;

import me.matl114.events.Listener;
import me.matl114.events.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracked;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

import java.util.Iterator;

@Mixin(DataTracker.class)
@Environment(EnvType.CLIENT)
public abstract class DataTrackerEvents {

    @Final
    @Shadow
    private DataTracker.Entry<?>[] entries;
    @Final
    @Shadow
    private DataTracked trackedEntity;
    @Redirect(method = "writeUpdatedEntries", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"))
    private <E> E listeneEntriesUpdate(Iterator<E> instance){
        var entry = instance.next();
        if(entry instanceof DataTracker.SerializedEntry<?> serializedEntry){

            if(this.trackedEntity instanceof Entity entity){
                Event<DataTracker.SerializedEntry<?>> serializedEntryMutableObject = new Event<>(serializedEntry, true, true, entity);
                Listener.getEntityTrackDataUpdate().handleValue(serializedEntryMutableObject);
                if(serializedEntryMutableObject.isCancelled() || serializedEntryMutableObject.context() == null){
                    //create current
                    DataTracker.Entry entry1 = this.entries[serializedEntry.id()];
                    return (E) new DataTracker.SerializedEntry(serializedEntry.id(), serializedEntry.handler(), entry1.get());
                }else{
                    return (E) serializedEntryMutableObject.context();
                }
            }
        }
        return entry;
    }
}
