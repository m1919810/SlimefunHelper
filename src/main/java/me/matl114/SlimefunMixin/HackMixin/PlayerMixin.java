package me.matl114.SlimefunMixin.HackMixin;

import lombok.Getter;
import me.matl114.Access.ClientPlayerAccess;
import me.matl114.HotKeyUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin implements ClientPlayerAccess {

    @Final
    @Shadow
    public ClientPlayNetworkHandler networkHandler;

    //    @Unique
//    private ScreenHandler keepedInventoryHandler=null;
    @Shadow
    public abstract void closeScreen();


    @Shadow @Final protected MinecraftClient client;

    @Unique
    @Getter
    public HandledScreen keepedInv=null;
    @Getter
    public ScreenHandler keepedInvHandler=null;

    @Unique
    public void clearKeepedInventory(boolean closeInv){
        keepedInv=null;
        ScreenHandler handler=keepedInvHandler;
        keepedInvHandler=null;
        if(closeInv){
            this.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(((ClientPlayerEntity)(Object)this).currentScreenHandler.syncId));
            this.closeScreen();
        }

    }
    @Inject(method="closeHandledScreen",at=@At(value = "HEAD"),cancellable = true)
    public void closeHandledScreen(CallbackInfo ci) {
        if(HotKeys.getButtonToggleManager().get(HotKeys.KEEP_INV).get()) {
            if(this.client.currentScreen instanceof HandledScreen handled) {
                keepedInv= handled;
                this.keepedInvHandler=((ClientPlayerEntity)(Object)this).currentScreenHandler;
                this.closeScreen();
                ci.cancel();
            }

        }
    }

}
