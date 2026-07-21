package me.matl114.hacks.modules.ac;

import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.NetworkUtils;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
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

    public final ModulePath disablers = makePath(Configs.TEST_CONFIG, "disablers");

    public final FlagRef enable =
            builder(disablers.addEnable(), Boolean.class).defaultValue(true).build();

    public final KeyBindRef hotkey = moduleEntry(
                    disablers.addHotkey(), new MultiKeyBind(), disablers.addEnable(), moduleMeta(() -> this.currentAC))
            .build();

    public final EnumRef<SupportAC> currentAC = builder(disablers.add("current-ac"), SupportAC.class)
            .defaultValue(SupportAC.NONE)
            .build();

    public final FlagRef grimSelfCheck = builder(disablers.add("grim-self-check"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef grimMultiplace = builder(disablers.add("grim-multi-place"), Boolean.class)
            .defaultValue(true)
            .build();

    //    public final FlagRef grimMultiBreak = builder(disablers.add("grim-multi-break"), Boolean.class)
    //            .defaultValue(false)
    //            .build();

    public final FlagRef autoFlushPlaceQueue = builder(disablers.add("auto-flush-multi-place-queue"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoFlushPlaceBreakQueue = builder(
                    disablers.add("auto-flush-place-break-queue"), Boolean.class)
            .defaultValue(true)
            .build();

    public DisablerManager() {
        super("Disabler");
        INSTANCE = this;
        bindFlag(enable);
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
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class),
                this::onBreakAction,
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
        return enable.get() && currentAC.get() == SupportAC.GRIM && grimSelfCheck.get() && grimSelfCheckDisabler;
    }

    public boolean isMultiPlaceCheckDisabled() {
        if (enable.get()) {
            return switch (currentAC.get()) {
                case GRIM -> isGrimMultiPlaceDisabled();
                case MATRIX -> false;
                default -> true;
            };
        }
        return false;
    }

    public boolean isMultiRotPlaceCheckDisabled() {
        return isMultiPlaceCheckDisabled() && isRotationPlaceCheckDisabled();
    }

    public boolean isRotationPlaceCheckDisabled() {
        if (enable.get()) {
            return switch (currentAC.get()) {
                case GRIM -> isGrimSelfCheckDisabled();
                case MATRIX -> false;
                default -> true;
            };
        }
        return false;
    }

    public boolean isGrimMultiPlaceDisabled() {
        return enable.get() && currentAC.get() == SupportAC.GRIM && (grimMultiplace.get());
    }

    public void onDisconnect(Event<Void> eventDisconnect) {
        grimSelfCheckDisabler = false;
    }

    boolean hasAnyPlaceActionGrimQueue = false;

    public boolean flushACPlaceQueue() {
        if (autoFlushPlaceQueue.get()) {
            return flushACPlaceQueue0();
        }
        return false;
    }

    private boolean flushACPlaceQueue0() {
        switch (currentAC.get()) {
            case GRIM -> {
                // flush ghost blocks
                // see GrimAC handleQueuedPlaces()
                if (hasAnyPlaceActionGrimQueue) {
                    if (ViaFabricPlusHooks.isSupportDupRot()) {
                        LegacySnapRotManager.INSTANCE.snapAt(mc.player.getPitch(), mc.player.getYaw(), true);
                    } else {
                        int selected = InventoryUtils.getSelectedSlot();
                        int next = selected == 8 ? 7 : 8;
                        Listener.sendPacketNoEvents(new UpdateSelectedSlotC2SPacket(next));
                        Listener.sendPacketNoEvents(new UpdateSelectedSlotC2SPacket(selected));
                    }
                }
                hasAnyPlaceActionGrimQueue = false;
                return true;
            }
        }
        return false;
    }

    public boolean flushACPlaceBreakQueue() {
        if (autoFlushPlaceBreakQueue.get()) {
            return flushACPlaceQueue0();
        }
        return false;
    }

    public void onPlace(Event<PlayerInteractBlockC2SPacket> blockPlace) {
        if (blockPlace.isCancelled()) return;
        BlockHitResult hitResult = blockPlace.context.getBlockHitResult();
        Direction direction = hitResult.getSide();
        Vec3d cursor = hitResult.getPos();
        BlockPos blockPos = hitResult.getBlockPos();
        if (enable.get() && hasAnyPlaceActionGrimQueue && autoFlushPlaceQueue.get()) {
            flushACPlaceQueue0();
        }
        hasAnyPlaceActionGrimQueue = true;

        if (grimSelfCheckDisabler) {
            hasPlaceThisTick = false;
        }
        if (hasPlaceThisTick && enable.get() && currentAC.get() == SupportAC.GRIM && grimMultiplace.get()) {
            if (direction != lastDirection
                    || !Objects.equals(cursor, lastCursor)
                    || !Objects.equals(blockPos, lastPos)) {
                PlayerInteractBlockC2SPacket pkt = blockPlace.context;
                PacketManager.schedulePostCallback(pkt, () -> {
                    Listener.sendPacketNoEvents(new PlayerInteractBlockC2SPacket(
                            pkt.getHand(), pkt.getBlockHitResult(), NetworkUtils.generateNextSequence()));
                });
            }
        }
        lastDirection = direction;
        lastCursor = cursor;
        lastPos = blockPos;
    }

    public void onBreakAction(Event<PlayerActionC2SPacket> eventBreak) {
        if (eventBreak.isCancelled()) return;
        switch (eventBreak.context.getAction()) {
            case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> {}
            default -> {
                return;
            }
        }
        if (enable.get() && hasAnyPlaceActionGrimQueue && autoFlushPlaceBreakQueue.get()) {
            flushACPlaceQueue0();
        }
        hasAnyPlaceActionGrimQueue = false;
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
