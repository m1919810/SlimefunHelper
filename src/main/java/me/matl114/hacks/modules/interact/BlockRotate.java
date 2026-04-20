package me.matl114.hacks.modules.interact;

import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.NetworkUtils;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public class BlockRotate extends BaseModule {
    public static final String[] ENABLE = makePath("block-rotate.test.enable");

    public static final String[] BYPASS = makePath("block-rotate.rotate-bypass-mode");

    public BlockRotate() {}

    public final FlagRef enable = flagBuilder(Configs.INTERACT_CONFIG, ENABLE).build();

    public final EnumRef<Configs.BypassMode> bypassMode = builder(
                    Configs.INTERACT_CONFIG, BYPASS, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onPreSendInteractBlockRotateDemo);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    // can not bypass
    public void onPreSendInteractBlockRotateDemo(Event<PlayerInteractBlockC2SPacket> e) {
        if (enable.get()) {
            if (bypassMode.get() == Configs.BypassMode.BYPASS_GRIM) {
                // to ensure the rotate is successfully done
                // use a wrong sequence id to ensure that this packet cancelled by grimac
                Listener.sendPacketNoEvents(
                        new PlayerInteractBlockC2SPacket(e.context.getHand(), e.context.getBlockHitResult(), -1));
            }
            mc.getNetworkHandler()
                    .sendPacket(new PlayerInteractItemC2SPacket(
                            Hand.MAIN_HAND, e.context.getSequence(), mc.player.getYaw() + 180, mc.player.getPitch()));
            PlayerInteractBlockC2SPacketAccess.of(e.context).setSequence(NetworkUtils.generateNextSequence());
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> e) {
        switch (e.context.getValue()) {
            case AC_GRIM -> bypassMode.set(Configs.BypassMode.BYPASS_GRIM);
            default -> bypassMode.set(Configs.BypassMode.NO_BYPASS);
        }
    }
}
