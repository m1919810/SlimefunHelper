package me.matl114.ListenerUtils;

import me.matl114.HackUtils.RenderTasks;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Listener {
    public static void init(){

    }

    private static final HashSet<BiPredicate<ClientConnection,Packet<?>>> listenerS2C = new LinkedHashSet<>();
    private static final HashSet<BiPredicate<ClientConnection,Packet<?>>> listenerC2S = new LinkedHashSet<>();
    public static void registerPacketListener(Consumer<Packet<?>> packetListener,boolean isS2C){
        registerPacketListener((c)->{packetListener.accept(c);return true;},isS2C);
    }
    public static void registerPacketListener(Predicate<Packet<?>> packetListener,boolean isS2C){
        if(isS2C){
            listenerS2C.add((conn,pack)->packetListener.test(pack));
        }else {
            listenerC2S.add((conn,pack)->packetListener.test(pack));
        }
    }
    public static void registerPacketListener(BiPredicate<ClientConnection,Packet<?>> packetListener,boolean isS2C){
        if(isS2C){
            listenerS2C.add(packetListener);
        }else {
            listenerC2S.add(packetListener);
        }
    }
    public static boolean acceptS2CPacket(ClientConnection connection,Packet<?> packet){

        return unpackMultiPacket(connection,packet,true);
    }
    public static boolean sendC2SPacket(ClientConnection connection,Packet<?> packet){
        return unpackMultiPacket(connection,packet,false);
    }
    @Unique
    private static boolean onSinglePacketListen(ClientConnection connection,Packet<?> packet, Set<BiPredicate<ClientConnection,Packet<?>>> listeners){
        for(BiPredicate<ClientConnection,Packet<?>> listener:listeners){
            if(!listener.test(connection,packet)){
                return false;
            }
        }
        return true;
    }
    @Unique
    private static boolean unpackMultiPacket(ClientConnection connection,Packet<?> packet,boolean isS2C ) {
        if(packet instanceof BundlePacket<?> bundle){
            var iter= bundle.getPackets();
            for (var pkt:iter){
                if(!unpackMultiPacket(connection,pkt,isS2C)){
                    return false;
                }
            }
            return true;
        }else{
            return onSinglePacketListen(connection,packet,isS2C?listenerS2C:listenerC2S);
        }
    }
    static{
        ConnectionListener.init();
    }
}
