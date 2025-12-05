package me.matl114.mixins.HackMixin;

import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(value = ClientLoginNetworkHandler.class, priority = Integer.MAX_VALUE)
public abstract class ClientLoginNetworkHandlerMixin {
    @Unique
    private static final Config.StringRef brandName = Configs.TEST_CONFIG.getString(Configs.CLIENT_BRAND_NAME);

    @ModifyArg(method = "onSuccess", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/BrandCustomPayload;<init>(Ljava/lang/String;)V"))
    private String changeBrandName(String string){
        String brand = brandName.getValue();
        if(brand != null && !brand.isEmpty()){
            return brand;
        }
        return string;
    }
}
