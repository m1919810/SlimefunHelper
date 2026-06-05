package me.matl114.hacks.modules.extra;

import com.google.common.util.concurrent.Runnables;
import java.util.List;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.presets.choices.QuestionScreen;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

public class ClientExtra extends BaseModule {
    public static ClientExtra INSTANCE;

    public ClientExtra() {
        INSTANCE = this;
    }

    public final ModulePath other = makePath(Configs.TEST_CONFIG, "other");

    public final FlagRef noCrash = builder(other.add("no-client-crash"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef keepInServer =
            flagBuilder(other.add("client-crash-keep-in-server")).build();

    public final FlagRef noEntityCrash =
            flagBuilder(other.add("no-entity-crash")).build();

    public final FlagRef noBlockEntityCrash =
            flagBuilder(other.add("no-block-entity-crash")).build();

    public final FlagRef noNtwException = builder(other.add("no-disconnect-on-network-error"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef noDecodeException = builder(other.add("no-disconnect-on-packet-decode"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef noUnexpected = builder(other.add("no-disconnect-on-packet-unexpected"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef portalGui =
            flagBuilder(other.add("keep-gui-open-on-portal")).build();

    public final StringRef clientBrandName = builder(other.add("client-brand-name"), StringRef.TYPE)
            .defaultValue("")
            .build();

    public final KeyBindRef cursorSwitchKey = hotkey(
                    Configs.TEST_CONFIG, other.add("cursor-switch-hotkey").toPath())
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asHandler(this::onCursorLockSwitch))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getClientMainExit(), this::onCrash);
        registerListener(
                Listener.getExceptionListener().getChannel(Listener.ExceptionType.PACKET_HANDLE_EXCEPTION),
                this::onNetworkException);
        registerListener(
                Listener.getExceptionListener().getChannel(Listener.ExceptionType.ENTITY_TICK),
                this::onEntityException);
        registerListener(
                Listener.getExceptionListener().getChannel(Listener.ExceptionType.BLOCK_ENTITY_TICK),
                this::onBlockEntityException);
        registerListener(
                Listener.getExceptionListener().getChannel(Listener.ExceptionType.PACKET_DECODE_EXCEPTION),
                this::onDecodeException);

        registerListener(
                Listener.getExceptionListener().getChannel(Listener.ExceptionType.UNKNOWN_CHANNEL_EXCEPTION),
                this::onUnexpectedException);
    }

    private final Text questionCrash =
            Text.literal("你的游戏刚才因为未知原因崩溃,但是SlimefunHelper拦截了它").formatted(Formatting.RED);
    private final List<QuestionScreen.Solution> crashSolutions = List.of(
            QuestionScreen.Solution.of(Text.literal("我已知晓, 继续游戏").formatted(Formatting.GREEN), Runnables.doNothing()),
            QuestionScreen.Solution.of(Text.literal("我已知晓, 退出游戏").formatted(Formatting.RED), this::exitGame));

    private void exitGame() {
        mc.scheduleStop();
    }

    public void onCrash(Event<MinecraftClient> event) {
        if (event.canCancel() && event.context().isRunning() && noCrash.get()) {
            event.cancel();
            // must disconnect from server here
            QuestionScreen screen = new QuestionScreen(questionCrash, crashSolutions);
            checkClientData(screen);
        }
    }

    public void onNetworkException(Event<Listener.WrapperException> event) {
        if (noNtwException.get()) {
            Listener.WrapperException we = event.context();
            Packet<?> packet = event.getArgs(0);
            PacketListener listener = event.getArgs(1);
            Throwable exception = we.exception();
            if (mc.player != null) {
                Debug.chat(Text.literal("Error while handling a network packet: ")
                        .formatted(Formatting.RED)
                        .append(Text.literal(packet.getClass().getSimpleName())));
                Debug.chat(
                        exception.getClass().getSimpleName(),
                        ":",
                        Text.literal(exception.getMessage() == null ? "Exception: null" : exception.getMessage()));
            }
            Debug.info("Packet Exception INFO :");
            Debug.info("  PacketListener : ", listener);
            Debug.info("  Packet :", packet);
            Debug.info("Exception StackTrace:");
            Debug.info(exception);
            event.cancel();
        }
    }

    public void onEntityException(Event<Listener.WrapperException> event) {
        if (noEntityCrash.get()) {
            Listener.WrapperException we = event.context();
            Entity entity = event.getArgs(0);
            event.cancel();
            if (!entity.isRemoved()) {
                // try fix common issues:
                Throwable exception = we.exception();
                Debug.chat(
                        "Error while ticking entity:",
                        entity.getDisplayName(),
                        entity instanceof PlayerEntity player
                                ? "(%s)".formatted(player.getNameForScoreboard())
                                : "(%s)".formatted(Registries.ENTITY_TYPE.getId(entity.getType())));
                Debug.chat(
                        exception.getClass().getSimpleName(),
                        ":",
                        Text.literal(exception.getMessage() == null ? "Exception: null" : exception.getMessage()));
                // try fix common issues
                if (!validVec3d(entity.getPos())) {
                    Debug.chat("Invalid Position detected!");
                    entity.setPosition(Vec3d.ZERO);
                }
                if (!validVec3d(entity.getVelocity())) {
                    Debug.chat("Invalid Velocity detected!");
                    entity.setVelocity(Vec3d.ZERO);
                }
                if (!Double.isFinite(entity.getPitch()) || !Double.isFinite(entity.getYaw())) {
                    Debug.chat("Invalid Rotation detected!");
                    entity.setPitch(0);
                    entity.setYaw(0);
                }
                Debug.info("Entity Exception INFO :");
                Debug.info("  Entity : ", entity);
                try {
                    Debug.info("  EntityNBT : ", VEntity.saveEntityNbt(entity));
                } catch (Throwable e) {
                }
                Debug.info("Exception StackTrace:");
                Debug.info(exception);
            }
        }
    }

    public void onBlockEntityException(Event<Listener.WrapperException> event) {
        if (noBlockEntityCrash.get()) {
            Listener.WrapperException we = event.context();
            BlockEntityTickInvoker entity = event.getArgs(0);
            World world = event.getArgs(1);
            event.cancel();
            if (!entity.isRemoved()) {
                Throwable exception = we.exception();
                Debug.chat(
                        "Error while ticking blockEntity at world:",
                        ChatUtils.getDisplayedLocation(Vec3d.of(entity.getPos())),
                        "World:",
                        world.getRegistryKey().getValue());
                Debug.chat(
                        exception.getClass().getSimpleName(),
                        ":",
                        Text.literal(exception.getMessage() == null ? "Exception: null" : exception.getMessage()));
                Debug.info("BlockEntity Exception INFO :");
                Debug.info("  World : ", world.getRegistryKey().getValue());
                Debug.info("  BlockEntityPos : ", entity);
                try {
                    BlockEntity be = world.getBlockEntity(entity.getPos());
                    Debug.info(" BlockEntity : ", be == null ? null : be.getType());
                    if (be != null) {
                        Debug.info(" BlockEntityNBT : ", be.createNbt(ItemStackUtils.registry()));
                    }
                    BlockState state = world.getBlockState(entity.getPos());
                    Debug.info(" BlockState : ", state);
                } catch (Throwable e) {
                }
                Debug.info("Exception StackTrace:");
                Debug.info(exception);
            }
        }
    }

    public void onDecodeException(Event<Listener.WrapperException> event) {
        if (noDecodeException.get()) {
            Listener.WrapperException we = event.context();
            PacketListener packet = event.getArgs(0);
            Throwable exception = we.exception();
            if (packet instanceof ClientPlayPacketListener playListener) {
                if (mc.player != null) {
                    Debug.chat(Text.literal("Error while decoding packet: ").formatted(Formatting.RED));
                    Debug.chat(
                            exception.getClass().getSimpleName(),
                            ":",
                            Text.literal(exception.getMessage() == null ? "Exception: null" : exception.getMessage()));
                }
                Debug.info("Exception StackTrace:");
                Debug.info(exception);
                event.cancel();
            }
        }
    }

    public void onUnexpectedException(Event<Listener.WrapperException> event) {
        if (noUnexpected.get()) {
            Listener.WrapperException we = event.context();
            PacketListener packet = event.getArgs(0);
            Throwable exception = we.exception();
            if (packet instanceof ClientPlayPacketListener playListener) {
                if (mc.player != null) {
                    Debug.chat(Text.literal("Error while receiving packet: ").formatted(Formatting.RED));
                    Debug.chat(
                            exception.getClass().getSimpleName(),
                            ":",
                            Text.literal(exception.getMessage() == null ? "Exception: null" : exception.getMessage()));
                }
                Debug.info("Exception StackTrace:");
                Debug.info(exception);
                event.cancel();
            }
        }
    }

    public boolean validVec3d(Vec3d vec3d) {
        return Double.isFinite(vec3d.x) && Double.isFinite(vec3d.y) && Double.isFinite(vec3d.z);
    }

    protected void checkClientData(Screen screen) {
        ScreenAccess currentScreen = ScreenAccess.of(mc.currentScreen);
        Screen parentScreen = (currentScreen instanceof QuestionScreen ? currentScreen.getParent() : mc.currentScreen);
        if (keepInServer.get()
                && mc.player != null
                && mc.world != null
                && mc.inGameHud != null
                && mc.getNetworkHandler() != null
                && mc.interactionManager != null) {
            ScreenAccess.of(screen).openFrom(parentScreen);
        } else {
            // 严重问题
            MainTasks.disconnectImmediately();
            ScreenAccess.of(screen).openFrom(parentScreen);
        }
    }

    public void onCursorLockSwitch() {
        if (mc.mouse != null) {
            if (mc.mouse.isCursorLocked()) {
                mc.mouse.unlockCursor();
            } else {
                mc.mouse.lockCursor();
            }
        }
    }
}
