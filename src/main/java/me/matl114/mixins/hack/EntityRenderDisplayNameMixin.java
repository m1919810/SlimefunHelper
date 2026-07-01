package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.RenderTasks;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class EntityRenderDisplayNameMixin {
    @WrapOperation(
            method = "updateRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/entity/EntityRenderer;hasLabel(Lnet/minecraft/entity/Entity;D)Z"))
    private boolean hasLabel(
            EntityRenderer instance, Entity entity, double squaredDistanceToCamera, Operation<Boolean> original) {
        if (entity instanceof PlayerEntity pl) {
            if (RenderTasks.getNameTag().hideName.get()) {
                return false;
            }
        }
        return original.call(instance, entity, squaredDistanceToCamera);
    }
}
