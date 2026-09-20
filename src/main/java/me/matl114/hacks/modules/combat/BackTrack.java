package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.RenderUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BackTrack extends BaseModule {
    public BackTrack() {
        super("BackTrack");
        bindFlag(enable);
    }

    public final ModulePath lagUtils = makePath(Configs.COMBAT_CONFIG, "lag-utils");
    public ModulePath bt = lagUtils.add("back-track");
    public final FlagRef enable = flagBuilder(bt.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(bt.addHotkey(), new MultiKeyBind(), bt.addEnable()).build();

    public final DoubleRef maxDelay =
            doubleBuilder(bt.add("max-delay")).defaultValue(50.0D).build();

    public final FlagRef render = flagBuilder(bt.add("render-old")).build();

    public final DoubleRef maxDistance =
            doubleBuilder(bt.add("max-distance")).defaultValue(5.0D).build();

    public final FlagRef playerOnly = flagBuilder(bt.add("player-only")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PacketManager.getPacketQueueInEvent(), this::onQueuePlayerPosition);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onShutdownQueue);
        registerListener(Listener.getPreTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        onShutdown();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        onShutdown();
    }

    public Entity currentTarget;
    public Vec3d lastTrackingPosition;
    public volatile boolean shouldDelay;

    public void onShutdown() {
        setNoTarget();
        setNoDelay();
    }

    public void setTarget(Entity entity) {
        currentTarget = entity;
        lastTrackingPosition = entity.getPos();
    }

    public void setNoTarget() {
        currentTarget = null;
        lastTrackingPosition = null;
    }

    public void setNoDelay() {
        shouldDelay = false;
    }

    public void setDelay() {
        shouldDelay = true;
    }

    public void refreshTarget() {
        if (enable.get()) {
            Entity entity = TargetSelector.INSTANCE.searchAttackEntity(
                    maxDistance.get(),
                    true,
                    playerOnly.get() ? (ev) -> ev instanceof PlayerEntity : (ev) -> ev instanceof LivingEntity);
            if (entity != currentTarget) {
                if (entity != null) {
                    setTarget(entity);
                } else {
                    setNoTarget();
                }
                setNoDelay();
            } else {
                // tick
                if (currentTarget == null) {
                    setNoDelay();
                } else if (lastTrackingPosition == null) {
                    lastTrackingPosition = currentTarget.getPos();
                } else if (!TargetSelector.INSTANCE.isWithinAttackRange(
                        mc.player.getPos(),
                        currentTarget.getBoundingBox(),
                        CombatExtra.INSTANCE.getAttackAtTargetRange(currentTarget))) {
                    setNoDelay();
                }
            }
        } else {
            setNoTarget();
            setNoDelay();
        }
    }

    Set<PacketType<?>> movePlayerEntityTypes = new HashSet<>();

    {
        movePlayerEntityTypes.add(PlayPackets.MOVE_ENTITY_POS);
        movePlayerEntityTypes.add(PlayPackets.MOVE_ENTITY_POS_ROT);
    }

    public void onQueuePlayerPosition(Event<PacketStorage> event) {
        if (shouldDelay) {
            long currentMs = System.currentTimeMillis();
            if (event.context.timestampMS() + maxDelay.get() < currentMs) {
                return;
            }
            event.cancel();
        }
        if (enable.get() && currentTarget != null && event.<Boolean>getArgs(1)) {
            var storage = event.context;
            if (storage instanceof PacketManager.PacketStorageImpl impl) {
                var packet = impl.packet();
                if (PacketManager.isAsyncOrNotTransactionS2CPacket(packet)) return;
                if (packet instanceof EntityPositionSyncS2CPacket positionSync
                        && positionSync.id() == currentTarget.getId()) {
                    onShutdown();
                    return;
                }
                if (packet instanceof EntityPositionS2CPacket position
                        && position.entityId() == currentTarget.getId()) {
                    onShutdown();
                    return;
                }
                if (packet instanceof EntityS2CPacket entityMove
                        && entityMove.getEntity(mc.world) == currentTarget
                        && entityMove.isPositionChanged()) {
                    TrackedPosition trackedPosition;
                    Vec3d vec3d;
                    if (lastTrackingPosition == null) {
                        trackedPosition = currentTarget.getTrackedPosition();
                    } else {
                        trackedPosition = new TrackedPosition();
                        trackedPosition.setPos(lastTrackingPosition);
                    }
                    vec3d = trackedPosition.withDelta(
                            (long) entityMove.getDeltaX(), (long) entityMove.getDeltaY(), (long)
                                    entityMove.getDeltaZ());
                    boolean lastDelay = shouldDelay;
                    handleTrackEntityPosition(vec3d);
                    lastTrackingPosition = vec3d;
                    if (shouldDelay) {
                        event.cancel();
                    }
                    return;
                }
                if (packet instanceof EntityStatusS2CPacket entityStatus
                        && entityStatus.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                        && entityStatus.getEntity(mc.world) == mc.player) {
                    setNoDelay();
                    return;
                }
                if (shouldDelay) {
                    event.cancel();
                }
            }
        }
    }

    public void handleTrackEntityPosition(Vec3d position) {
        if (lastTrackingPosition != null) {
            double attackRange = CombatExtra.INSTANCE.getAttackAtTargetRange(currentTarget) - 0.02;
            Box currentBox = currentTarget.dimensions.getBoxAt(lastTrackingPosition);
            Box futureBox = currentTarget.dimensions.getBoxAt(position);
            boolean currentCanAttack =
                    TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), currentBox, attackRange);
            boolean futureCanAttack =
                    TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), futureBox, attackRange);
            if (currentCanAttack && !futureCanAttack) {
                setDelay();
            } else if (futureCanAttack) {
                // attack window
                setNoDelay();
            } else {
                Vec3d bestEyePos = TargetSelector.INSTANCE.getBestAttackEyePos(mc.player.getPos(), currentBox);
                double currentDistance = currentBox.squaredMagnitude(bestEyePos);
                double futureDistance = futureBox.squaredMagnitude(bestEyePos);
                if (futureDistance > currentDistance) {
                    // leaving
                    setDelay();
                }
            }
        }
    }

    public void onShutdownQueue(Event<Void> event) {
        onShutdown();
    }

    public void onTick(Event<Void> eventTick) {
        if (checkNull()) {
            onShutdown();
            return;
        }
        refreshTarget();
    }

    public void onRender(Event<MatrixStack> eventMatrixStack) {
        if (render.get() && shouldDelay && lastTrackingPosition != null && currentTarget != null) {
            Box boundingBox = currentTarget.dimensions.getBoxAt(lastTrackingPosition);
            RenderUtils.startDrawVirtual(eventMatrixStack.context);
            try {
                RenderUtils.drawOutlinedBox(
                        eventMatrixStack.context, boundingBox.getMinPos(), boundingBox.getMaxPos(), Color.ORANGE);
            } finally {
                RenderUtils.stopDrawVirtual(eventMatrixStack.context);
            }
        }
    }
}
