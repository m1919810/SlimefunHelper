package me.matl114.hooks.mixin.litematica;

import fi.dy.masa.litematica.network.ServuxLitematicaHandler;
import me.matl114.hacks.modules.extra.BadPacketsFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ServuxLitematicaHandler.class)
public abstract class ServuxLitematicaFixMixin {
    @Inject(
            method =
                    "Lfi/dy/masa/litematica/network/ServuxLitematicaHandler;handleBulkData(ILnet/minecraft/nbt/NbtCompound;)V",
            at = @At("HEAD"),
            remap = true,
            require = 0,
            expect = 0,
            cancellable = true)
    public void onHandleBulkData(int type, NbtCompound nbt, CallbackInfo ci) {
        if (nbt == null || nbt.isEmpty()) {
            return;
        }

        var task = nbt.get("Task");
        if (task instanceof NbtString string) {
            String task2 = string.asString();
            switch (task2) {
                    // File-Transmit support
                case "Litematic-TransmitStart",
                        "Litematic-TransmitCancel",
                        "Litematic-TransmitData",
                        "Litematic-TransmitEnd" -> {
                    BadPacketsFix.INSTANCE.logI18NSub(
                            "LitematicaFix", "message.module.bad-packets.litematica-backdoor");
                    if (BadPacketsFix.INSTANCE.cancelLitematicaTransmit.get()) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}
