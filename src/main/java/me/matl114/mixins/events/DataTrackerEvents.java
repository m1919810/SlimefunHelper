package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracked;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DataTracker.class)
@Environment(EnvType.CLIENT)
public abstract class DataTrackerEvents {
    @Final
    @Shadow
    private DataTracked trackedEntity;

    @Inject(method = "writeUpdatedEntries", at = @At("HEAD"))
    private void callDataTrackerEntryUpdateEvents(
            List<DataTracker.SerializedEntry<?>> entries,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<List<DataTracker.SerializedEntry<?>>> entryRef) {
        // make it removable
        if (Listener.getEntityTrackDataUpdate().isEmpty()) return;
        if (this.trackedEntity instanceof Entity entity) {
            // recreate List to avoid immutableList
            List<DataTracker.SerializedEntry<?>> entryList = new ArrayList<>();
            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                DataTracker.SerializedEntry<?> serializedEntry = iterator.next();
                Event<DataTracker.SerializedEntry<?>> serializedEntryMutableObject =
                        new Event<>(serializedEntry, true, true, this.trackedEntity);
                Listener.getEntityTrackDataUpdate().handleValue(serializedEntryMutableObject);
                if (serializedEntryMutableObject.isCancelled() || serializedEntryMutableObject.context() == null) {
                    // skip current serializedEntry
                    continue;
                } else {
                    entryList.add(serializedEntryMutableObject.context());
                }
            }
            entryRef.set(entryList);
        }
    }
}
