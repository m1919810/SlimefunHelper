package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.utils.EntityUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class LegacySnapRotManager extends BaseModule {
    public static LegacySnapRotManager INSTANCE;

    public LegacySnapRotManager() {
        super("LegacySnapRotManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getEntityPreTickListener().getChannel(EntityType.PLAYER), this::onPrePlayerTick);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onInteractItem);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class),
                this::onSendPlayerPosRotPacket,
                Integer.MIN_VALUE);
    }

    Vec2f lastSnapPitchYaw;

    public void onPrePlayerTick(Event<PlayerEntity> eventPre) {
        if (mc.player != null && eventPre.context == mc.player) {
            resyncSnap();
        }
    }

    public boolean betweenViaPacket;

    public void onSendPlayerPosRotPacket(Event<PlayerMoveC2SPacket> event) {
        if (betweenViaPacket
                && ViaFabricPlusHooks.isSupportDupRot()
                && event.context instanceof PlayerMoveC2SPacket.Full move
                && move instanceof PlayerMoveC2SPacketAccess acc) {
            acc.setCause(PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP);
        }
        // reset snap packets because there are other rot packets
        if (event.context.changesLook()) {
            lastSnapPitchYaw = null;
        }
    }

    public void onInteractItem(Event<PlayerInteractItemC2SPacket> eventInteract) {
        if (false && lastSnapPitchYaw != null) {
            float pitch = eventInteract.context.getPitch();
            float yaw = eventInteract.context.getYaw();
            if (EntityUtils.isRotationDifferent(lastSnapPitchYaw.x, pitch, lastSnapPitchYaw.y, yaw)
                    && PlayerStateManager.INSTANCE.isRotationDifferent(pitch, yaw)) {
                snapAt(pitch, yaw, false);
            }
        }
    }

    public void resyncSnap() {
        if (lastSnapPitchYaw != null && PlayerStateManager.INSTANCE.isRotationDifferent()) {
            ClientPlayerAccess access = ClientPlayerAccess.of(mc.player);
            access.resyncRot();
        }
        lastSnapPitchYaw = null;
    }

    public void snapAt(Vec3d look, boolean force) {
        Vec2f py = EntityUtils.rotationToPitchYaw(look.normalize());
        snapAt(py.x, py.y, force);
    }

    public void snapAt(float pitch, float yaw, boolean force) {
        if (force || PlayerStateManager.INSTANCE.isRotationDifferent(pitch, yaw)) {
            mc.getNetworkHandler().sendPacket(createSnapAt(pitch, yaw));
        }
        lastSnapPitchYaw = new Vec2f(pitch, yaw);
    }

    public PlayerMoveC2SPacket createSnapAt(Vec3d look) {
        Vec2f py = EntityUtils.rotationToPitchYaw(look.normalize());
        return createSnapAt(py.x, py.y);
    }

    public PlayerMoveC2SPacket createSnapAt(Vec3d look, boolean onGroundOverride) {
        Vec2f py = EntityUtils.rotationToPitchYaw(look.normalize());
        return createSnapAt(py.x, py.y, onGroundOverride);
    }

    public PlayerMoveC2SPacket createSnapAt(float pitch, float yaw) {
        float lastYaw = PlayerStateManager.INSTANCE.lastYaw;
        return PlayerMoveC2SPacketAccess.setCause(
                VPacket.newFull(
                        PlayerStateManager.INSTANCE.lastX,
                        PlayerStateManager.INSTANCE.lastY,
                        PlayerStateManager.INSTANCE.lastZ,
                        EntityUtils.getSafeYaw(lastYaw, yaw),
                        EntityUtils.getSafePitch(pitch),
                        PlayerStateManager.INSTANCE.lastOnGround,
                        mc.player.horizontalCollision),
                PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP);
    }

    public PlayerMoveC2SPacket createSnapAt(float pitch, float yaw, boolean onGroundOverride) {
        float lastYaw = PlayerStateManager.INSTANCE.lastYaw;
        return PlayerMoveC2SPacketAccess.setCause(
                VPacket.newFull(
                        PlayerStateManager.INSTANCE.lastX,
                        PlayerStateManager.INSTANCE.lastY,
                        PlayerStateManager.INSTANCE.lastZ,
                        EntityUtils.getSafeYaw(lastYaw, yaw),
                        EntityUtils.getSafePitch(pitch),
                        onGroundOverride,
                        mc.player.horizontalCollision),
                PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP);
    }

    public PlayerMoveC2SPacket createAsSnap(PlayerMoveC2SPacket full) {
        var pkt = VPacket.newFull(
                full.getX(PlayerStateManager.INSTANCE.lastX),
                full.getY(PlayerStateManager.INSTANCE.lastY),
                full.getZ(PlayerStateManager.INSTANCE.lastZ),
                PlayerStateManager.INSTANCE.lastYaw,
                PlayerStateManager.INSTANCE.lastPitch,
                full.isOnGround(),
                VPacket.getCollisionFlag(full));
        PlayerMoveC2SPacketAccess.of(pkt).setCause(PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP);
        return pkt;
    }

    public void sendAsSnap(PlayerMoveC2SPacket full) {
        PlayerMoveC2SPacket recreateFull = createAsSnap(full);
        mc.getNetworkHandler().sendPacket(recreateFull);
    }
}
