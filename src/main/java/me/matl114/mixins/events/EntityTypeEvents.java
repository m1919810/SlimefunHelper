package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(EntityType.class)
public abstract class EntityTypeEvents {
    @WrapOperation(
            method =
                    "create(Lnet/minecraft/world/World;Lnet/minecraft/entity/SpawnReason;)Lnet/minecraft/entity/Entity;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/EntityType$EntityFactory;create(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"))
    private <T extends Entity> T onCreate(
            EntityType.EntityFactory<T> instance,
            EntityType<T> tEntityType,
            World world,
            Operation<T> original,
            @Local(argsOnly = true) SpawnReason spawnReason) {
        T val = original.call(instance, tEntityType, world);
        Event<Entity> event = new Event<Entity>(val, true, true, tEntityType);
        Listener.getEntityCreateListener().handleValue(event);
        if (event.isCancelled()) {
            return null;
        } else {
            return (T) event.context();
        }
    }
}
