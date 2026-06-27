package me.matl114.hacks.modules.render;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VRecord;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityLog extends BaseModule {
    public final ModulePath entityLog = makePath(Configs.RENDER_CONFIG, "detect-entity.entity-log");

    public EntityLog() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(entityLog.add("enable")).build();
    public final KeyBindRef hotkeyToggle = toggleHotkey(
                    entityLog.add("hotkey"), new MultiKeyBind(), entityLog.add("enable"))
            .build();

    public final NBTRef<RegistryRegex<EntityType<?>>> whiteList = builder(
                    entityLog.add("whitelist"), RegistryRegex.<EntityType<?>>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("player"), Registries.ENTITY_TYPE))
            .build();

    public final FlagRef chatLog = builder(entityLog.add("log-entity-to-chat"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef renderLogPosition =
            flagBuilder(entityLog.add("render-log-players")).build();

    public final FlagRef ignoreOutOfChunkLog = builder(entityLog.add("ignore-out-of-chunk-log"), Boolean.class)
            .defaultValue(true)
            .build();

    public Map<UUID, Pair<Box, Vec3d>> offLinePos = new LinkedHashMap<>();

    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntitySpawnS2CPacket.class), this::onEntitySpawn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntitiesDestroyS2CPacket.class), this::onEntityRemove);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
    }

    public void onEntitySpawn(Event<EntitySpawnS2CPacket> packetEvent) {
        // Debug.info("check entity", packet.getEntityType());
        var packet = packetEvent.context();
        if (enable.get()) {
            if (whiteList.get().test(packet.getEntityType())) {
                EntityType<?> type = packet.getEntityType();
                if (type == EntityType.PLAYER) {
                    if (chatLog.get()) {
                        Text text = null;
                        if (MinecraftClient.getInstance().world != null) {
                            PlayerListEntry entry = MinecraftClient.getInstance()
                                    .getNetworkHandler()
                                    .getPlayerListEntry(packet.getUuid());
                            if (entry != null) {
                                text = Text.literal(VRecord.getName(entry.getProfile()))
                                        .formatted(Formatting.GREEN);
                            }
                        }

                        Debug.chat(
                                "Player ",
                                text == null ? "" : text,
                                "spawn at position ",
                                ChatUtils.getDisplayedLocation(packet.getX(), packet.getY(), packet.getZ()),
                                ",distance: %.2f"
                                        .formatted(calculateDistance(packet.getX(), packet.getY(), packet.getZ())));
                        // Debug.chat("Player Entity Id ", packet.getEntityId());
                    }
                    offLinePos.remove(packet.getUuid());
                } else {
                    // if(LivingEntity.class.isAssignableFrom( packet.getEntityType().getBaseClass())){
                    // only log the living Entity; the common Entities are mostly functional and are noisy
                    if (chatLog.get()) {
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
                        if (chatLog.get()) {
                            Debug.chat(
                                    "Player",
                                    pl.getName(),
                                    "disappear at position ",
                                    ChatUtils.getDisplayedLocation(entity.getX(), entity.getY(), entity.getZ()),
                                    ",distance: %.2f"
                                            .formatted(calculateDistance(entity.getX(), entity.getY(), entity.getZ())));
                        }
                        onPlayerDisappear(pl);
                    } else {
                        if (chatLog.get()) {
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

    public void onPlayerDisappear(PlayerEntity player) {}

    public void onWorldSwitch(Event<World> eventWorld) {}
}
