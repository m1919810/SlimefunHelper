package me.matl114.mixins.hack;

import me.matl114.hacks.ExtraTasks;
import me.matl114.hacks.modules.extra.ClientExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(value = ClientLoginNetworkHandler.class, priority = Integer.MAX_VALUE)
public abstract class ClientLoginNetworkHandlerMixin {

    @ModifyArg(
            method = "onSuccess",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/network/packet/BrandCustomPayload;<init>(Ljava/lang/String;)V"))
    private String changeBrandName(String string) {
        String brand = ClientExtra.INSTANCE.clientBrandName.getValue();
        if (brand != null && !brand.isEmpty()) {
            return brand;
        }
        return string;
    }
}
