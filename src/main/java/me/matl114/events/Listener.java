package me.matl114.events;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.*;
import lombok.Getter;
import me.matl114.accessors.events.ClientConnectionAccess;
import me.matl114.events.annotations.*;
import me.matl114.events.catchers.AbstractTypedPacketCatcher;
import me.matl114.events.catchers.PacketCatcher;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.channels.EventChannelDispatcher;
import me.matl114.events.channels.PacketEventChannel;
import me.matl114.hacks.MovTasks;
import me.matl114.managers.Tasks;
import me.matl114.managers.input.IHotKey;
import me.matl114.managers.input.IInputManager;
import me.matl114.utils.collections.FPoint;
import me.matl114.utils.collections.Point;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.listener.ClientCookieRequestPacketListener;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.packet.*;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.config.FeaturesS2CPacket;
import net.minecraft.network.packet.s2c.config.ResetChatS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkSentS2CPacket;
import net.minecraft.network.packet.s2c.play.StartChunkSendS2CPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Unique;

public class Listener {
    public static void init() {}

    @Getter
    private static final Map<PacketType<?>, Class<? extends Packet<?>>> registeredPacketTypes = new LinkedHashMap<>();

    private static final Map<Identifier, PacketType<?>> c2sPacketTypes = new HashMap<>();
    private static final Map<Identifier, PacketType<?>> s2cPacketTypes = new HashMap<>();

    private static void registerPacketTypesInternal(Class<?> clazz) {
        for (var field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && PacketType.class.isAssignableFrom(field.getType())) {
                try {
                    PacketType<?> typeInstance = (PacketType<?>) field.get(null);
                    if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
                        Class<? extends Packet<?>> packetClass =
                                (Class<? extends Packet<?>>) parameterizedType.getActualTypeArguments()[0];
                        registeredPacketTypes.put((PacketType<?>) typeInstance, packetClass);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static {
        // register PlayPackets
        registerPacketTypesInternal(CommonPackets.class);
        registerPacketTypesInternal(PlayPackets.class);
        registerPacketTypesInternal(LoginPackets.class);
        registerPacketTypesInternal(PingPackets.class);
        registerPacketTypesInternal(StatusPackets.class);
        registerPacketTypesInternal(HandshakePackets.class);
        registerPacketTypesInternal(ConfigPackets.class);
        registerPacketTypesInternal(CookiePackets.class);
        for (var packetType : registeredPacketTypes.keySet()) {
            if (packetType.side() == NetworkSide.SERVERBOUND) {
                c2sPacketTypes.put(packetType.id(), packetType);
            } else {
                s2cPacketTypes.put(packetType.id(), packetType);
            }
        }
    }

    public static Class<? extends Packet<?>> getPacketClassById(Identifier id, boolean s2c) {
        return registeredPacketTypes.entrySet().stream()
                .filter(type -> Objects.equals(type.getKey().id(), id)
                        && type.getKey().side() == (s2c ? NetworkSide.CLIENTBOUND : NetworkSide.SERVERBOUND))
                .findAny()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public static PacketType<?> getPacketTypeById(Identifier id, boolean s2c) {
        return (s2c ? s2cPacketTypes : c2sPacketTypes).get(id);
    }

    public static <T extends Packet<?>> EventChannel<T> getPacketListenerPoint(Class<T> clazz) {
        return packetPoint.getChannel(clazz);
    }
    //    private static final Map<Class<?>, CatcherPoint<Event<Packet<?>>>> packetCatcher = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Packet<?>>, Class<? extends Packet<?>>> mappedPacketClass =
            new ConcurrentHashMap<>();

    public static <T extends Packet<?>, W extends Packet<?>> Class<W> getMappedPacketClass(Class<T> packet) {
        return (Class<W>)
                mappedPacketClass.computeIfAbsent((Class<? extends Packet<?>>) packet, Listener::getPacketClass);
    }

    private static <T extends Packet<?>, W extends Packet<?>> Class<W> getPacketClass(Class<T> packet) {
        Class<?> clazz1 = packet;
        while (Packet.class.isAssignableFrom(clazz1.getSuperclass())) {
            clazz1 = clazz1.getSuperclass();
        }
        return (Class<W>) clazz1;
    }

    protected static <T extends Packet<?>> Consumer<Event<T>> wrapListener(Predicate<T> w) {
        return (packetEvent -> {
            if (packetEvent.isCancelled()) {
                return;
            }
            boolean how = w.test(packetEvent.context());
            if (!how) {
                packetEvent.cancel();
            }
        });
    }

    protected static <T extends Packet<?>> Consumer<Event<T>> wrapListener(BiPredicate<ClientConnection, T> w) {
        return (packetEvent -> {
            if (packetEvent.isCancelled()) {
                return;
            }
            boolean how = w.test(packetEvent.getArgs(0), packetEvent.context());
            if (!how) {
                packetEvent.cancel();
            }
        });
    }

    protected static <T> Consumer<Event<T>> wrapListener(Consumer<T> w) {
        return (packetEvent -> {
            if (packetEvent.isCancelled()) {
                return;
            }
            w.accept(packetEvent.context());
        });
    }

    public static void registerPacketListener(Consumer<Packet<?>> packetListener, boolean isS2C) {
        if (isS2C) {
            getPacketAcceptPoint().registerHandler(wrapListener(packetListener));
        } else {
            getPacketSendPoint().registerHandler(wrapListener(packetListener));
        }
    }

    public static void registerPacketListener(Predicate<Packet<?>> packetListener, boolean isS2C) {
        if (isS2C) {
            getPacketAcceptPoint().registerHandler(wrapListener(packetListener));
        } else {
            getPacketSendPoint().registerHandler(wrapListener(packetListener));
        }
    }

    public static void registerPacketListener(BiPredicate<ClientConnection, Packet<?>> packetListener, boolean isS2C) {
        if (isS2C) {
            getPacketAcceptPoint().registerHandler(wrapListener(packetListener));
        } else {
            getPacketSendPoint().registerHandler(wrapListener(packetListener));
        }
    }

    public static <T extends Packet<?>> void registerSinglePacketListener(Class<T> clazz, Consumer<T> predicate) {
        getPacketListenerPoint(clazz).registerHandler(wrapListener(predicate));
    }

    public static <T extends Packet<?>> void registerSinglePacketListener(Class<T> clazz, Predicate<T> predicate) {
        getPacketListenerPoint(clazz).registerHandler(wrapListener(predicate));
    }

    public static <T extends Packet<?>> void registerSinglePacketListener(
            Class<T> clazz, BiPredicate<ClientConnection, T> predicate) {
        getPacketListenerPoint(clazz).registerHandler(wrapListener(predicate));
    }

    @Getter
    public static ClientConnection clientConnection;

    public static ClientConnectionAccess getConnectionAccess() {
        return ClientConnectionAccess.of(clientConnection);
    }

    public static Packet<?> acceptS2CPacket(ClientConnection connection, Packet<?> packet) {

        return unpackMultiPacket(connection, packet, true);
    }

    public static Packet<?> sendC2SPacket(ClientConnection connection, Packet<?> packet) {
        return unpackMultiPacket(connection, packet, false);
    }

    @Unique
    private static Packet<?> onSinglePacketListen(ClientConnection connection, Packet<?> packet, boolean s2c) {
        Event<Packet<?>> packetEvent = new Event<>(packet, true, true, connection);
        // already handled in PacketEventChannel
        //        if (s2c) {
        //            getPacketAcceptPoint().handleValue(packetEvent);
        //        } else {
        //            getPacketSendPoint().handleValue(packetEvent);
        //        }
        getPacketPoint().handleValue(packetEvent);
        if (packetEvent.isCancelled()) {
            return null;
        } else {
            return packetEvent.context();
        }
    }

    @Unique
    private static Packet<?> unpackMultiPacket(ClientConnection connection, Packet<?> packet, boolean isS2C) {
        if (packet instanceof BundleS2CPacket bundle) {
            var iter = bundle.getPackets();
            List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
            boolean recreate = false;
            for (var pkt : iter) {
                Packet<? super ClientPlayPacketListener> p =
                        (Packet<? super ClientPlayPacketListener>) unpackMultiPacket(connection, pkt, isS2C);
                if (p != null) {
                    packets.add(p);
                    if (p != pkt) {
                        recreate = true;
                    }
                } else {
                    recreate = true;
                }
            }
            if (recreate) {
                return packets.isEmpty() ? null : new BundleS2CPacket(packets);
            } else {
                return packet;
            }
        } else {
            return onSinglePacketListen(connection, packet, isS2C);
        }
    }

    private static final Function<Class<? extends Screen>, Class<? extends Screen>> screenClassIdentifierMapper =
            Util.memoize((clz -> {
                Class<?> clzz = clz;
                while (clzz != Screen.class && clzz.getSuperclass() != Screen.class) {
                    clzz = clzz.getSuperclass();
                }
                return (Class<? extends Screen>) clzz;
            }));

    // basic events
    @Getter
    @Broadcast
    private static final EventChannel<ClientPlayerEntity> gameJoinPoint = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<World> worldSwitchPoint = new EventChannel<>();
    // disconnect or enter reconfiguration
    @Getter
    @Broadcast
    @ExtraArgs({boolean.class}) // whether disconnect or not
    private static final EventChannel<Void> serverLeavePoint = new EventChannel<>();
    // disconnect from server
    @Getter
    @Broadcast
    @ExtraArgs({boolean.class}) // whether transferring
    private static final EventChannel<Void> serverDisconnectPoint = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    @ExtraArgs({ServerInfo.class})
    private static final EventChannel<ServerAddress> serverPreConnectPoint = new EventChannel<>();

    @Getter // cancelable
    @Broadcast
    private static final EventChannel<Void> preTick = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<Void> postTick = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<ClientPlayerEntity> preGameTick = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<ClientPlayerEntity> postGameTick = new EventChannel<>();

    @Getter // arguments RenderTickCounter, tick , cancelable
    @Cancelable
    @ExtraArgs({RenderTickCounter.class, boolean.class})
    private static final EventChannel<GameRenderer> gameRender = new EventChannel<>();

    @Getter
    @ApiStatus.Experimental
    @Broadcast
    private static final EventChannel<Language> languageReload = new EventChannel<>();

    @Getter
    @Cancelable(optional = true)
    private static final EventChannel<MinecraftClient> clientMainExit = new EventChannel<>();

    // chat events
    @Getter // cancelable, modifiable
    @Cancelable
    @Modifiable
    private static final EventChannel<String> chatSend = new EventChannel<>();

    @Getter // cancelable, modifiable
    @Cancelable
    @Modifiable
    @ExtraArgs({MessageSignatureData.class, MessageIndicator.class})
    private static final EventChannel<Text> messageAddToHud = new EventChannel<>();

    @Getter // cancelable, modifiable
    @Cancelable
    @Modifiable
    private static final EventChannel<ChatHudLine> messageAddToVisible = new EventChannel<>();

    @Getter // cancelable, modifiable
    @Cancelable
    @Modifiable
    private static final EventChannel<String> chatScreenSendMessage = new EventChannel<>();

    // screen events
    @Getter
    @Broadcast
    private static final EventChannelDispatcher<Screen> postCloseScreen = new EventChannelDispatcher<>(
            screen -> screen == null ? Screen.class : screenClassIdentifierMapper.apply(screen.getClass()));

    @Getter
    @Cancelable
    @Modifiable
    private static final EventChannelDispatcher<Screen> preSetScreen = new EventChannelDispatcher<>(
            screen -> screen == null ? Screen.class : screenClassIdentifierMapper.apply(screen.getClass()));

    @Getter
    @Cancelable // note: this cancels post operations of setting a screen , like cursor lock, render refresh and title
    // update
    private static final EventChannelDispatcher<Screen> midSetScreen = new EventChannelDispatcher<>(
            screen -> screen == null ? Screen.class : screenClassIdentifierMapper.apply(screen.getClass()));

    @Getter
    @Broadcast
    // update
    private static final EventChannelDispatcher<Screen> postSetScreen = new EventChannelDispatcher<>(
            screen -> screen == null ? Screen.class : screenClassIdentifierMapper.apply(screen.getClass()));

    @Getter
    @Broadcast
    private static final EventChannel<HandledScreen<?>> postOpenHandledScreen = new EventChannel<>();

    @Getter
    @Broadcast //  note: this is called when a screen open for the first time, or change its size. most screen clear
    // their children after change, but not all of them
    private static final EventChannelDispatcher<Screen> postInitializeScreen = new EventChannelDispatcher<>(
            screen -> screen == null ? Screen.class : screenClassIdentifierMapper.apply(screen.getClass()));

    @Getter // stores argument of the RecipeBook
    @Broadcast
    @ExtraArgs({RecipeBookWidget.class, ButtonWidget.class})
    private static final EventChannel<RecipeBookProvider> postToggleRecipeBook = new EventChannel<>();

    @Getter
    @Cancelable
    @ExtraArgs({int.class, int.class, int.class})
    private static final EventChannelDispatcher<SlotActionType> preClickSlot =
            new EventChannelDispatcher<>(Function.identity());

    @Getter
    @Broadcast
    @ExtraArgs({int.class, int.class, int.class})
    private static final EventChannelDispatcher<SlotActionType> postClickSlot =
            new EventChannelDispatcher<>(Function.identity());

    @Getter
    @Broadcast
    private static final EventChannel<RecipeEntry<?>> clickCraftingRecipe = new EventChannel<>();

    // packet events
    @Cancelable
    @ExtraArgs({ClientConnection.class})
    public static EventChannel<Packet<?>> getPacketAcceptPoint() {
        return packetPoint.getPacketReceiveChannel();
    }

    @Cancelable
    @ExtraArgs({ClientConnection.class})
    public static EventChannel<Packet<?>> getPacketSendPoint() {
        return packetPoint.getPacketSendChannel();
    }

    @Getter
    @ExtraArgs({ClientConnection.class})
    private static final PacketEventChannel packetPostSendPoint = new PacketEventChannel();

    @Getter // packet accept or send
    @Cancelable
    @Modifiable
    @ExtraArgs({ClientConnection.class})
    @Dispatch(by = "type and side")
    private static final PacketEventChannel packetPoint = new PacketEventChannel();

    @Getter // packet being handled on MainThread
    @Cancelable
    @ExtraArgs({PacketListener.class})
    @Dispatch(by = "type")
    private static final PacketEventChannel packetPreHandlePoint = new PacketEventChannel();

    @Getter
    @Broadcast
    @ExtraArgs({PacketListener.class})
    @Dispatch(by = "type")
    private static final PacketEventChannel packetPostHandlePoint = new PacketEventChannel();

    //    @Getter // network exception
    //    @Cancelable
    //    @ExtraArgs({PacketListener.class, Exception.class})
    //    private static final EventChannel<Packet<?>> packetListenerException = new EventChannel<>();

    // client player behaviours
    @Getter
    @Cancelable
    private static final EventChannel<ClientPlayerEntity> clientPlayerSendMovementPoint = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    @ApiStatus.Experimental
    @Dispatch(by = "Entity.getType")
    @ExtraArgs({Entity.class})
    private static final EventChannelDispatcher<DataTracker.SerializedEntry<?>> entityTrackDataUpdate =
            new EventChannelDispatcher<>(e -> e.<Entity>getArgs(0).getType(), true);

    @Getter
    @Broadcast
    private static final EventChannel<ClientPlayerEntity> thisPlayerSpawnPoint = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    // jump not because of toggle creative flight
    private static final EventChannel<Integer> playerNotFlyJumpPoint = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<ClientPlayerEntity> playerLandingPoint = new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs({BlockPos.class})
    private static final EventChannel<Vec3d> playerWebSlowPoint = new EventChannel<>();

    @Getter
    @Modifiable
    private static final EventChannel<MovTasks.MovInfo> teleportConfirmResponsePoint = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    private static final EventChannel<Integer> useItemCooldownReset = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    private static final EventChannel<Vec3d> playerVelocityTick = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<Input> playerKeyboardInputTick = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<ClientPlayerEntity> playerInitConfiguration = new EventChannel<>();

    @Getter
    @Modifiable
    @Cancelable
    private static final EventChannel<Integer> playerFallFlyingTick = new EventChannel<>();

    @Getter
    @Modifiable
    @Cancelable // records whether a Elytra flying should be started, every condition is considered, you can use this
    // event to also stop fallFlying
    @ExtraArgs(
            value = {Boolean.class},
            names = {"currentFallFlying"})
    private static final EventChannel<Boolean> playerSwitchFallFlying = new EventChannel<>();

    @Getter
    @Cancelable
    private static final EventChannel<Vec3d> playerTravelingTick = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    private static final EventChannel<FPoint> playerChangeLook = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    private static final EventChannel<Vec3d> playerExplosionVelocity = new EventChannel<>();

    // entities
    @Getter
    @Broadcast
    private static final EventChannel<PlayerListEntry> otherPlayerJoinPoint = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<PlayerListEntry> otherPlayerExitPoint = new EventChannel<>();

    @Getter // vc update
    @Cancelable
    @Modifiable
    @ExtraArgs({Entity.class})
    private static final EventChannelDispatcher<Vec3d> entityClientVelocityUpdate =
            new EventChannelDispatcher<>(event -> event.<Entity>getArgs(0).getType(), true);

    @Getter
    @Cancelable
    private static final EventChannelDispatcher<Entity> entityPreTickListener =
            new EventChannelDispatcher<>(Entity::getType);

    @Getter
    @Broadcast
    private static final EventChannelDispatcher<Entity> entityMidTickListener =
            new EventChannelDispatcher<>(Entity::getType);

    @Getter
    @Broadcast
    private static final EventChannelDispatcher<Entity> entityPostTickListener =
            new EventChannelDispatcher<>(Entity::getType);

    @Getter
    @Broadcast
    @ExtraArgs(
            value = {List.class},
            names = {"updatedEntry"})
    private static final EventChannelDispatcher<Entity> entityDataListener =
            new EventChannelDispatcher<>(Entity::getType);

    @Getter
    @Cancelable
    @Modifiable
    @ExtraArgs(value = {EntityType.class})
    private static final EventChannelDispatcher<Entity> entityCreateListener =
            new EventChannelDispatcher<>((event -> event.getArgs(0)), true);

    @Getter
    @Cancelable
    private static final EventChannelDispatcher<Entity> serverEntitySpawnListener =
            new EventChannelDispatcher<>(Entity::getType);

    @Getter
    @Broadcast
    @ExtraArgs(value = {Entity.RemovalReason.class})
    private static final EventChannelDispatcher<Entity> entityRemoveListener =
            new EventChannelDispatcher<>(Entity::getType);

    // world events

    @Getter
    @Cancelable
    private static final EventChannel<BlockEntityTickInvoker> blockEntityTickListener = new EventChannel<>();

    @Getter
    @Modifiable
    private static final EventChannel<Boolean> preWorldScannListener = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<Void> resetWorldScannListener = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<List<BiPredicate<BlockPos, BlockState>>> worldScannChunkBlockFilterList =
            new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs({BlockPos.class, ChunkPos.class})
    private static final EventChannelDispatcher<BlockState> worldScannBlockResult =
            new EventChannelDispatcher<>(AbstractBlock.AbstractBlockState::getBlock);

    @Getter
    @Broadcast
    @ExtraArgs({ChunkPos.class})
    private static final EventChannel<Map<BlockPos, BlockState>> worldScannChunkResult = new EventChannel<>();

    // client interactions and attacks
    @Getter // handle player uses and attacks
    @Cancelable
    private static final EventChannel<Void> preHandleInputEvents = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<Void> postHandleInputEvents = new EventChannel<>();

    @Getter // player interact at block
    @Cancelable
    private static final EventChannel<BlockHitResult> prePlayerUseItemAtBlock = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<BlockHitResult> postPlayerUseItemAtBlock = new EventChannel<>();

    @Getter // player attack at block
    @Cancelable
    @Modifiable
    private static final EventChannel<HitResult> mineBlockAction = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    private static final EventChannel<HitResult> attackAction = new EventChannel<>();

    @Getter
    @Cancelable
    @Modifiable
    @ExtraArgs(value = {Hand.class})
    private static final EventChannel<HitResult> itemUseAction = new EventChannel<>();

    // client behaviours with the computer
    @Getter // the window size change
    @Broadcast
    private static final EventChannel<Point> resolutionChange = new EventChannel<>();

    @Getter // glfw events
    @Cancelable
    @ExtraArgs(
            value = {int.class, int.class, int.class, int.class},
            names = {"keyCode", "scanCode", "action", "modifiers"})
    private static final EventChannel<Keyboard> KeyboardInput = new EventChannel<>();

    @Getter
    @Cancelable
    @ExtraArgs(
            value = {int.class, int.class, int.class},
            names = {"eventButton", "action", "mode"})
    private static final EventChannel<Mouse> mouseButton = new EventChannel<>();

    @Getter
    @Cancelable
    @ExtraArgs(
            value = {double.class, double.class},
            names = {"horizontal", "vertical"})
    private static final EventChannel<Mouse> mouseScroll = new EventChannel<>();

    @Getter
    @Cancelable
    @ExtraArgs(
            value = {double.class, double.class},
            names = {"mouseX", "mouseY"})
    private static final EventChannel<Mouse> mouseMove = new EventChannel<>();

    @Getter
    @Cancelable
    @ExtraArgs(
            value = {double.class, double.class, double.class, double.class},
            names = {"mouseX", "mouseY", "deltaX", "deltaY"})
    private static final EventChannel<Mouse> mouseDrag = new EventChannel<>();

    @Getter
    @Cancelable
    @ExtraArgs(
            value = {int.class, int.class},
            names = {"codepoint", "modifiers"})
    private static final EventChannel<Character> charTyped = new EventChannel<>();

    @Getter // multiKeybind driven by glfw
    @Cancelable
    @ExtraArgs({IInputManager.class})
    private static final EventChannel<IHotKey> hotKeyTriggeredListener = new EventChannel<>();

    // exceptions
    @Getter
    @Cancelable
    @Dispatch(by = "WrapperException.type")
    private static final EventChannelDispatcher<WrapperException> exceptionListener =
            new EventChannelDispatcher<>(WrapperException::type);

    // custom event channel, where you can place all sort of things here
    @Getter
    @Cancelable(optional = true)
    @Modifiable(optional = true)
    @Dispatch(by = "EventContainer.getType")
    private static final EventChannelDispatcher<EventContainer<?>> customListener =
            new EventChannelDispatcher<>(EventContainer::getType);

    // network event

    @Getter
    @Broadcast
    @ExtraArgs({NetworkSide.class, Boolean.class})
    private static final EventChannel<ChannelPipeline> connectionChannelInitialize = new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs({NetworkSide.class, PacketListener.class})
    private static final EventChannel<ClientConnection> connectionEstablish = new EventChannel<>();

    private static final Set<Class<?>> asyncPackets = ImmutableSet.<Class<?>>builder()
            .add(CustomPayloadS2CPacket.class)
            .add(StartChunkSendS2CPacket.class)
            .add(ChunkSentS2CPacket.class)
            .add(PingResultS2CPacket.class)
            .add(DisconnectS2CPacket.class)
            .add(ResetChatS2CPacket.class)
            .add(FeaturesS2CPacket.class)
            .build();

    public static boolean isAsyncImportantPacket(Packet<?> packet) {
        return asyncPackets.contains(packet.getClass());
    }

    public static void callPacketHandleEvent(
            Packet<?> instance, PacketListener t, BiConsumer<Packet<?>, PacketListener> callback) {
        if (!Listener.prepacketListenerApplyPoint(instance, t)) {
            try {
                callback.accept(instance, t);
            } catch (OffThreadException e) {
                // off thread, maybe a mistake
            } catch (RejectedExecutionException | ClassCastException e) {
                throw e;
            } catch (Throwable e) {
                if (e instanceof CrashException crashException
                        && crashException.getCause() instanceof OutOfMemoryError) {
                    throw e;
                }
                if (handleException(e, ExceptionType.PACKET_HANDLE_EXCEPTION, instance, t)) {
                    throw e;
                }
            } finally {
                Listener.postPacketListenerApplyPoint(instance, t);
            }
        }
    }

    public static boolean prepacketListenerApplyPoint(Packet<?> packet, PacketListener listener) {
        // most handle are on Thread, some are not
        if (!MinecraftClient.getInstance().isOnThread()) {
            return false;
        }
        Event<Packet<?>> packetEvent = new Event<>(packet, true, false, listener);
        packetPreHandlePoint.handleValue(packetEvent);
        return packetEvent.isCancelled();
    }

    public static void postPacketListenerApplyPoint(Packet<?> packet, PacketListener listener) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            return;
        }
        Event<Packet<?>> packetEvent = new Event<>(packet, false, false, listener);
        packetPostHandlePoint.handleValue(packetEvent);
    }

    private static final Map<Class<?>, ArrayDeque<PacketCatcher>> preCatchers = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ArrayDeque<PacketCatcher>> postCatchers = new ConcurrentHashMap<>();

    public static <T extends Packet<?>> void addPrePacketCatcher(PacketCatcher packet) {
        Class<?> dequeCls =
                packet instanceof AbstractTypedPacketCatcher abstractType ? abstractType.packetClass : Packet.class;
        var re = preCatchers.computeIfAbsent(dequeCls, k -> new ArrayDeque<>());
        synchronized (re) {
            re.addLast(packet);
        }
    }

    public static <T extends Packet<?>> void addPostPacketCatcher(PacketCatcher packet) {
        Class<?> dequeCls =
                packet instanceof AbstractTypedPacketCatcher abstractType ? abstractType.packetClass : Packet.class;
        var re = postCatchers.computeIfAbsent(dequeCls, k -> new ArrayDeque<>());
        synchronized (re) {
            re.addLast(packet);
        }
    }

    public static void onPacketEventCatch(
            Map<Class<?>, ArrayDeque<PacketCatcher>> packetCatchers, Event<? extends Packet<?>> packet) {
        Packet<?> pkt = packet.context();
        ArrayDeque<PacketCatcher> re = packetCatchers.get(Packet.class);
        if (re != null) {
            onPacketCatcherArrayWalk(re, packet);
        }
        if (packet.isCancelled()) return;
        ArrayDeque<PacketCatcher> re2 = packetCatchers.get(Listener.getMappedPacketClass(pkt.getClass()));
        if (re2 != null) {
            onPacketCatcherArrayWalk(re2, packet);
        }
    }

    private static void onPacketCatcherArrayWalk(ArrayDeque<PacketCatcher> re, Event<? extends Packet<?>> packet) {
        if (packet.isCancelled()) return;
        synchronized (re) {
            var iter = re.iterator();
            while (iter.hasNext()) {
                var handler = iter.next();
                boolean removal = handler.catchEvent(packet);
                if (removal) {
                    iter.remove();
                }
                if (packet.isCancelled()) {
                    return;
                }
            }
        }
    }

    public static void sendPacketNoEvents(Packet<?> packet) {
        var re = MinecraftClient.getInstance().getNetworkHandler();
        if (re != null) {
            sendPacketNoEvents(re.getConnection(), packet);
        }
    }

    // make a method to send packet without event
    public static void sendPacketNoEvents(ClientConnection connection, Packet<?> packet) {
        connection.submit((con) -> {
            Channel channel = con.channel;
            if (channel.eventLoop().inEventLoop()) {
                sendInternal(channel, packet);
            } else {
                channel.eventLoop().execute(() -> {
                    sendInternal(channel, packet);
                });
            }
        });
    }

    private static void sendInternal(Channel channel, Packet<?> packet) {
        ChannelFuture channelFuture = channel.writeAndFlush(packet);
        channelFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public static boolean doItemUseAtBlockPre(Hand hand, BlockHitResult result) {
        Event<BlockHitResult> event = new Event<>(result, true, false, hand);
        prePlayerUseItemAtBlock.handleValue(event);
        return !event.isCancelled();
    }

    public static void doItemUseAtBlockPost(Hand hand, BlockHitResult result) {
        Event<BlockHitResult> event = new Event<>(result, false, false, hand);
        postPlayerUseItemAtBlock.handleValue(event);
    }

    public static void onClientConnectionEstablish(Event<ClientConnection> event) {
        if (event.getArgs(0) == NetworkSide.CLIENTBOUND
                && event.getArgs(1) instanceof ClientCookieRequestPacketListener) {
            clientConnection = event.context;
            Tasks.scheduleRepeated(
                    () -> {
                        // after the connection
                        if (clientConnection != null
                                && clientConnection.isChannelAbsent()
                                && !clientConnection.isOpen()) {
                            clientConnection = null;
                            return true;
                        }
                        return false;
                    },
                    20,
                    20);
        }
    }

    static {
        Listener.getPacketPreHandlePoint()
                .registerHandler((Consumer<Event<Packet<?>>>) ev -> onPacketEventCatch(preCatchers, ev));
        Listener.getPacketSendPoint()
                .registerHandler((Consumer<Event<Packet<?>>>) ev -> onPacketEventCatch(preCatchers, ev));

        Listener.getPacketPostHandlePoint()
                .registerHandler((Consumer<Event<Packet<?>>>) ev -> onPacketEventCatch(postCatchers, ev));
        Listener.getPacketPostSendPoint()
                .registerHandler((Consumer<Event<Packet<?>>>) ev -> onPacketEventCatch(postCatchers, ev));
        Listener.getConnectionEstablish()
                .registerHandler((Consumer<Event<ClientConnection>>) Listener::onClientConnectionEstablish);
    }

    public static boolean handleException(Throwable e, ExceptionType type, Object... objects) {
        Event<WrapperException> event = new Event<>(new WrapperException(type, e), true, false, objects);
        getExceptionListener().handleValue(event);
        if (event.isCancelled()) {
            return false;
        } else {
            return true;
        }
    }

    public static record WrapperException(ExceptionType type, Throwable exception) {}

    public static enum ExceptionType {
        PACKET_HANDLE_EXCEPTION,
        PACKET_DECODE_EXCEPTION,
        UNKNOWN_CHANNEL_EXCEPTION,
        CLIENT_CRASH,
        ENTITY_TICK,
        BLOCK_ENTITY_TICK,
        UNKNOWN;
    }
}
