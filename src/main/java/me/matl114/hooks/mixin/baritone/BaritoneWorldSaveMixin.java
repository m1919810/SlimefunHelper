package me.matl114.hooks.mixin.baritone;

import baritone.cache.WorldProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.task.ServerStorage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(WorldProvider.class)
public abstract class BaritoneWorldSaveMixin {
    @WrapOperation(
            method = {
                "a(Lnet/minecraft/world/World;)V",
                "Lbaritone/cache/WorldProvider;getSaveDirectories(Lnet/minecraft/world/World;)Ljava/util/Optional;"
            },
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ServerInfo;address:Ljava/lang/String;"),
            require = 0,
            expect = 0)
    private String onWorldLoadAddressRemap(ServerInfo instance, Operation<String> original) {
        String address = original.call(instance);
        if (ServerStorage.INSTANCE.enableProxyBaritone.get()) {
            return ServerStorage.INSTANCE.getSaveId(address);
        }
        return address;
    }
}
