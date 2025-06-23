package me.matl114.mixins.HackMixin;

import me.matl114.access.EntityAccess;
import me.matl114.hackUtils.EntityTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityAccess {
    @Inject(method = "tick", at = @At("HEAD"))
    public void onEntityTickUpdate(CallbackInfo ci){
        Entity entity = (Entity)(Object)(this);
        EntityTasks.onEntityUpdate(entity);
    }
    @Inject(method = "onDataTrackerUpdate", at = @At("HEAD"))
    public void onEntityDataUpdate(List<DataTracker.SerializedEntry<?>> dataEntries, CallbackInfo ci){
        Entity entity = (Entity)(Object)(this);
        EntityTasks.onEntityDataUpdate(entity, dataEntries);
    }
    @Shadow
    protected abstract void setFlag(int index, boolean value);

    public void setDataFlag(int index, boolean val){
        this.setFlag(index, val);
    }



}
