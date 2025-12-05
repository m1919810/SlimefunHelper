package me.matl114.listenerUtils;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import me.matl114.hackUtils.MovTasks;
import me.matl114.managers.IHotKey;
import me.matl114.utils.UtilClass.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Listener {
    public static void init(){

    }
    private static final Map<PacketType<?>, Class<? extends Packet<?>>> registeredPacketTypes = new LinkedHashMap<>();
    private static void registerPacketTypesInternal(Class<?> clazz){
        for (var field: clazz.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers()) && PacketType.class.isAssignableFrom(field.getType())){
                try {
                    PacketType<?> typeInstance = (PacketType<?>) field.get(null);
                    if(field.getGenericType() instanceof ParameterizedType parameterizedType){
                        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) parameterizedType.getActualTypeArguments()[0];
                        registeredPacketTypes.put((PacketType<?>) typeInstance, packetClass);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    static{
        //register PlayPackets
        registerPacketTypesInternal(CommonPackets.class);
        registerPacketTypesInternal(PlayPackets.class);
        registerPacketTypesInternal(LoginPackets.class);
        registerPacketTypesInternal(PingPackets.class);
        registerPacketTypesInternal(StatusPackets.class);
        registerPacketTypesInternal(HandshakePackets.class);
        registerPacketTypesInternal(ConfigPackets.class);
        registerPacketTypesInternal(CookiePackets.class);
    }
    public static Class<? extends Packet<?>> getPacketClassById(Identifier id, boolean s2c){
        return registeredPacketTypes.entrySet().stream()
            .filter(type -> Objects.equals(type.getKey().id(), id) && type.getKey().side() == (s2c ? NetworkSide.CLIENTBOUND : NetworkSide.SERVERBOUND))
            .findAny()
            .map(Map.Entry::getValue)
            .orElse(null);

    }


    private static final HashSet<BiPredicate<ClientConnection,Packet<?>>> listenerS2C = new LinkedHashSet<>();
    private static final HashSet<BiPredicate<ClientConnection,Packet<?>>> listenerC2S = new LinkedHashSet<>();
    private static final Map<Class<?>,HashSet< BiPredicate<ClientConnection,Packet<?>>>> packetListener = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Deque<BiPredicate<ClientConnection, Packet<?>>>> packetCatcher = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Packet<?>>, Class<? extends Packet<?>>> mappedPacketClass = new ConcurrentHashMap<>();
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
    public static <T extends Packet<?>> void registerSinglePacketCatcher(Class<T> clazz){
        registerSinglePacketCatcher(clazz, ((connection, t) -> {return true;}));
    }
    public static <T extends Packet<?>> void registerSinglePacketCatcher(Class<T> clazz, Predicate< T> predicate){
        registerSinglePacketCatcher(clazz, ((connection, t) -> predicate.test(t)));
    }
    public static <T extends Packet<?>> void registerSinglePacketCatcher(Class<T> clazz, BiPredicate<ClientConnection, T> predicate){
        packetCatcher.computeIfAbsent((Class<?>) clazz, (c)->new ArrayDeque<>()).add((BiPredicate<ClientConnection, Packet<?>>) predicate);
    }


    public static boolean acceptS2CPacket(ClientConnection connection,Packet<?> packet){

        return unpackMultiPacket(connection,packet,true);
    }
    public static boolean sendC2SPacket(ClientConnection connection,Packet<?> packet){
        return unpackMultiPacket(connection,packet,false);
    }




    @Unique
    private static boolean onSinglePacketListen(ClientConnection connection,Packet<?> packet, boolean s2c, Set<BiPredicate<ClientConnection,Packet<?>>> listeners){
        Event<Packet<?>> packetEvent = new Event<>(packet, true, false, connection);
        if(s2c){
            getPacketAcceptPoint().handleValue(packetEvent);
        }else {
            getPacketSendPoint().handleValue(packetEvent);
        }
        if (packetEvent.isCancelled()){
            return false;
        }

        for(BiPredicate<ClientConnection,Packet<?>> listener:listeners){
            if(!listener.test(connection,packet)){
                return false;
            }
        }

        Class<?> t =  getMappedPacketClass(packet.getClass());
        var re2 = packetCatcher.get(t);
        boolean result =true;
        if(re2 != null && !re2.isEmpty()){
            Iterator<BiPredicate<ClientConnection, Packet<?>>> predicateIterator = re2.iterator();
            while (predicateIterator.hasNext()){
                var re3 = predicateIterator.next();
                if(re3.test(connection, packet)){
                    result = false;
                }
                predicateIterator.remove();
            }
        }
        if(!result)return false;
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
            return onSinglePacketListen(connection,packet, isS2C,isS2C?listenerS2C:listenerC2S);
        }
    }
    @Getter
    private static final ListenerPoint<Event<String>> chatEntryPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Text>> messageAddToHudPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<ChatHudLine>> messageAddToVisiblePoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<String>> chatScreenSendInput = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Screen>> clientScreenClose = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Void> gameJoinPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Void> worldSwitchPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Void> serverDisconnectPoint  = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<HandledScreen<?>> screenOpenPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<CommandDispatcher<CommandSource>> commandReloadPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Packet<?>>> packetAcceptPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Packet<?>>> packetSendPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Packet<?>>> mainThreadPacketPreApplyPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Packet<?>>> mainThreadPacketPostApplyPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<ClientPlayerEntity>> clientPlayerSendMovementPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<DataTracker.SerializedEntry<?>>> entityTrackDataUpdate = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<ClientPlayerEntity> thisPlayerSpawnPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<PlayerListEntry> otherPlayerJoinPoint = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<PlayerListEntry> otherPlayerExitPoint = new ListenerPoint<>();



    @Getter
    //jump not because of toggle creative flight
    private static final ListenerPoint<Event<Integer>> playerNotFlyJumpPoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<MovTasks.MovInfo>> teleportConfirmResponsePoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Vec3d>> teleportConfirmVelocityUpdatePoint = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Integer>> useItemCooldownReset = new ListenerPoint<>();

    //movement
    @Getter
    private static final ListenerPoint<Event<Vec3d>> playerVelocityUpdate = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Input>> playerKeyboardInputTick = new ListenerPoint<>();



    public static boolean prepacketListenerApplyPoint(Packet<?> packet, PacketListener listener){
        if(!MinecraftClient.getInstance().isOnThread()){
            return false;
        }
        Event<Packet<?>> packetEvent = new Event<>(packet, true, false, listener);
        mainThreadPacketPreApplyPoint.handleValue(packetEvent);
        return packetEvent.isCancelled();
    }
    public static void postPacketListenerApplyPoint(Packet<?> packet, PacketListener listener){
        if(!MinecraftClient.getInstance().isOnThread()){
            return ;
        }
        Event<Packet<?>> packetEvent = new Event<>(packet, false, false, listener);
        mainThreadPacketPostApplyPoint.handleValue(packetEvent);
    }
//    @Getter
//    private static final ListenerPoint<Event<>>
    //trigger attack
    @Getter
    private static final ListenerPoint<Event<Void>> preTick = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Void>> postTick = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Void>> preHandleInput = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Void>> postHandleEvent = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Void>> triggerLeftClick = new ListenerPoint<>();
    //trigger use
    @Getter
    private static final ListenerPoint<Event<Hand>> triggerRightClick = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<BlockHitResult>> prePlayerUseItemAtBlock = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<BlockHitResult>> postPlayerUseItemAtBlock = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<PlayerInteractItemC2SPacket>> playerItemUsePacketCreate = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Point>> currentScreenResize = new ListenerPoint<>();

    public static boolean doItemUseAtBlockPre(Hand hand, BlockHitResult result){
        Event<BlockHitResult> event = new Event<>(result, true, false, hand);
         prePlayerUseItemAtBlock.handleValue(event);
         return !event.isCancelled();
    }
    public static void doItemUseAtBlockPost(Hand hand, BlockHitResult result){
        Event<BlockHitResult> event = new Event<>(result, false, false, hand);
        postPlayerUseItemAtBlock.handleValue(event);
    }
    @Getter
    private static final ListenerPoint<ClientPlayerEntity> playerInitConfiguration = new ListenerPoint<>();

    //with KeyCodes.java
    @Getter
    private static final ListenerPoint<Event<Integer>> KeyboardInputListener = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Integer>> mouseButtonListener = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Void>> mouseScrollListener = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Void>> mouseMoveListener = new ListenerPoint<>();
    @Getter
    private static final ListenerPoint<Event<Void>> mouseDragListener = new ListenerPoint<>();


    @Getter
    private static final ListenerPoint<Event<Character>> charTypedListener = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<IHotKey>> hotKeyTriggeredListener = new ListenerPoint<>();

    @Getter
    private static final ListenerPoint<Event<Screen>> postSetScreen = new ListenerPoint<>();

    static{
        //ConnectionListener.init();
    }
}
