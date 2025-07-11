package me.matl114.mixins.HackMixin;

import it.unimi.dsi.fastutil.BidirectionalIterator;
import me.matl114.access.EntityAccess;
import me.matl114.hackUtils.EntityTasks;
import me.matl114.utils.UtilClass.LinkNode;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin<T extends Entity> implements EntityAccess<T> {
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

    @Unique
    public void addTickWrapper(ProgressWrapper<T> wrapper){
        headNode.insertAfter(wrapper);
    }


    @Unique
    private final LinkNode<ProgressWrapper<T>> headNode = LinkNode.createHead();
    @Unique
    private BidirectionalIterator<ProgressWrapper<T>> usedIterator;
    @Unique
    public void beforeTick(){
        var iter = LinkNode.iterator(headNode);
        while (iter.hasNext()){
            var next = iter.next();
            next.preProgress((T) (Object)this);
        }
        usedIterator = iter;
    }

    @Unique
    public void afterTick(){
        var iter = usedIterator;
        usedIterator = null;
        while (iter.hasPrevious()){
            var prev = iter.previous();
            prev.postProgress((T) (Object)this);
            if(!prev.stillWrap((T) (Object)this)){
                iter.remove();
            }
        }
    }

}
