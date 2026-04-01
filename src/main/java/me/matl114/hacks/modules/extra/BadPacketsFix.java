package me.matl114.hacks.modules.extra;

import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;

public class BadPacketsFix extends BaseModule {
    public static final String[] BAD_PACKETS_SPRINT = new String[] {"bad-packets", "fix-dup-sprint"};
    public static final String[] BAD_PACKETS_SNEAK = new String[] {"bad-packets", "fix-dup-sneak"};
    public static final String[] BAD_PACKETS_INPUT = new String[] {"bad-packets", "fix-dup-input"};

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

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInitialize);
        registerListener(Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class), this::onSendSprint);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInputC2SPacket.class), this::onSendInput);
    }

    boolean serverSprint = false;
    boolean serverSneak = false;
    PlayerInputUtils.Input serverInput = PlayerInputUtils.EMPTY;

    public void onPlayerInitialize(Event<ClientPlayerEntity> event) {
        ClientPlayerEntity entity = event.context();
        serverSprint = entity.isSprinting();
        serverSneak = entity.isSneaking();
        serverInput = PlayerInputUtils.EMPTY;
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

    public void onSendInput(Event<PlayerInputC2SPacket> inputC2SPacketEvent) {
        PlayerInputUtils.Input input = PlayerInputUtils.of(inputC2SPacketEvent.context());
        if (Objects.equals(input, serverInput)) {
            if (enableInput.get()) {
                inputC2SPacketEvent.cancel();
            }
        } else {
            serverInput = input;
        }
    }
}
