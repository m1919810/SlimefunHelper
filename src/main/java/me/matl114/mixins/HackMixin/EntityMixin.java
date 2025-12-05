package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import me.matl114.access.EntityAccess;
import me.matl114.access.EntityInternalAccess;
import me.matl114.hackUtils.EntityTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LinkNode;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
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
public abstract class EntityMixin<T extends Entity> implements EntityAccess<T>, EntityInternalAccess<T> {
    @Unique
    byte renderTracked = 0;
    @Unique
    public byte renderTrackedLevel(){
        return renderTracked;
    }
    @Unique
    public void markRenderTracked(byte tracked){
        renderTracked = tracked;
    }
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

    @Unique
    private static final Config.FlagRef noSlowInBlock = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_NO_SLOW_DOWN_BLOCK_IN);
    @Inject(method = "slowMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;onLanding()V", shift = At.Shift.AFTER), cancellable = true)
    private void onSlowMovementDoNotModifyVelocity(BlockState state, Vec3d multiplier, CallbackInfo ci){
        if(noSlowInBlock.get ( ) &&(Entity)(Object)this instanceof ClientPlayerEntity player){
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d onModifyVelocity(Vec3d original){
        if(checkNotClientPlayer())return original;
        Event<Vec3d> vec3d = new Event<>(original, true, true);
        Listener.getPlayerVelocityUpdate().handleValue(vec3d);
        if(vec3d.isCancelled()){
            return Vec3d.ZERO;
        }else{
            return vec3d.context();
        }
    }

}
