package me.matl114.mixins.HackMixin;

import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientBrandRetriever.class, priority = 1)
@Environment(EnvType.CLIENT)
public abstract class ClientBrandMixin {

//    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
//    private static void onBrandName(CallbackInfoReturnable<String> cir){

//    }
}
