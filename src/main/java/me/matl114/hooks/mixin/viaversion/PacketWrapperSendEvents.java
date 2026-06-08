package me.matl114.hooks.mixin.viaversion;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import me.matl114.events.Event;
import me.matl114.events.PacketManager;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hooks.impl.PacketWrapperSendStorageImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Environment(EnvType.CLIENT)
@Pseudo
@Mixin(value = PacketWrapperImpl.class, remap = false)
public abstract class PacketWrapperSendEvents implements PacketWrapper {
    @WrapMethod(
            method = "Lcom/viaversion/viaversion/protocol/packet/PacketWrapperImpl;sendToServer0(Ljava/lang/Class;ZZ)V")
    public void onSendToServer(
            Class<?> protocol, boolean skipCurrentPipeline, boolean currentThread, Operation<Void> operation) {
        if (user() != null && user().getChannel() != null) {
            if (this.getPacketType() != null && this.getPacketType().state() == State.PLAY) {
                if (!PacketManager.startFlushOut) {
                    PacketWrapperSendStorageImpl storageImpl = new PacketWrapperSendStorageImpl(
                            System.currentTimeMillis(),
                            this,
                            this.user().getChannel(),
                            (bl) -> operation.call(protocol, skipCurrentPipeline, bl));
                    Event<PacketStorage> event = new Event<>(storageImpl, true, false);
                    PacketManager.getPacketQueueEvent().handleValue(event);
                    if (event.isCancelled()) {
                        PacketManager.handleQueueOut(storageImpl);
                        return;
                    }
                }
            }

            operation.call(protocol, skipCurrentPipeline, currentThread);
        }
    }
    //
    //    @WrapMethod(method = "sendToServer0")
    //    public void onSendToServer0(Class<?> protocol, boolean skipCurrentPipeline, boolean currentThread,
    // Operation<Void> operation) {
    //
    //    }
}
