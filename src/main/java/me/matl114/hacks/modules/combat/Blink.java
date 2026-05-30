package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.OptionalInt;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.entity.EntityMovementStatus;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Box;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class Blink extends BaseModule {
    public final ModulePath lagUtils = makePath(Configs.COMBAT_CONFIG, "lag-utils");
    public final ModulePath blink = lagUtils.add("blink");

    public Blink() {
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

    public final FlagRef flushOnAttack =
            flagBuilder(blink.add("flush-on-attack")).build();

    public final FlagRef closeOnAttack =
            flagBuilder(blink.add("close-on-attack")).build();

    public final FlagRef flushOnHurt = flagBuilder(blink.add("flush-on-hurt")).build();

    public final FlagRef closeOnHurt = flagBuilder(blink.add("close-on-hurt")).build();

    public final FlagRef flushOnVelocity =
            flagBuilder(blink.add("flush-on-velocity")).build();

    public final FlagRef closeOnVelocity =
            flagBuilder(blink.add("close-on-velocity")).build();

    public final FlagRef invHandle =
            flagBuilder(blink.add("cache-inventory-packet")).build();

    public final FlagRef elytraSupport =
            flagBuilder(blink.add("elytra-support")).build();

    public final FlagRef closeOnTotem = flagBuilder(blink.add("close-on-totem")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PacketManager.getPacketQueueEvent().getPacketSendChannel(), this::onPacketQueue);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onShutdown);
        registerListener(Listener.getPostTick(), this::onTick);
        registerListener(Listener.getServerLeavePoint(), this::onDisconnect);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onPacketHurt);
        registerListener(
                Listener.getPacketPoint().getChannel(EntityVelocityUpdateS2CPacket.class), this::onPacketVelocity);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
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
                    var packet = packets.packet();
                    if (packet instanceof PlayerMoveC2SPacket move) {
                        if (afterTeleportExcept.booleanValue()) {
                            return PacketManager.FlushAction.FLUSH;
                        }
                        return PacketManager.FlushAction.DROP;
                    } else if (packet instanceof PlayerInputC2SPacket inputC2SPacket) {
                        return PacketManager.FlushAction.DROP;
                    } else if (packet instanceof TeleportConfirmC2SPacket tp) {
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
        }
    }

    public void onDisconnect(Event<Void> disconnect) {
        enable.set(false);
    }

    boolean escapeSwing = false;

    public void onPacketQueue(Event<Packet<?>> packet) {
        if (enable.get()) {
            var pkt = packet.context;
            if (PacketManager.isAsyncOrNotTransactionC2SPacket(pkt)) return;
            if ((!(invHandle.get() || (elytraSupport.get() && mc.player.isFallFlying())))
                    && PacketManager.isInventoryPacket(pkt)) return;
            if (pkt instanceof PlayerInteractItemC2SPacket && mc.player.isFallFlying()) {
                if (onFireworkUse()) {
                    return;
                }
            }
            if (pkt instanceof PlayerInteractEntityC2SPacket packet1) {
                boolean flush = false;
                if (flushOnAttack.get()) {
                    flush();
                    flush = true;
                }
                if (closeOnAttack.get()) {
                    enable.set(false);
                    flush = true;
                }
                if (!flush) {
                    packet.cancel();
                }
                escapeSwing = true;
            } else if (pkt instanceof HandSwingC2SPacket swing && escapeSwing) {
                escapeSwing = false;
                boolean flush = false;
                if (flushOnAttack.get()) {
                    flush();
                    flush = true;
                }
                if (!flush) {
                    packet.cancel();
                }
            } else if (pkt instanceof TeleportConfirmC2SPacket tp) {
                flush();
            } else {
                packet.cancel();
            }
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
            if (flushOnHurt.get()) {
                flush();
            }
            if (closeOnHurt.get()) {
                enable.set(false);
            }
        }
    }

    public void onPacketVelocity(Event<EntityVelocityUpdateS2CPacket> event) {
        if (enable.get() && mc.player != null && event.context.getEntityId() == mc.player.getId()) {
            if (flushOnVelocity.get()) {
                flush();
            }
            if (closeOnVelocity.get()) {
                enable.set(false);
            }
        }
    }

    public void onEntityStatus(Event<EntityStatusS2CPacket> eventTotem) {
        if (checkNull()) return;
        if (enable.get()
                && closeOnTotem.get()
                && eventTotem.context.getEntity(mc.world) == mc.player
                && eventTotem.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
            flush();
            enable.set(false);
        }
    }

    public boolean onFireworkUse() {
        if (enable.get() && elytraSupport.get()) {
            flush();
            return true;
        }
        return false;
    }
}
