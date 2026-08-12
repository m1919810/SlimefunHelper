package me.matl114.hacks.modules.combat;

import com.google.common.collect.ImmutableList;
import java.awt.*;
import java.util.List;
import java.util.OptionalInt;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.entity.EntityMovementStatus;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class Blink extends BaseModule {
    public final ModulePath lagUtils = makePath(Configs.COMBAT_CONFIG, "lag-utils");
    public final ModulePath blink = lagUtils.add("blink");

    public Blink() {
        super("Blink");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(blink.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(blink.add("hotkey"), new MultiKeyBind(), blink.add("enable"))
            .build();

    public final KeyBindRef revert = hotkey(blink.add("revert"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::revertMoves))
            .build();

    public final FlagRef render = flagBuilder(blink.add("render")).build();

    public final FlagRef autoClose = flagBuilder(blink.add("close-on-delay")).build();

    public final IntRef closeDelay =
            intBuilder(blink.add("close-delay")).defaultValue(50).build();

    public final FlagRef autoFlush = flagBuilder(blink.add("auto-flush")).build();

    public final IntRef autoFlushDelay =
            intBuilder(blink.add("auto-flush-period")).defaultValue(20).build();

    public final EnumRef<Action> attackBehaviour = builder(blink.add("attack-behaviour"), Action.class)
            .defaultValue(Action.FLUSH)
            .build();

    public final EnumRef<Action> onHurtBehaviour = builder(blink.add("on-hurt-behaviour"), Action.class)
            .defaultValue(Action.NONE)
            .build();

    public final EnumRef<Action> onVelocityBehaviour = builder(blink.add("on-velocity-behaviour"), Action.class)
            .defaultValue(Action.NONE)
            .build();

    public final EnumRef<Action> onInventoryBehaviour = builder(blink.add("on-inventory-behaviour"), Action.class)
            .defaultValue(Action.NONE)
            .build();

    public final EnumRef<Action> onTotemBehaviour = builder(blink.add("on-totem-behaviour"), Action.class)
            .defaultValue(Action.NONE)
            .build();

    public final EnumRef<Action> onEnermyNearBehaviour = builder(blink.add("on-enermy-near"), Action.class)
            .defaultValue(Action.NONE)
            .build();

    public final DoubleRef nearRange = builder(blink.add("enermy-near-range"), DoubleRef.TYPE)
            .defaultValue(3.5D)
            .build();

    public final EnumRef<Action> nearTargetAction = builder(blink.add("on-near-target"), Action.class)
            .defaultValue(Action.NONE)
            .build();

    public final FlagRef elytraSupport =
            flagBuilder(blink.add("elytra-support")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PacketManager.getPacketQueueEvent().getChannel(NetworkSide.SERVERBOUND), this::onPacketQueue);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onShutdown);
        registerListener(Listener.getPostTick(), this::onTick);
        registerListener(Listener.getServerLeavePoint(), this::onDisconnect);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onPacketHurt);
        registerListener(
                Listener.getPacketPoint().getChannel(EntityVelocityUpdateS2CPacket.class), this::onPacketVelocity);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.FIREWORK_ROCKET), this::onFireworkOwner);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onEntityStatus);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        startTick = Tasks.getTick();
        startPlayerPos = null;
    }

    public EntityMovementStatus<ClientPlayerEntity> startPlayerPos;

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        flush();
        startPlayerPos = null;
        if (checkNull()) return;
        Debug.chat("[Blink] Disable and flush");
    }

    public void onShutdown(Event<Void> eve) {
        if (enable.get()) {
            enable.set(false);
        }
    }

    public void flush() {
        lastAutoDumpTick = Tasks.getTick();
        PacketManager.flushOutBound();
        if (mc.player != null) {
            startPlayerPos = new EntityMovementStatus<>(mc.player);
        }
    }

    public void onRender(Event<MatrixStack> eve) {
        if (enable.get() && render.get()) {
            MatrixStack stack = eve.context();
            if (startPlayerPos != null) {
                RenderUtils.startDrawVirtual(stack);
                try {
                    Box box = startPlayerPos.entity.dimensions.getBoxAt(startPlayerPos.pos);
                    RenderUtils.drawOutlinedBox(stack, box.getMinPos(), box.getMaxPos(), Color.MAGENTA);
                } finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }

    int startTick = 0;
    int lastAutoDumpTick = 0;

    public void revertMoves() {
        if (checkNull()) return;
        if (enable.get() && startPlayerPos != null) {
            Debug.chat("[Blink] Start revert");
            try {
                MutableBoolean afterTeleportExcept = new MutableBoolean(false);
                lastAutoDumpTick = Tasks.getTick();
                PacketManager.flushOutBound(packets -> {
                    var packet = packets.packetType();
                    if (packet == PlayPackets.MOVE_PLAYER_POS
                            || packet == PlayPackets.MOVE_PLAYER_ROT
                            || packet == PlayPackets.MOVE_PLAYER_POS_ROT
                            || packet == PlayPackets.MOVE_PLAYER_STATUS_ONLY) {
                        if (afterTeleportExcept.booleanValue()) {
                            return PacketManager.FlushAction.FLUSH;
                        }
                        return PacketManager.FlushAction.DROP;
                    } else if (packet == PlayPackets.PLAYER_INPUT) {
                        return PacketManager.FlushAction.DROP;
                    } else if (packet == PlayPackets.ACCEPT_TELEPORTATION) {
                        afterTeleportExcept.setTrue();
                        return PacketManager.FlushAction.FLUSH;
                    } else return PacketManager.FlushAction.FLUSH;
                });
            } finally {
                ClientPlayerAccess.of(mc.player).resyncInput();
                startPlayerPos.restore();
            }
        }
    }

    public void onTick(Event<Void> tickEvent) {
        if (enable.get()) {
            if (mc.player != null && startPlayerPos == null) {
                startPlayerPos = new EntityMovementStatus<>(mc.player);
            }
            if (mc.player != null && autoFlush.get() && lastAutoDumpTick + autoFlushDelay.get() < Tasks.getTick()) {
                Debug.chat("[Blink] Auto flush");
                flush();
            }
            if (autoClose.get() && Tasks.getTick() > startTick + closeDelay.get()) {
                enable.set(false);
            }

            if (onEnermyNearBehaviour.get() != Action.NONE && startPlayerPos != null) {
                // check if any enermy
                boolean find = false;
                List<Entity> et = ImmutableList.copyOf(mc.world.getEntities());
                Vec3d oldPos = startPlayerPos.pos;
                Vec3d predictionPos = oldPos.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
                for (var e : et) {
                    if (e.getBoundingBox().squaredMagnitude(predictionPos) < MathUtils.s2(nearRange.get())
                            && TargetSelector.INSTANCE.canAttack(e)) {
                        find = true;
                        break;
                    }
                }
                if (find) {
                    handleAction(onEnermyNearBehaviour.get());
                }
            }
            if (nearTargetAction.get() != Action.NONE) {
                Entity target =
                        TargetSelector.INSTANCE.searchAttackEntity(CombatExtra.INSTANCE.getAttackRange(), true, 1);
                if (target != null) {
                    handleAction(nearTargetAction.get());
                }
            }
        }
    }

    public void onDisconnect(Event<Void> disconnect) {
        enable.set(false);
    }

    boolean escapeSwing = false;
    boolean elytraSupportFuckStartFly = false;

    public void onPacketQueue(Event<PacketStorage> packet) {
        if (enable.get()) {
            var pkt = packet.context;
            PacketType<?> pktType = pkt.packetType();
            if (PacketManager.isAsyncOrNotTransactionC2SPacket(pktType)) return;
            if ((!(onInventoryBehaviour.get() == Action.NONE || (elytraSupport.get() && mc.player.isFallFlying())))
                    && PacketManager.isInventoryPacket(pktType)) return;
            // support elytra armorFly mace attack
            //            if(elytraSupport.get() && mc.player.isFallFlying() && pkt instanceof ClientCommandC2SPacket
            // cmd && cmd.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            //                if(elytraSupportFuckStartFly){
            //                    flush();
            //                    elytraSupportFuckStartFly = false;
            //                    return;
            //                }
            //            }
            if (pktType == PlayPackets.USE_ITEM && mc.player.isFallFlying()) {
                if (onFireworkUse()) {
                    return;
                }
            }
            if (pktType == PlayPackets.INTERACT) {
                handleQueueAction(packet, attackBehaviour.get());
                escapeSwing = true;
            } else if (pktType == PlayPackets.SWING && escapeSwing) {
                escapeSwing = false;
                handleQueueAction(packet, attackBehaviour.get());
            } else if (pktType == PlayPackets.ACCEPT_TELEPORTATION) {
                flush();
            } else {
                packet.cancel();
            }
        }
    }

    public void handleQueueAction(Event<?> packet, Action action) {
        switch (action) {
            case FLUSH -> flush();
            case CLOSE -> enable.set(false);
            default -> packet.cancel();
        }
    }

    public void handleAction(Action action) {
        switch (action) {
            case FLUSH -> flush();
            case CLOSE -> enable.set(false);
        }
    }

    public void onFireworkOwner(Event<DataTracker.SerializedEntry<?>> firework) {
        if (enable.get()
                && elytraSupport.get()
                && firework.context().id() == VDataFlag.ID_FIREWORK_SHOOTER_ID
                && firework.getArgs(0) instanceof FireworkRocketEntity fireworkEntity
                && mc.player != null
                && mc.player.isFallFlying()
                && firework.context().value() instanceof OptionalInt opint
                && opint.isPresent()
                && opint.getAsInt() == mc.player.getId()) {
            flush();
        }
    }

    public void onPacketHurt(Event<EntityDamageS2CPacket> damage) {
        if (enable.get() && mc.player != null && damage.context.entityId() == mc.player.getId()) {
            handleAction(onHurtBehaviour.get());
        }
    }

    public void onPacketVelocity(Event<EntityVelocityUpdateS2CPacket> event) {
        if (enable.get()
                && mc.player != null
                && event.context.getEntityId() == mc.player.getId()
                && !event.isCancelled()) {
            handleAction(onVelocityBehaviour.get());
        }
    }

    public void onEntityStatus(Event<EntityStatusS2CPacket> eventTotem) {
        if (checkNull()) return;
        if (enable.get()
                && eventTotem.context.getEntity(mc.world) == mc.player
                && eventTotem.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
            handleAction(onTotemBehaviour.get());
        }
    }

    public boolean onFireworkUse() {
        if (enable.get() && elytraSupport.get()) {
            flush();
            return true;
        }
        return false;
    }

    public static enum Action implements ConfigEnum {
        NONE,
        FLUSH,
        CLOSE;

        @Override
        public String getConfigEnumType() {
            return "blink_event_action";
        }
    }
}
