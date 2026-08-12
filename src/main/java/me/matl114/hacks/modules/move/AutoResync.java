package me.matl114.hacks.modules.move;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

@SuppressWarnings("all")
public class AutoResync extends BaseModule {
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");

    public AutoResync() {
        super("AutoResync");
    }

    public Optional<Vec3d> pos;
    public int ticksTilExpire;

    public final FlagRef autoResyncRot =
            flagBuilder(moveSafety.add("auto-resync-rotation")).build();

    public final FlagRef modifyPacketRot =
            flagBuilder(moveSafety.add("auto-resync-rot-modify-packet")).build();

    public final FlagRef noVelocitySetback =
            flagBuilder(moveSafety.add("auto-resync-velocity")).build();

    public final FlagRef autoResyncPos =
            flagBuilder(moveSafety.add("auto-resync-pos")).build();

    public final DoubleRef autoResyncPosDistance = builder(moveSafety.add("auto-resync-distance"), DoubleRef.TYPE)
            .defaultValue(10.0D)
            .build();

    public final FlagRef logAutoResync =
            flagBuilder(moveSafety.add("log-auto-resync-request")).build();

    public final IntRef expireTick = builder(moveSafety.add("auto-resync-request-expire-tick"), IntRef.TYPE)
            .defaultValue(10)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef recursive =
            flagBuilder(moveSafety.add("auto-resync-request-recursively")).build();

    public void setAutoResyncSchedule(Optional<Vec3d> pos) {
        this.setAutoResyncSchedule(pos, expireTick.get());
    }

    public void setAutoResyncSchedule(Optional<Vec3d> pos, int ticksExpire) {
        this.pos = pos;
        this.ticksTilExpire = ticksExpire + Tasks.getTick();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onPreSetBack);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onPostSetBack);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(PlayerRotationS2CPacket.class), this::onPreRotate);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerRotationS2CPacket.class), this::onPostRotate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
    }

    int worldSwitchTick = 0;

    public void onWorldSwitch(Event<World> event) {
        worldSwitchTick = Tasks.getTick();
    }

    public Vec2f restoreRot = null;

    public void onSetBack(Event<PlayerPositionLookS2CPacket> event) {
        if (event.isCancelled()) return;
        if (mc.player == null) return;
        // just switch world for no more than 10 second, it is a game join, do not apply any resync
        if (worldSwitchTick + 100 > Tasks.getTick()) return;
        if (mc.player.getPos().equals(Vec3d.ZERO)) {
            // ignoring first spawn packets
            return;
        }
        if (mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) {
            // do not modify spectator tp
            return;
        }
        boolean currentOnGround = mc.player.isOnGround();
        if (ticksTilExpire > Tasks.getTick() && pos != null) {
            // auto resync
            Vec3d resyncToPos = pos.orElseGet(mc.player::getPos);
            PlayerPositionLookS2CPacket packet1 = event.context;
            Vec3d resyncPos = getPosition(packet1);
            double sqdistance = resyncPos.squaredDistanceTo(mc.player.getPos());
            double sqdistance2 = resyncPos.squaredDistanceTo(resyncToPos);
            if (sqdistance > 1E-4
                    && sqdistance < MathUtils.s2(128)
                    && sqdistance2 > 1E-4
                    && sqdistance2 < MathUtils.s2(128)) {
                // don't so far, it may be a real teleport
                if (logAutoResync.get()) {
                    Debug.chat("Auto Resync triggered!");
                }
                mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(packet1.teleportId()));
                executeResyncTo(resyncPos, resyncToPos, currentOnGround);
                if (recursive.get()) {
                    mc.player.setPosition(resyncToPos);
                    setAutoResyncSchedule(Optional.empty());
                }
                event.cancel();
                return;
            }
        }
        if (autoResyncPos.get()) {
            Vec3d resyncToPos = mc.player.getPos();
            BlockPos blockPos = BlockPos.ofFloored(resyncToPos);
            // do not resync in unloaded chunks
            if (mc.world.getChunkManager().isChunkLoaded(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
                PlayerPositionLookS2CPacket packet1 = event.context;
                Vec3d resyncPos = getPosition(packet1);
                double sqDistance = resyncToPos.squaredDistanceTo(resyncPos);
                if (autoResyncPosDistance.get() > 0 && sqDistance < MathUtils.s2(autoResyncPosDistance.get())) {
                    mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(packet1.teleportId()));
                    executeResyncTo(resyncPos, resyncToPos, currentOnGround);
                    event.cancel();
                    return;
                }
            }
        }
        // remove rot
        boolean recreate = false;
        var packet = event.context();
        Set<PositionFlag> flags = packet.relatives();
        Set<PositionFlag> newFlags = null;
        EntityPosition pos = packet.change();
        Vec3d position = pos.position();
        Vec3d deltaMovement = pos.deltaMovement();
        float yaw = pos.yaw();
        float pitch = pos.pitch();
        if (autoResyncRot.get()) {
            if (modifyPacketRot.get()) {
                recreate = true;
                if (newFlags == null) {
                    newFlags = new HashSet<>(flags);
                }
                newFlags.add(PositionFlag.X_ROT);
                newFlags.add(PositionFlag.Y_ROT);
                yaw = 0;
                pitch = 0;
            }
        }
        if (noVelocitySetback.get()) {
            recreate = true;
            if (newFlags == null) {
                newFlags = new HashSet<>(flags);
            }
            newFlags.remove(PositionFlag.ROTATE_DELTA);
            newFlags.add(PositionFlag.DELTA_X);
            newFlags.add(PositionFlag.DELTA_Y);
            newFlags.add(PositionFlag.DELTA_Z);
            deltaMovement = Vec3d.ZERO;
        }
        if (recreate && newFlags != null) {
            event.context(new PlayerPositionLookS2CPacket(
                    packet.teleportId(), new EntityPosition(position, deltaMovement, yaw, pitch), newFlags));
        }
    }

    public void onPreSetBack(Event<PlayerPositionLookS2CPacket> event) {
        if (autoResyncRot.get() && !modifyPacketRot.get()) {
            restoreRot = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
            //            mc.player.setPitch(PlayerStateManager.INSTANCE.lastPitch);
            //            mc.player.setYaw(PlayerStateManager.INSTANCE.lastYaw);
        }
    }

    public void onPreRotate(Event<PlayerRotationS2CPacket> eventRotate) {
        if (autoResyncRot.get()) {
            if (modifyPacketRot.get()) {
                eventRotate.cancel();
            } else {
                restoreRot = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
            }
        }
    }

    public void onPostSetBack(Event<PlayerPositionLookS2CPacket> event) {
        if (restoreRot != null) {
            EntityUtils.setEntityPitchSafe(mc.player, restoreRot.x);
            PlayerStateManager.setPlayerYawSafe(mc.player, restoreRot.y);
            restoreRot = null;
        }
    }

    public void onPostRotate(Event<PlayerRotationS2CPacket> eventRotate) {
        if (restoreRot != null) {
            EntityUtils.setEntityPitchSafe(mc.player, restoreRot.x);
            PlayerStateManager.setPlayerYawSafe(mc.player, restoreRot.y);
            restoreRot = null;
        }
    }

    public void executeResyncTo(Vec3d resyncPos, Vec3d resyncToPos, boolean currentOnGround) {
        mc.player.setPosition(resyncPos);
        mc.player.setOnGround(false);
        //                    mc.getNetworkHandler().sendPacket(new
        // PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
        // false));
        if (currentOnGround) {
            resyncToPos = resyncToPos.add(0, 1e-6, 0);
        }
        MovTasks.scheduleTpInternal(MovTasks.createPlayerMovContext(), resyncToPos, 200, false, true, true);
        ticksTilExpire = -1;
        pos = null;
    }

    public Vec3d getPosition(PlayerPositionLookS2CPacket packet) {
        EntityPosition entityPosition = EntityPosition.fromEntity(mc.player);
        EntityPosition entityPosition2 = EntityPosition.apply(entityPosition, packet.change(), packet.relatives());
        return entityPosition2.position();
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case HACKING, VANILLA, AC_VULCAN -> {
                if (autoResyncPosDistance.get() < 0) {
                    autoResyncPosDistance.set(-autoResyncPosDistance.get());
                }
            }
            default -> {
                if (autoResyncPosDistance.get() > 0) {
                    autoResyncPosDistance.set(-autoResyncPosDistance.get());
                }
            }
        }
    }
}
