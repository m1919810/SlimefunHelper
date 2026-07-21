package me.matl114.hacks.modules.render;

import java.net.URI;
import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class RenderExtra extends BaseModule {
    public static RenderExtra INSTANCE;
    public final ModulePath resource = makePath(Configs.RENDER_CONFIG, "resource");
    public final ModulePath serverResource = resource.add("server");
    public final ModulePath render = makePath(Configs.RENDER_CONFIG, "render");

    public RenderExtra() {
        INSTANCE = this;
    }

    public final FlagRef enableRejectResourcePack =
            flagBuilder(serverResource.add("ignore-server-request")).build();

    public final FlagRef nightVision =
            builder(render.add("nightvision"), Boolean.class).defaultValue(true).build();

    public final FlagRef noBobWorld = builder(render.add("no-world-bob-view"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef noWurstHud =
            flagBuilder(render.add("disable-wurst-hud")).build();

    public final FlagRef enhancedDebugHud =
            flagBuilder(render.add("enhanced-debug-hud")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(ResourcePackSendS2CPacket.class), this::onResourceRequest);

        registerListener(RenderListener.getApplyWorldBobView(), this::onApplyBobView);
    }

    public void onResourceRequest(Event<ResourcePackSendS2CPacket> resourceEvent) {
        // note that resourcePack may be sent during configuration time
        if (enableRejectResourcePack.get()) {
            ClientConnection connection = resourceEvent.getArgs(0);
            var sendPacket = resourceEvent.context();
            connection.send(
                    new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            connection.send(
                    new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
            connection.send(new ResourcePackStatusC2SPacket(
                    sendPacket.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            Debug.chat(
                    Text.literal("Successfully reject server resourcepack").formatted(Formatting.GREEN),
                    sendPacket.id());
            Style st = Style.EMPTY;
            try {
                st = st.withClickEvent(new ClickEvent.OpenUrl(URI.create(sendPacket.url())));
            } catch (Exception e) {
            }
            Debug.chat(
                    Text.literal("Download url:").formatted(Formatting.GREEN),
                    Text.literal(sendPacket.url()).setStyle(st).formatted(Formatting.YELLOW));
            resourceEvent.cancel();
        }
    }

    public void onApplyBobView(Event<MatrixStack> event) {
        if (noBobWorld.get()) {
            event.cancel();
        }
    }
}
