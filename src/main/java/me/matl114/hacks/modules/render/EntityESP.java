package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.*;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render2D;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderMode;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.render.RenderCollector;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class EntityESP extends BaseModule {
    public final ModulePath entityRoot = makePath(Configs.RENDER_CONFIG, "detect-entity");
    public final ModulePath entityEsp = entityRoot.add("entity-esp");

    public EntityESP() {
        super("EntityESP");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(entityEsp.add("enable")).build();

    public final KeyBindRef hotkeyToggle = toggleHotkey(
                    Configs.RENDER_CONFIG,
                    entityEsp.add("hotkey").toPath(),
                    new MultiKeyBind(),
                    entityEsp.add("enable").toPath())
            .build();

    public final EnumRef<RenderMode> renderMode = builder(entityEsp.add("render-mode"), RenderMode.class)
            .defaultValue(RenderMode.RENDER_3D)
            .build();

    public final NBTRef<EntrySet<EntityType<?>>> whiteList = builder(
                    Configs.RENDER_CONFIG, EntrySet.<EntityType<?>>parameter())
            .path(entityEsp.add("whitelist").toPath())
            .defaultValue(new EntrySet<>(new Regex("player,wither"), Registries.ENTITY_TYPE))
            .build();

    public final NBTRef<EntryPrimitiveMap<EntityType<?>, TextColor>> renderColor = builder(
                    entityEsp.add("color"),
                    NBTType.<EntryPrimitiveMap<EntityType<?>, TextColor>>parameter(EntryPrimitiveMap.class))
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.ENTITY_TYPE,
                    NBTTypes.COLOR_TYPE,
                    Map.of(
                            EntityType.PLAYER, Objects.requireNonNull(TextColor.fromFormatting(Formatting.YELLOW)),
                            EntityType.ARMOR_STAND, Objects.requireNonNull(TextColor.fromFormatting(Formatting.GREEN))),
                    TextColor.fromFormatting(Formatting.RED)))
            .build();

    public final NBTRef<EntryPrimitiveMap<EntityType<?>, Boolean>> renderBoxSettings = builder(
                    entityEsp.add("boxing-option"),
                    NBTType.<EntryPrimitiveMap<EntityType<?>, Boolean>>parameter(EntryPrimitiveMap.class))
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.ENTITY_TYPE,
                    NBTTypes.BOOLEAN_TYPE,
                    Map.of(
                            EntityType.PLAYER, true,
                            EntityType.END_CRYSTAL, true,
                            EntityType.WITHER, true),
                    false))
            .build();

    public final NBTRef<EntryPrimitiveMap<EntityType<?>, Boolean>> renderTraceSettings = builder(
                    entityEsp.add("trace-option"),
                    NBTType.<EntryPrimitiveMap<EntityType<?>, Boolean>>parameter(EntryPrimitiveMap.class))
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.ENTITY_TYPE,
                    NBTTypes.BOOLEAN_TYPE,
                    Map.of(
                            EntityType.PLAYER, true,
                            EntityType.END_CRYSTAL, false,
                            EntityType.WITHER, true),
                    false))
            .build();

    public final NBTRef<EntryPrimitiveMap<EntityType<?>, Boolean>> highLightSettings = builder(
                    entityEsp.add("highlight-option"),
                    NBTType.<EntryPrimitiveMap<EntityType<?>, Boolean>>parameter(EntryPrimitiveMap.class))
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.ENTITY_TYPE,
                    NBTTypes.BOOLEAN_TYPE,
                    Map.of(
                            EntityType.PLAYER, true,
                            EntityType.END_CRYSTAL, true,
                            EntityType.WITHER, true),
                    true))
            .build();

    //    public final NBTRef<TracingOption> traceOption = builder(entityEsp.add("tracing-option"), TracingOption.class)
    //            .defaultValue(new TracingOption(true, false))
    //            .build();
    //    public final FlagRef glowEntity = flagBuilder(entityEsp.add("glow-effect")).build();

    @Override
    public void registerAll() {
        super.registerAll();

        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(RenderListener.getRender2DEvent(), this::onRender2D);
        registerListener(Listener.getPostGameTick(), this::onTick);
    }

    private final RenderCollector<Entity> renderOutline = RenderCollectors.createEntityBoxCollector(true, false, false);
    private final RenderCollector<Entity> renderFace = RenderCollectors.createEntityFaceCollector();
    private final RenderCollector<Vec3d> renderTrace = RenderCollectors.createTracerCollector();

    public void onTick(Event<ClientPlayerEntity> event) {
        renderOutline.clear();
        renderFace.clear();
        renderTrace.clear();

        boolean enable = this.enable.get();

        var whitelist = whiteList.get().set();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.gameRenderer.getCamera().getFocusedEntity()) continue;
            if (entity == null || entity.isRemoved()) {
                continue;
            } else {
                EntityInternalAccess<?> access = EntityInternalAccess.of(entity);
                int renderLevel = access.renderTrackedLevel();
                if (renderLevel == EntityInternalAccess.RENDER_LEVEL_WHITELIST) {
                    if (!whitelist.contains(entity.getType())) {
                        access.markRenderTracked(EntityInternalAccess.RENDER_LEVEL_DISABLE);
                        continue;
                    }
                }
                if ((renderLevel == EntityInternalAccess.RENDER_LEVEL_WHITELIST && enable)
                        || renderLevel == EntityInternalAccess.RENDER_LEVEL_FORCE) {
                    updateRenderCollectors(entity);
                }
                if (renderLevel == EntityInternalAccess.RENDER_LEVEL_DISABLE) {
                    if (whitelist.contains(entity.getType())) {
                        access.markRenderTracked(EntityInternalAccess.RENDER_LEVEL_WHITELIST);
                    }
                }
            }
        }
    }

    public void onRender3D(Event<Render3D> stackE) {
        if (checkNull()) return;

        if (enable.get() && renderMode.get().isIn(RenderMode.RENDER_3D)) {
            var stack = stackE.context.stack();
            float tickDelta = stackE.context.partialTicks();

            RenderUtils.startDrawVirtual(stack);
            try {
                render(stack, tickDelta);
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    public void onRender2D(Event<Render2D> event) {
        if (checkNull()) return;
        if (enable.get() && renderMode.get().isIn(RenderMode.RENDER_2D)) {
            float tickDelta = event.context.partialTicks();
            render(event.context.drawContext(), tickDelta);
        }
    }

    public void render(Object object, float tickDelta) {
        if (object instanceof MatrixStack stack) {
            renderFace.render3D(stack, tickDelta);
            renderOutline.render3D(stack, tickDelta);
            renderTrace.render3D(stack, tickDelta);
        } else if (object instanceof VDrawContext vdraw) {
            renderFace.render2D(vdraw, tickDelta);
            renderOutline.render2D(vdraw, tickDelta);
            renderTrace.render2D(vdraw, tickDelta);
        }
    }

    private void updateRenderCollectors(Entity entity) {
        Color color = getShaderColorByEntityType(entity);
        if (color == null) return;

        if (renderBoxSettings.get().getEntryValueOr(entity.getType(), false)) {
            renderOutline.submit(entity, color.getRGB());
        }
        if (highLightSettings.get().getEntryValueOr(entity.getType(), false)) {
            renderFace.submit(entity, ColorUtils.withAlphaInt(color.getRGB(), 64));
        }
        if (renderTraceSettings.get().getEntryValueOr(entity.getType(), false)) {
            renderTrace.submit(RenderUtils.getLerpedBox(entity, 0.0F).getCenter(), color.getRGB());
        }
    }

    private Color getShaderColorByEntityType(Entity entity) {
        EntityType<?> type = entity.getType();
        TextColor color = renderColor.get().getEntryValue(type);
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
