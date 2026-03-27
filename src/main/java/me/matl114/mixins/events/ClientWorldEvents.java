package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
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
            EntityAccess.of(instance).beforeTick();
            original.call(instance);
            EntityAccess.of(instance).afterTick();
            Listener.getEntityPostTickListener().handleValue(entityEvent);
        }
    }
}
