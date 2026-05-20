package me.matl114.hacks.modules.extra;

import java.util.Objects;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.util.math.Vec2f;

public class BadPacketsFix extends BaseModule {
    public static final String[] BAD_PACKETS_SPRINT = new String[] {"bad-packets", "fix-dup-sprint"};
    public static final String[] BAD_PACKETS_SNEAK = new String[] {"bad-packets", "fix-dup-sneak"};
    public static final String[] BAD_PACKETS_INPUT = new String[] {"bad-packets", "fix-dup-input"};

    public static final String[] BAD_PACKETS_FLY = new String[] {"bad-packets", "fix-fly-packets"};
    public static final String[] BAD_PACKETS_ROT = new String[] {"bad-packets", "fix-dup-rot"};

    public BadPacketsFix() {}

    public final FlagRef enableSprint = builder(Configs.TEST_CONFIG, BAD_PACKETS_SPRINT, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableSneak = builder(Configs.TEST_CONFIG, BAD_PACKETS_SNEAK, Boolean.class)
            .defaultValue(true)
            .build();
    public final FlagRef enableInput = builder(Configs.TEST_CONFIG, BAD_PACKETS_INPUT, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableFly = builder(Configs.TEST_CONFIG, BAD_PACKETS_FLY, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableRot = builder(Configs.TEST_CONFIG, BAD_PACKETS_ROT, Boolean.class)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInitialize);
        registerListener(Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class), this::onSendSprint);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInputC2SPacket.class), this::onSendInput);
        registerListener(Listener.getPacketPoint().getChannel(PlayerAbilitiesS2CPacket.class), this::onServerAbility);
        registerListener(
                Listener.getPacketPoint().getChannel(UpdatePlayerAbilitiesC2SPacket.class), this::onAbilityUpdate);
        registerListener(Listener.getPacketPoint().getChannel(TeleportConfirmC2SPacket.class), this::onTeleportConfirm);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onPlayerRotation);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(Listener.getPostHandleInputEvents(), this::onPostInputEvent);
    }

    boolean serverSprint = false;
    // removed due to protocol change
    //boolean serverSneak = false;
    boolean serverCanFly = false;
    PlayerInputUtils.Input serverInput = PlayerInputUtils.EMPTY;
    float serverPitch;
    float serverYaw;
    boolean handlingInputs = false;

    public void onPreInputEvent(Event<Void> eventVoid) {
        handlingInputs = true;
    }

    public void onPostInputEvent(Event<Void> eventVoid) {
        handlingInputs = false;
    }

    public void onPlayerInitialize(Event<ClientPlayerEntity> event) {
        ClientPlayerEntity entity = event.context();
        serverSprint = entity.isSprinting();
        //serverSneak = entity.isSneaking();
        serverInput = PlayerInputUtils.EMPTY;
        serverCanFly = entity.getAbilities().allowFlying;
        serverPitch = entity.getPitch();
        serverYaw = entity.getYaw();
    }

    public void onSendSprint(Event<ClientCommandC2SPacket> event) {
        if (event.context().getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING
                || event.context().getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
            boolean isStartingSprint = (event.context().getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING);
            if (serverSprint == isStartingSprint) {
                if (enableSprint.get()) {
                    event.cancel();
                }
            } else {
                serverSprint = isStartingSprint;
            }
        }
    }

    public boolean shouldConsiderInputPacket = SupportVersion.CURRENT.isHigherOrEqualTo(21, 2);

    public void onSendInput(Event<PlayerInputC2SPacket> inputC2SPacketEvent) {
        PlayerInputUtils.Input input = PlayerInputUtils.of(inputC2SPacketEvent.context());
        if (Objects.equals(input, serverInput)) {
            if (!mc.player.isRiding() && shouldConsiderInputPacket && enableInput.get()) {
                inputC2SPacketEvent.cancel();
            }
        } else {
            serverInput = input;
        }
    }

    public void onServerAbility(Event<PlayerAbilitiesS2CPacket> event) {
        serverCanFly = event.context.allowFlying();
    }

    public void onAbilityUpdate(Event<UpdatePlayerAbilitiesC2SPacket> event) {
        if (!event.isCancelled()
                && enableFly.get()
                && !serverCanFly
                && event.context().isFlying()) {
            event.cancel();
        }
    }

    boolean exempt = false;

    public void onTeleportConfirm(Event<TeleportConfirmC2SPacket> packetEvent) {
        exempt = true;
    }

    public void onPlayerRotation(Event<PlayerMoveC2SPacket> packetEvent) {
        PlayerMoveC2SPacket packet = packetEvent.context();
        float serverPitch = packet.getPitch(this.serverPitch);
        float serverYaw = packet.getYaw(this.serverYaw);
        if (packet instanceof PlayerMoveC2SPacketAccess access) {
            PlayerMoveC2SPacketAccess.Cause cause = access.getCause();
            if (cause != null) {
                switch (cause) {
                    case SET_BACK, LEGACY_SNAP -> {
                        exempt = true;
                    }
                }
            }
        }
        // fix lower than 1.20.6 interactItem protocol
        if (handlingInputs) {
            exempt = true;
        }
        if (exempt) {
            this.serverPitch = serverPitch;
            this.serverYaw = serverYaw;
            exempt = false;
            return;
        }

        if (serverPitch == this.serverPitch && serverYaw == this.serverYaw) {
            if (packet.changesLook() && enableRot.get()) {
                if (packet instanceof PlayerMoveC2SPacket.Full full) {
                    packetEvent.context(VPacket.newPositionAndOnGround(
                            packet.getX(mc.player.getX()),
                            packet.getY(mc.player.getY()),
                            packet.getZ(mc.player.getX()),
                            packet.isOnGround(),
                            VPacket.getCollisionFlag(full)));
                } else if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround lookAndOnGround) {
                    packetEvent.context(VPacket.newOnGroundOnly(
                            lookAndOnGround.isOnGround(), VPacket.getCollisionFlag(lookAndOnGround)));
                }
            }
        } else {
            this.serverPitch = serverPitch;
            this.serverYaw = serverYaw;
            return;
        }
    }

    public boolean isRotationDifferent() {
        return EntityUtils.isRotationDifferent(serverPitch, mc.player.getPitch(), serverYaw, mc.player.getYaw());
    }

    public boolean isRotationDifferent(float pitch, float yaw) {
        return EntityUtils.isRotationDifferent(serverPitch, pitch, serverYaw, yaw);
    }

    public Vec2f getServerPitchYaw() {
        return new Vec2f(serverPitch, serverYaw);
    }

    public boolean isServerSprinting() {
        return serverSprint;
    }

    public void sendSprintingStatus(boolean bl) {
        if (bl != serverSprint) {
            if (bl) {
                mc.getNetworkHandler()
                        .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            } else {
                mc.getNetworkHandler()
                        .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
            ClientPlayerAccess.of(mc.player).resyncSprint();
        }
    }
}
