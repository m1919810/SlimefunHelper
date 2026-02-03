package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Listener;
import me.matl114.events.Event;
import me.matl114.utils.impl.collections.LinkNode;
import me.matl114.utils.impl.entity.ProgressWrapper;
import me.matl114.utils.impl.containers.MetaData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityEvents<T extends Entity> implements EntityAccess<T> {
    @ModifyExpressionValue(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d onModifyVelocity(Vec3d original){
        if(!checkClientPlayer())return original;
        Event<Vec3d> vec3d = new Event<>(original, true, true);
        Listener.getPlayerVelocityTick().handleValue(vec3d);
        if(vec3d.isCancelled()){
            return Vec3d.ZERO;
        }else{
            return vec3d.context();
        }
    }

    @Unique
    public MetaData metaData;
    @Unique
    public MetaData getMetadata(){
        if(metaData == null){
            metaData = new MetaData();
        }
        return metaData;
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
    @Shadow
    protected abstract void setFlag(int index, boolean value);

    @Shadow
    protected abstract boolean getFlag(int index);
    @Unique
    public void setDataFlag(int index, boolean val){
        this.setFlag(index, val);
    }
    @Unique
    public boolean getDataFlag(int index){
        return getFlag(index);
    }


    @Inject(method = "tick", at = @At("HEAD"))
    public void onEntityTickUpdate(CallbackInfo ci){
        Entity entity = (Entity)(Object)(this);
        Listener.getEntityTickListener().broadcast(entity);
    }
    @Inject(method = "onDataTrackerUpdate", at = @At("HEAD"))
    public void onEntityDataUpdate(List<DataTracker.SerializedEntry<?>> dataEntries, CallbackInfo ci){
        Entity entity = (Entity)(Object)(this);
        Listener.getEntityDataListener().broadcast(entity, dataEntries);
    }
}
