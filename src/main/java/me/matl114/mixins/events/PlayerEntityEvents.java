package me.matl114.mixins.events;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Listener;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityEvents {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onPlayerTravel(CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity clientPlayerEntity) {
            if (!Listener.getPlayerTravelingTick().fireEvent(clientPlayerEntity)) {
                ci.cancel();
            } else {
                if (!ClientPlayerAccess.of(clientPlayerEntity)
                        .getLegalMovementManager()
                        .preTravelTick(clientPlayerEntity)) {
                    ci.cancel();
                    ClientPlayerAccess.of(clientPlayerEntity)
                            .getLegalMovementManager()
                            .postTravelTick(clientPlayerEntity);
                }
            }
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onPlayerTravelReturn(CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity clientPlayerEntity) {
            ClientPlayerAccess.of(clientPlayerEntity).getLegalMovementManager().postTravelTick(clientPlayerEntity);
        }
    }
}
