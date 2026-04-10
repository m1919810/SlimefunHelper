package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.*;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class EntityESP extends BaseModule {
    public static final String[] DETECT_ENTITY = {"detect-entity", "entity-esp", "enable"};
    public static final String[] DETECT_ENTITY_TOGGLE = {"detect-entity", "entity-esp", "hotkey"};
    public static final String[] DETECT_SPAWN_WHITELIST = {"detect-entity", "entity-esp", "whitelist"};
    public static final String[] RENDER_COLOR = {"detect-entity", "entity-esp", "color"};
    public static final String[] LOG_ON_SCREEN = {"detect-entity", "entity-esp", "log-to-chat"};
    public static final String[] ENTITY_TRACE = {"detect-entity", "entity-esp", "tracing-option"};
    public static final String[] ENTITY_GLOW = {"detect-entity", "entity-esp", "glow-effect"};

    public EntityESP() {}

    public final FlagRef enable =
            flagBuilder(Configs.RENDER_CONFIG, DETECT_ENTITY).build();

    public final KeyBindRef hotkeyToggle = toggleHotkey(
                    Configs.RENDER_CONFIG,
                    DETECT_ENTITY_TOGGLE,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_P),
                    DETECT_ENTITY)
            .build();

    public final NBTRef<RegistryRegex<EntityType<?>>> whiteList = builder(
                    Configs.RENDER_CONFIG, RegistryRegex.<EntityType<?>>parameter())
            .path(DETECT_SPAWN_WHITELIST)
            .defaultValue(new RegistryRegex<>(new Regex("player,wither"), Registries.ENTITY_TYPE))
            .build();

    public final NBTRef<EntryPrimitiveMap<EntityType<?>, TextColor>> renderColor = builder(
                    Configs.RENDER_CONFIG,
                    RENDER_COLOR,
                    NBTType.<EntryPrimitiveMap<EntityType<?>, TextColor>>parameter(EntryPrimitiveMap.class))
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.ENTITY_TYPE,
                    NBTTypes.COLOR_TYPE,
                    Map.of(
                            EntityType.PLAYER, Objects.requireNonNull(TextColor.fromFormatting(Formatting.YELLOW)),
                            EntityType.ARMOR_STAND, Objects.requireNonNull(TextColor.fromFormatting(Formatting.GREEN))),
                    TextColor.fromFormatting(Formatting.RED)))
            .build();

    public final FlagRef logEntity = builder(Configs.RENDER_CONFIG, FlagRef.TYPE)
            .path(LOG_ON_SCREEN)
            .defaultValue(false)
            .build();

    public final NBTRef<TracingOption> traceOption = builder(Configs.RENDER_CONFIG, ENTITY_TRACE, TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();
    public final FlagRef glowEntity =
            flagBuilder(Configs.RENDER_CONFIG, ENTITY_GLOW).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntitySpawnS2CPacket.class), this::onEntitySpawn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntitiesDestroyS2CPacket.class), this::onEntityRemove);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(Listener.getPostGameTick(), this::onTick);
    }

    public void onEntitySpawn(Event<EntitySpawnS2CPacket> packetEvent) {
        // Debug.info("check entity", packet.getEntityType());
        var packet = packetEvent.context();
        if (enable.get()) {
            if (whiteList.get().test(packet.getEntityType())) {
                EntityType<?> type = packet.getEntityType();
                if (logEntity.get()) {
                    if (type == EntityType.PLAYER) {
                        Text text = null;
                        if (MinecraftClient.getInstance().world != null) {
                            PlayerListEntry entry = MinecraftClient.getInstance()
                                    .getNetworkHandler()
                                    .getPlayerListEntry(packet.getUuid());
                            if (entry != null) {
                                text = Text.literal(entry.getProfile().getName())
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
                if (logEntity.get()) {
                    for (var entity : removing) {
                        Debug.chat(
                                "Entity",
                                entity.getType().getName(),
                                entity instanceof PlayerEntity pl
                                        ? pl.getName()
                                        : (entity.hasCustomName() ? entity.getCustomName() : ""),
                                "disappear at position ",
                                ChatUtils.getDisplayedLocation(entity.getX(), entity.getY(), entity.getZ()),
                                ",distance: %.2f"
                                        .formatted(calculateDistance(entity.getX(), entity.getY(), entity.getZ())));
                    }
                }
            }
        }
    }

    List<Entity> entities = new ArrayList<>();

    public void onTick(Event<ClientPlayerEntity> event) {
        entities = new ArrayList<>();
        boolean enable = this.enable.get();

        var whitelist = whiteList.get().getFilterValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.gameRenderer.getCamera().getFocusedEntity()) continue;
            if (entity == null || entity.isRemoved()) {
                continue;
            } else {
                EntityInternalAccess<?> access = EntityInternalAccess.of(entity);
                int renderLevel = access.renderTrackedLevel();
                if (!glowEntity.get()) {
                    access.setGlow0(false);
                }
                if (renderLevel == EntityInternalAccess.RENDER_LEVEL_WHITELIST) {
                    if (!whitelist.contains(entity.getType())) {
                        access.setGlow0(false);
                        access.markRenderTracked(EntityInternalAccess.RENDER_LEVEL_DISABLE);
                        continue;
                    }
                }
                if ((renderLevel == EntityInternalAccess.RENDER_LEVEL_WHITELIST && enable)
                        || renderLevel == EntityInternalAccess.RENDER_LEVEL_FORCE) {
                    if (glowEntity.get()) {
                        if (!entity.isGlowing()) {
                            access.setGlow0(true);
                        }
                    }
                    entities.add(entity);
                }
                if (renderLevel == EntityInternalAccess.RENDER_LEVEL_DISABLE) {
                    if (whitelist.contains(entity.getType())) {
                        access.markRenderTracked(EntityInternalAccess.RENDER_LEVEL_WHITELIST);
                    }
                }
            }
        }
    }

    public void onRender(Event<MatrixStack> stackE) {
        if (checkNull()) return;

        if (enable.get()) {
            var stack = stackE.context;
            float tickDelta = stackE.getArgs(0);
            boolean doLineTrace = traceOption.get().line(); // .get();
            boolean doBoxTrace = traceOption.get().box();
            RenderUtils.startDrawVirtual(stack);
            try {
                Vec3d traceOrigin = RenderUtils.getTracerOrigin(tickDelta);
                Vec3d camera = RenderUtils.getCameraPos();
                VRender.getInstance().createLinesLayer((op, vp) -> {
                    List<Entity> entities = this.entities;
                    for (var entity : entities) {
                        //  entity.getBoundingBox();
                        Color color = getShaderColorByEntityType(entity);
                        if (color != null) {
                            Box box =
                                    RenderUtils.getLerpedBox(entity, tickDelta).offset(camera.negate());
                            if (doLineTrace) {
                                Vec3d center = box.getCenter();
                                op.drawLine(stack, vp, traceOrigin, center, color.getRGB());
                            }
                            if (doBoxTrace) {
                                op.drawOutlinedBox(stack, vp, box.getMinPos(), box.getMaxPos(), color.getRGB());
                            }
                        }
                    }
                });
            } finally {
                RenderUtils.stopDrawVirtual(stack);
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

    private Color getShaderColorByEntityType(Entity entity) {
        EntityType<?> type = entity.getType();
        TextColor color = renderColor.get().getOrDefault(type);
        return color != null ? new Color(color.getRgb()) : null;
        //        if (entity instanceof PlayerEntity entity1) {
        //            return Color.YELLOW;
        //        }
        //        if (!(entity instanceof LivingEntity)) {
        //            return Color.RED;
        //        }
        //        return switch (entity.getType().getSpawnGroup()) {
        //            case WATER_CREATURE, CREATURE, AXOLOTLS, AMBIENT, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE ->
        // Color.GREEN;
        //            default -> Color.RED;
        //        };
    }
}
