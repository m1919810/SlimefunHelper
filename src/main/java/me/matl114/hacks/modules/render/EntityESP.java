package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class EntityESP extends BaseModule {
    public static final String[] DETECT_ENTITY = {"detect-entity", "detect-entity"};
    public static final String[] DETECT_ENTITY_TOGGLE = {"detect-entity", "detect-entity-hotkey"};
    public static final String[] DETECT_SPAWN_WHITELIST = {"detect-entity", "spawn-whitelist"};
    public static final String[] LOG_ON_SCREEN = {"detect-entity", "log-to-chat"};
    public static final String[] RAYTRACE_ENTITY = {"detect-entity", "ray-trace-entity"};
    public static final String[] ENTITY_HITBOX = {"detect-entity", "render-trace-hit-box"};
    public static final String[] ENTITY_GLOW = {"detect-entity", "entity-glow-effect"};

    public EntityESP() {}

    Set<EntityType<?>> types = new HashSet<>();

    public void updateWhiteList(String value) {
        Set<EntityType<?>> set = new HashSet<>();
        EntityUtils.parseEntityWhiteList(value.replace(',', '|'), set);
        types = set;
    }

    public final FlagRef enable =
            flagBuilder(Configs.RENDER_CONFIG, DETECT_ENTITY).build();

    public final KeyBindRef hotkeyToggle = toggleHotkey(
                    Configs.RENDER_CONFIG,
                    DETECT_ENTITY_TOGGLE,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_P),
                    DETECT_ENTITY)
            .build();

    public final StringRef whiteList = builder(Configs.RENDER_CONFIG, StringRef.TYPE)
            .path(DETECT_SPAWN_WHITELIST)
            .defaultValue("player,wither")
            .validator(s -> Configs.REGEX_VALIDATOR.test(s.replace(',', '|')))
            .updateListener(this::updateWhiteList)
            .build();

    public final FlagRef logEntity = builder(Configs.RENDER_CONFIG, FlagRef.TYPE)
            .path(LOG_ON_SCREEN)
            .defaultValue(false)
            .build();

    public final FlagRef lineTrace =
            flagBuilder(Configs.RENDER_CONFIG, RAYTRACE_ENTITY).build();

    public final FlagRef boxTrace =
            flagBuilder(Configs.RENDER_CONFIG, ENTITY_HITBOX).build();

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
    }

    public void onEntitySpawn(Event<EntitySpawnS2CPacket> packetEvent) {
        // Debug.info("check entity", packet.getEntityType());
        var packet = packetEvent.context();
        if (enable.get()) {
            if (types.contains(packet.getEntityType())) {
                EntityType<?> type = packet.getEntityType();
                if (logEntity.get()) {
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
    }

    public void onEntityRemove(Event<EntitiesDestroyS2CPacket> packetEvent) {
        if (enable.get()) {
            var packet = packetEvent.context();
            if (mc.world != null) {
                Set<Entity> removing = new LinkedHashSet<>();
                for (int i : packet.getEntityIds()) {
                    Entity entity = mc.world.getEntityById(i);
                    if (entity == null) continue;
                    if (types.contains(entity.getType())) {
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

    public void onRender(Event<MatrixStack> stackE) {
        if (mc.world == null || mc.player == null) return;
        var stack = stackE.context;
        boolean enable = this.enable.get();

        float tickDelta = (float) stackE.getArgs(0);
        var whitelist = types;
        boolean doLineTrace = lineTrace.get();
        boolean doBoxTrace = boxTrace.get();

        RenderUtils.startDrawVirtual(stack);
        try {
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

                        Box box = RenderUtils.getLerpedBox(entity, tickDelta); //  entity.getBoundingBox();
                        if (doLineTrace) {
                            Vec3d center = box.getCenter();
                            Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
                            RenderUtils.drawLineVirtualCameraCoord(
                                    stack,
                                    cursorPos,
                                    center.subtract(RenderUtils.getCameraPos()),
                                    getShaderColorByEntityType(entity));
                        }
                        if (doBoxTrace) {
                            RenderUtils.drawOutlinedBox(
                                    stack, box.getMinPos(), box.getMaxPos(), getShaderColorByEntityType(entity));
                        }
                    }
                    if (renderLevel == EntityInternalAccess.RENDER_LEVEL_DISABLE) {
                        if (whitelist.contains(entity.getType())) {
                            access.markRenderTracked(EntityInternalAccess.RENDER_LEVEL_WHITELIST);
                        }
                    }
                }
            }
        } finally {
            RenderUtils.stopDrawVirtual(stack);
        }
    }

    private static double calculateDistance(double x1, double y1, double z1) {
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            return Math.sqrt(player.getPos().squaredDistanceTo(x1, y1, z1));
        }
        return -1.0f;
    }

    private static Color getShaderColorByEntityType(Entity entity) {
        if (entity instanceof PlayerEntity entity1) {
            return Color.YELLOW;
        }
        if (!(entity instanceof LivingEntity)) {
            return Color.RED;
        }
        return switch (entity.getType().getSpawnGroup()) {
            case WATER_CREATURE, CREATURE, AXOLOTLS, AMBIENT, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE -> Color.GREEN;
            default -> Color.RED;
        };
    }
}
