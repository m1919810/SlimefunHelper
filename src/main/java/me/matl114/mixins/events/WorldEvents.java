package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(World.class)
public abstract class WorldEvents {
    @Shadow
    @Final
    private boolean isClient;

    @WrapWithCondition(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/BlockEntityTickInvoker;tick()V"))
    public boolean shouldTickBlockEntities(BlockEntityTickInvoker instance) {
        if (isClient) {
            Event<BlockEntityTickInvoker> event = new Event<>(instance, true, false);
            Listener.getBlockEntityTickListener().handleValue(event);
            return !event.isCancelled();
        }
        return true;
    }
}
