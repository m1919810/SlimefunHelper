package me.matl114.hacks.modules.ac;

import java.util.Objects;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hooks.ViaFabricPlusHooks;
import net.minecraft.network.packet.c2s.play.*;

public class PacketOrderManager extends BaseModule {
    public boolean swapping;
    public boolean dropping;
    public boolean interacting;
    public boolean attacking;
    public boolean releasing;
    public boolean digging;
    public boolean sprinting;
    public boolean placing;
    public boolean using;
    public boolean startingToGlide;
    public static PacketOrderManager INSTANCE;

    public PacketOrderManager() {
        super("PacketOrderManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class),
                this::onInteract,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onInteractBlock,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class),
                this::onPlayerAction,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class),
                this::onEntityAction,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onPacketTick, Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientTickEndC2SPacket.class), this::onTickEnd, Integer.MAX_VALUE);
    }

    public void onInteract(Event<PlayerInteractEntityC2SPacket> event) {
        String name = ((Enum) event.context.type.getType()).name();
        if (Objects.equals(name, "ATTACK")) {
            attacking = true;
        } else {
            interacting = true;
        }
    }

    public void onInteractBlock(Event<PlayerInteractBlockC2SPacket> event) {
        placing = true;
    }

    public void onPlayerAction(Event<PlayerActionC2SPacket> event) {
        switch (event.context.getAction()) {
            case SWAP_ITEM_WITH_OFFHAND -> swapping = true;
            case DROP_ITEM, DROP_ALL_ITEMS -> dropping = true;
            case RELEASE_USE_ITEM -> releasing = true;
            case STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, START_DESTROY_BLOCK -> digging = true;
        }
    }

    public void onEntityAction(Event<ClientCommandC2SPacket> event) {
        switch (event.context.getMode()) {
            case START_SPRINTING, STOP_SPRINTING -> {
                if (!mc.player.hasVehicle()) {
                    sprinting = true;
                }
            }
            case START_FALL_FLYING -> startingToGlide = true;
        }
    }

    boolean lastTickMove;

    public void onTick() {
        swapping = false;
        dropping = false;
        attacking = false;
        interacting = false;
        releasing = false;
        digging = false;
        placing = false;
        using = false;
        sprinting = false;
        startingToGlide = false;
    }

    public void onPacketTick(Event<PlayerMoveC2SPacket> event) {
        lastTickMove = true;
        PlayerMoveC2SPacketAccess.Cause cause =
                PlayerMoveC2SPacketAccess.of(event.context).getCause();
        if (cause != PlayerMoveC2SPacketAccess.Cause.SET_BACK && cause != PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP) {
            onTick();
        }
    }

    public void onTickEnd(Event<ClientTickEndC2SPacket> event) {
        if (lastTickMove) {
            lastTickMove = false;
        } else {
            if (ViaFabricPlusHooks.isSupportEndTick()) {
                onTick();
            }
        }
    }
}
