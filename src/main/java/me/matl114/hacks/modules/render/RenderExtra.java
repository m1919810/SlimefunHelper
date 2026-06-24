package me.matl114.hacks.modules.render;

import java.net.URI;
import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.Debug;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class RenderExtra extends BaseModule {
    public static RenderExtra INSTANCE;
    public final ModulePath resource = makePath(Configs.RENDER_CONFIG, "resource");
    public final ModulePath serverResource = resource.add("server");
    public final ModulePath render = makePath(Configs.RENDER_CONFIG, "render");
    public final ModulePath effectSetting = render.add("eff-setting");

    public RenderExtra() {
        INSTANCE = this;
    }

    public final FlagRef enableRejectResourcePack =
            flagBuilder(serverResource.add("ignore-server-request")).build();

    public final FlagRef nightVision =
            builder(render.add("nightvision"), Boolean.class).defaultValue(true).build();

    public final FlagRef noEffect =
            builder(render.add("no-effect"), Boolean.class).defaultValue(true).build();

    public final FlagRef noNausea =
            builder(render.add("no-nausea"), Boolean.class).defaultValue(true).build();

    public final FlagRef noEffectForce =
            flagBuilder(effectSetting.add("force-no")).build();

    public final FlagRef noOverlay = flagBuilder(render.add("no-overlay")).build();

    public final FlagRef noFireOverlay =
            flagBuilder(render.add("no-fire-overlay")).build();

    public final FlagRef noBobWorld = builder(render.add("no-world-bob-view"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final NBTRef<RegistryRegex<StatusEffect>> noEffectTypes = builder(
                    effectSetting.add("types"), NBTType.<RegistryRegex<StatusEffect>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^(blindness|darkness|nausea)$"), Registries.STATUS_EFFECT))
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
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusEffectS2CPacket.class), this::doCancelEffect);
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

    public void doCancelEffect(Event<EntityStatusEffectS2CPacket> packet) {
        if (mc.player != null && packet.context.getEntityId() == mc.player.getId()) {
            RegistryEntry<StatusEffect> reg = packet.context.getEffectId();
            if (noEffectForce.get() && noEffectTypes.get().test(reg)) {
                packet.cancel();
            }
        }
    }

    public void onApplyBobView(Event<MatrixStack> event) {
        if (noBobWorld.get()) {
            event.cancel();
        }
    }
}
