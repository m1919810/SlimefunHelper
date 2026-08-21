package me.matl114.hooks.mixin.xaero;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.task.ServerStorage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.MapProcessor;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(MapProcessor.class)
public abstract class XaeroMapProcessorMixin {
    @WrapOperation(
            method =
                    "Lxaero/map/MapProcessor;getMainId(ILnet/minecraft/client/network/ClientPlayNetworkHandler;)Ljava/lang/String;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ServerInfo;address:Ljava/lang/String;"),
            require = 0)
    private String onGetServerAddressRemap(ServerInfo info, Operation<String> operation) {
        String address = operation.call(info);
        if (ServerStorage.INSTANCE.enableProxyXaeroMap.get()) {
            return ServerStorage.INSTANCE.getSaveId(address);
        }
        return address;
    }
}
