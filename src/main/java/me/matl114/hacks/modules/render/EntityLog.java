package me.matl114.hacks.modules.render;

import java.util.LinkedHashSet;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class EntityLog extends BaseModule {
    public static final String[] LOG_ON_SCREEN = {"detect-entity", "entity-log", "enable"};
    public static final String[] DETECT_ENTITY_TOGGLE = {"detect-entity", "entity-log", "hotkey"};
    public static final String[] DETECT_SPAWN_WHITELIST = {"detect-entity", "entity-log", "whitelist"};
    public final FlagRef enable = builder(Configs.RENDER_CONFIG, FlagRef.TYPE)
            .path(LOG_ON_SCREEN)
            .defaultValue(false)
            .build();
    public final KeyBindRef hotkeyToggle = toggleHotkey(
                    Configs.RENDER_CONFIG, DETECT_ENTITY_TOGGLE, new MultiKeyBind(), LOG_ON_SCREEN)
            .build();
    public final NBTRef<RegistryRegex<EntityType<?>>> whiteList = builder(
                    Configs.RENDER_CONFIG, RegistryRegex.<EntityType<?>>parameter())
            .path(DETECT_SPAWN_WHITELIST)
            .defaultValue(new RegistryRegex<>(new Regex("player"), Registries.ENTITY_TYPE))
            .build();

    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntitySpawnS2CPacket.class), this::onEntitySpawn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntitiesDestroyS2CPacket.class), this::onEntityRemove);
    }

    public void onEntitySpawn(Event<EntitySpawnS2CPacket> packetEvent) {
        // Debug.info("check entity", packet.getEntityType());
        var packet = packetEvent.context();
        if (enable.get()) {
            if (whiteList.get().test(packet.getEntityType())) {
                EntityType<?> type = packet.getEntityType();

                if (type == EntityType.PLAYER) {
                    Text text = null;
                    if (MinecraftClient.getInstance().world != null) {
                        PlayerListEntry entry = MinecraftClient.getInstance()
                                .getNetworkHandler()
                                .getPlayerListEntry(packet.getUuid());
                        if (entry != null) {
                            text = Text.literal(entry.getProfile().name()).formatted(Formatting.GREEN);
                        }
                    }

                    Debug.chat(
                            "Player ",
                            text == null ? "" : text,
                            "spawn at position ",
                            ChatUtils.getDisplayedLocation(packet.getX(), packet.getY(), packet.getZ()),
                            ",distance: %.2f"
                                    .formatted(calculateDistance(packet.getX(), packet.getY(), packet.getZ())));
                    Debug.chat("Player Entity Id ", packet.getEntityId());
                } else {
                    // if(LivingEntity.class.isAssignableFrom( packet.getEntityType().getBaseClass())){
                    // only log the living Entity; the common Entities are mostly functional and are noisy
                    Debug.chat(
                            "Entity",
                            packet.getEntityType().getName(),
                            "spawn at position ",
                            ChatUtils.getDisplayedLocation(packet.getX(), packet.getY(), packet.getZ()),
                            ",distance: %.2f"
                                    .formatted(calculateDistance(packet.getX(), packet.getY(), packet.getZ())));
                    // }

                }
            }
        }
    }

    private static double calculateDistance(double x1, double y1, double z1) {
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            return Math.sqrt(player.getPos().squaredDistanceTo(x1, y1, z1));
        }
        return -1.0f;
    }

    public void onEntityRemove(Event<EntitiesDestroyS2CPacket> packetEvent) {
        if (enable.get()) {
            var packet = packetEvent.context();
            if (mc.world != null) {
                Set<Entity> removing = new LinkedHashSet<>();
                for (int i : packet.getEntityIds()) {
                    Entity entity = mc.world.getEntityById(i);
                    if (entity == null) continue;
                    if (whiteList.get().test(entity.getType())) {
                        removing.add(entity);
                    }
                }

                for (var entity : removing) {
                    if (entity instanceof PlayerEntity pl) {
                        Debug.chat(
                                "Player",
                                pl.getName(),
                                "disappear at position ",
                                ChatUtils.getDisplayedLocation(entity.getX(), entity.getY(), entity.getZ()),
                                ",distance: %.2f"
                                        .formatted(calculateDistance(entity.getX(), entity.getY(), entity.getZ())));
                    } else {
                        Debug.chat(
                                "Entity",
                                entity.getType().getName(),
                                (entity.hasCustomName() ? entity.getCustomName() : ""),
                                "disappear at position ",
                                ChatUtils.getDisplayedLocation(entity.getX(), entity.getY(), entity.getZ()),
                                ",distance: %.2f"
                                        .formatted(calculateDistance(entity.getX(), entity.getY(), entity.getZ())));
                    }
                }
            }
        }
    }
}
