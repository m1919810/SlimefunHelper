package me.matl114.SlimefunMixin.FixMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(LoginHelloC2SPacket.class)
public abstract class LoginHelloNameFixC2SPacketMixin {
    //运行逻辑
    //client->server helloPacket
    //server->handleHello->client helloPacket
    //server->LoginHandler.fireEvent->readyToAcceptPlayer
    //server->tick->
    @ModifyArg(method = "write",at= @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;writeString(Ljava/lang/String;I)Lnet/minecraft/network/PacketByteBuf;"),index = 0)
    private String onChangeNameSend(String name) {
//        byte[] nameBytes = new byte[]{33,34,35,36};
//        return new String(nameBytes, StandardCharsets.UTF_8);//name.substring(0,Math.min(name.length(),16));
        return name==null?"null":name.substring(0,Math.min(16,name.length()));
    }
}
