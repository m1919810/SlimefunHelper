package me.matl114.listenerUtils;

import lombok.Getter;
import me.matl114.utils.UtilClass.ArgumentCancellablePoint;
import me.matl114.utils.UtilClass.ArgumentListenerPoint;

import me.matl114.utils.UtilClass.ListenerPoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Unique;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Listener {
    public static void init(){

    }

    private static final HashSet<BiPredicate<ClientConnection,Packet<?>>> listenerS2C = new LinkedHashSet<>();
    private static final HashSet<BiPredicate<ClientConnection,Packet<?>>> listenerC2S = new LinkedHashSet<>();
    private static final HashMap<Class<?>,HashSet< BiPredicate<ClientConnection,Packet<?>>>> packetListener = new LinkedHashMap<>();
    private static final HashMap<Class<? extends Packet<?>>, Class<? extends Packet<?>>> mappedPacketClass = new LinkedHashMap<>();
    public static <T extends Packet<?>, W extends Packet<?>> Class<W> getMappedPacketClass(Class<T> packet){
        return (Class<W>) mappedPacketClass.computeIfAbsent((Class<? extends Packet<?>>) packet, Listener::getPacketClass);
    }
    private static <T extends Packet<?>, W extends Packet<?>> Class<W> getPacketClass(Class<T> packet){
        Class<?> clazz1 = packet;
        while (Packet.class.isAssignableFrom(clazz1.getSuperclass())){
            clazz1 = clazz1.getSuperclass();
        }
        return (Class<W>) clazz1;
    }
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
    public static <T extends Packet<?>> void registerSinglePacketListener(Class<T> clazz, Consumer< T> predicate){
        registerSinglePacketListener(clazz, ((connection, t) -> {predicate.accept(t);return true;}));
    }
    public static <T extends Packet<?>> void registerSinglePacketListener(Class<T> clazz, Predicate< T> predicate){
        registerSinglePacketListener(clazz, ((connection, t) -> predicate.test(t)));
    }
    public static <T extends Packet<?>> void registerSinglePacketListener(Class<T> clazz, BiPredicate<ClientConnection, T> predicate){
        packetListener.computeIfAbsent((Class<?>) clazz, (c)->new LinkedHashSet<>()).add((BiPredicate<ClientConnection, Packet<?>>) predicate);
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
        Class<?> t = mappedPacketClass.computeIfAbsent((Class<? extends Packet<?>>) packet.getClass(), Listener::getPacketClass);
        var re = packetListener.get(t);
        if(re != null && !re.isEmpty()){
            for (BiPredicate<ClientConnection,Packet<?>> predicate :re){
                if(!predicate.test(connection, packet)){
                    return false;
                }
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

    @Getter
    private static final ListenerPoint<Void> gameJoinPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Void> serverDisconnectPoint  = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<HandledScreen<?>> screenOpenPoint = new ListenerPoint<>();

    @Getter
    private static final ArgumentCancellablePoint<Packet<?>> mainThreadPacketPreApplyPoint = new ArgumentCancellablePoint<>();

    private static final ArgumentListenerPoint<Packet<?>> mainThreadPacketPostApplyPoint = new ArgumentListenerPoint<>();
    public static boolean prepacketListenerApplyPoint(Packet<?> packet, PacketListener listener){
        if(!MinecraftClient.getInstance().isOnThread()){
            return false;
        }
        return !mainThreadPacketPreApplyPoint.handleValue(packet, listener);
    }
    public static void postPacketListenerApplyPoint(Packet<?> packet, PacketListener listener){
        if(!MinecraftClient.getInstance().isOnThread()){
            return ;
        }
        mainThreadPacketPostApplyPoint.handleValue(packet, listener);
    }
    @Getter
    private static final ArgumentCancellablePoint<BlockHitResult> prePlayerUseItemAtBlock = new ArgumentCancellablePoint<>();
    @Getter
    private static final ArgumentListenerPoint<BlockHitResult> postPlayerUseItemAtBlock = new ArgumentListenerPoint<>();

    public static boolean doItemUseAtBlockPre(Hand hand, BlockHitResult result){
        return prePlayerUseItemAtBlock.handleValue(result, hand);
    }
    public static void doItemUseAtBlockPost(Hand hand, BlockHitResult result){
        postPlayerUseItemAtBlock.handleValue(result, hand);
    }
    static{
        //ConnectionListener.init();
    }
}
