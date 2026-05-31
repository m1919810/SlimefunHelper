package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.crash.CrashException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class ClientWorldEvents {
    @WrapOperation(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    public void onEntityTick(Entity instance, Operation<Void> original) {
        Event<Entity> entityEvent = new Event<>(instance, true, false);
        Listener.getEntityPreTickListener().handleValue(entityEvent);
        if (entityEvent.isCancelled()) {
            return;
        } else {
            try {
                original.call(instance);
            } catch (Throwable e) {
                if (e instanceof CrashException crashException
                        && crashException.getCause() instanceof OutOfMemoryError) {
                    throw e;
                }
                if (Listener.handleException(e, Listener.ExceptionType.ENTITY_TICK, instance)) {
                    throw e;
                }
            } finally {
                Listener.getEntityPostTickListener().handleValue(entityEvent);
            }
        }
    }
}
