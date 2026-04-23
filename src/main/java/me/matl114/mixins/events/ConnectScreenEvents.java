package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenEvents {
    @Inject(
            method =
                    "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/multiplayer/ConnectScreen;<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;)V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private static void onPreConnect(
            Screen screen,
            MinecraftClient client,
            ServerAddress address,
            ServerInfo info,
            boolean quickPlay,
            CookieStorage cookieStorage,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<ServerAddress> infoLocalRef) {
        Event<ServerAddress> infoEvent = new Event<>(address, true, true, info);
        Listener.getServerPreConnectPoint().handleValue(infoEvent);
        if (infoEvent.isCancelled()) {
            ci.cancel();
        }
        if (infoEvent.context != address) {
            infoLocalRef.set(infoEvent.context);
        }
    }
}
