package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ExtraTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.EntityUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class LegacySnapRotManager extends BaseModule {
    public LegacySnapRotManager() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getEntityPreTickListener().getChannel(EntityType.PLAYER), this::onPrePlayerTick);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onInteractItem);
    }

    Vec2f lastSnapPitchYaw;

    public void onPrePlayerTick(Event<PlayerEntity> eventPre) {
        if (mc.player != null && eventPre.context == mc.player) {
            resyncSnap();
        }
    }

    public void onInteractItem(Event<PlayerInteractItemC2SPacket> eventInteract) {
        if (false && lastSnapPitchYaw != null) {
            float pitch = eventInteract.context.getPitch();
            float yaw = eventInteract.context.getYaw();
            if (EntityUtils.isRotationDifferent(lastSnapPitchYaw.x, pitch, lastSnapPitchYaw.y, yaw)
                    && ExtraTasks.getBadPacketsFix().isRotationDifferent(pitch, yaw)) {
                snapAt(pitch, yaw, false);
            }
        }
    }

    public void resyncSnap() {
        if (lastSnapPitchYaw != null && ExtraTasks.getBadPacketsFix().isRotationDifferent()) {
            mc.getNetworkHandler()
                    .sendPacket(PlayerMoveC2SPacketAccess.setCause(
                            VPacket.newFull(
                                    mc.player.getX(),
                                    mc.player.getY(),
                                    mc.player.getZ(),
                                    mc.player.getYaw(),
                                    mc.player.getPitch(),
                                    mc.player.isOnGround(),
                                    mc.player.horizontalCollision),
                            PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP));
        }
        lastSnapPitchYaw = null;
    }

    public void snapAt(Vec3d look, boolean force) {
        Vec2f py = EntityUtils.rotationToPitchYaw(look.normalize());
        snapAt(py.x, py.y, force);
    }

    public void snapAt(float pitch, float yaw, boolean force) {
        if (force || ExtraTasks.getBadPacketsFix().isRotationDifferent(pitch, yaw)) {
            Vec2f serverPitchYaw = ExtraTasks.getBadPacketsFix().getServerPitchYaw();
            mc.getNetworkHandler()
                    .sendPacket(PlayerMoveC2SPacketAccess.setCause(
                            VPacket.newFull(
                                    mc.player.getX(),
                                    mc.player.getY(),
                                    mc.player.getZ(),
                                    EntityUtils.getSafeYaw(serverPitchYaw.y, yaw),
                                    EntityUtils.getSafePitch(pitch),
                                    mc.player.isOnGround(),
                                    mc.player.horizontalCollision),
                            PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP));
        }
        lastSnapPitchYaw = new Vec2f(pitch, yaw);
    }

    public void sendAsSnap(PlayerMoveC2SPacket full) {
        Vec2f py = ExtraTasks.getBadPacketsFix().getServerPitchYaw();
        PlayerMoveC2SPacket recreateFull = VPacket.newFull(
                full.getX(mc.player.getX()),
                full.getY(mc.player.getY()),
                full.getZ(mc.player.getZ()),
                py.y,
                py.x,
                full.isOnGround(),
                VPacket.getCollisionFlag(full));
        PlayerMoveC2SPacketAccess.of(recreateFull).setCause(PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP);
        mc.getNetworkHandler().sendPacket(recreateFull);
    }
}
