package me.matl114.hacks.modules.ac;

import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.NetworkUtils;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class DisablerManager extends BaseModule {
    public static DisablerManager INSTANCE;
    public final EnumRef<SupportAC> currentAC = builder(
                    Configs.TEST_CONFIG, makePath("disablers.current-ac"), SupportAC.class)
            .defaultValue(SupportAC.NONE)
            .build();

    public final FlagRef grimFastBreak = builder(
                    Configs.TEST_CONFIG, makePath("disablers.grim-fast-break"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef grimSelfCheck = builder(
                    Configs.TEST_CONFIG, makePath("disablers.grim-self-check"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef grimMultiplace = builder(
                    Configs.TEST_CONFIG, makePath("disablers.grim-multi-place"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoFlushPlaceQueue = builder(
                    Configs.TEST_CONFIG, makePath("disablers.auto-flush-multi-place-queue"), Boolean.class)
            .defaultValue(true)
            .build();

    public DisablerManager() {
        INSTANCE = this;
    }

    boolean grimSelfCheckDisabler;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getServerLeavePoint(), this::onDisconnect);
        registerListener(Listener.getPacketPoint().getChannel(PlayerRespawnS2CPacket.class), this::onRespawn);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
        registerListener(Listener.getPostTick(), this::onTick);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onPlace,
                Integer.MAX_VALUE - 1);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onFlying);
        registerListener(Listener.getPacketPoint().getChannel(CommonPongC2SPacket.class), this::onPingPong);
    }

    public void onRespawn(Event<PlayerRespawnS2CPacket> respawn) {
        if (!grimSelfCheckDisabler) {
            grimSelfCheckDisabler = true;
        }
    }

    Direction lastDirection;
    Vec3d lastCursor;
    BlockPos lastPos;
    boolean hasPlaceThisTick;

    public boolean isGrimSelfCheckDisabled() {
        return currentAC.get() == SupportAC.GRIM && grimSelfCheck.get() && grimSelfCheckDisabler;
    }

    public boolean isMultiPlaceCheckDisabled() {
        return switch (currentAC.get()) {
            case GRIM -> isGrimMultiPlaceDisabled();
            case MATRIX -> false;
            default -> true;
        };
    }

    public boolean isMultiRotPlaceCheckDisabled() {
        return isMultiPlaceCheckDisabled() && isRotationPlaceCheckDisabled();
    }

    public boolean isRotationPlaceCheckDisabled() {
        return switch (currentAC.get()) {
            case GRIM -> isGrimSelfCheckDisabled();
            case MATRIX -> false;
            default -> true;
        };
    }

    public boolean isGrimMultiPlaceDisabled() {
        return currentAC.get() == SupportAC.GRIM && (grimMultiplace.get() || grimSelfCheckDisabler);
    }

    public void onDisconnect(Event<Void> eventDisconnect) {
        grimSelfCheckDisabler = false;
    }

    boolean hasAnyPlaceActionGrimQueue = false;

    public void flushACPlaceQueue() {
        switch (currentAC.get()) {
            case GRIM -> {
                // flush ghost blocks
                // see GrimAC handleQueuedPlaces()
                if (hasAnyPlaceActionGrimQueue) {
                    Listener.sendPacketNoEvents(new UpdateSelectedSlotC2SPacket(
                            mc.player.getInventory().getSelectedSlot()));
                }
                hasAnyPlaceActionGrimQueue = false;
            }
        }
    }

    public void onPlace(Event<PlayerInteractBlockC2SPacket> blockPlace) {
        BlockHitResult hitResult = blockPlace.context.getBlockHitResult();
        Direction direction = hitResult.getSide();
        Vec3d cursor = hitResult.getPos();
        BlockPos blockPos = hitResult.getBlockPos();
        if (hasAnyPlaceActionGrimQueue && autoFlushPlaceQueue.get()) {
            flushACPlaceQueue();
        }
        hasAnyPlaceActionGrimQueue = true;

        if (grimSelfCheckDisabler) {
            hasPlaceThisTick = false;
        }
        if (hasPlaceThisTick) {
            if (direction != lastDirection
                    || !Objects.equals(cursor, lastCursor)
                    || !Objects.equals(blockPos, lastPos)) {
                if (isGrimSelfCheckDisabled()) {
                    PlayerInteractBlockC2SPacket pkt = blockPlace.context;
                    PacketManager.schedulePostCallback(pkt, () -> {
                        Listener.sendPacketNoEvents(new PlayerInteractBlockC2SPacket(
                                pkt.getHand(), pkt.getBlockHitResult(), NetworkUtils.generateNextSequence()));
                    });
                }
            }
        }
        lastDirection = direction;
        lastCursor = cursor;
        lastPos = blockPos;
    }
    // see GrimAC handleQueuedPlaces
    public void onFlying(Event<PlayerMoveC2SPacket> playerMoveC2SPacket) {
        hasAnyPlaceActionGrimQueue = false;
    }

    public void onPingPong(Event<CommonPongC2SPacket> eventTransaction) {
        int id = eventTransaction.context.getParameter();
        if (id == (short) id) {
            // grimTransaction
            hasAnyPlaceActionGrimQueue = false;
        }
    }

    public void onTick(Event<Void> event) {
        hasPlaceThisTick = false;
    }

    public void onPresetReload(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                currentAC.set(SupportAC.GRIM);
            }
            case AC_MATRIX -> {
                currentAC.set(SupportAC.MATRIX);
            }
            default -> {
                currentAC.set(SupportAC.NONE);
            }
        }
    }

    public enum SupportAC implements ConfigEnum {
        NONE,
        GRIM,
        MATRIX;

        @Override
        public String getConfigEnumType() {
            return "support_disabler_ac";
        }

        @Override
        public Text getDisplay() {
            return Text.literal(this.name());
        }
    }
}
