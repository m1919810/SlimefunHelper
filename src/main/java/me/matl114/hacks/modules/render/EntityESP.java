package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderMode;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

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

    List<Entity> entities = new ArrayList<>();

    public void onTick(Event<ClientPlayerEntity> event) {
        entities = new ArrayList<>();
        boolean enable = this.enable.get();

        var whitelist = whiteList.get().set();
        var glowMap = highLightSettings.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.gameRenderer.getCamera().getFocusedEntity()) continue;
            if (entity == null || entity.isRemoved()) {
                continue;
            } else {
                EntityInternalAccess<?> access = EntityInternalAccess.of(entity);
                int renderLevel = access.renderTrackedLevel();
                if (!glowMap.getEntryValueOr(entity.getType(), false)) {
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
                    if (glowMap.getEntryValueOr(entity.getType(), false)) {
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

    public void onRender3D(Event<MatrixStack> stackE) {
        if (checkNull()) return;

        if (enable.get() && renderMode.get().isIn(RenderMode.RENDER_3D)) {
            var stack = stackE.context;
            float tickDelta = stackE.getArgs(0);

            RenderUtils.startDrawVirtual(stack);
            try {
                render(stack, tickDelta);
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    public void onRender2D(Event<VDrawContext> event) {
        if (checkNull()) return;
        if (enable.get() && renderMode.get().isIn(RenderMode.RENDER_2D)) {
            float tickDelta = event.getArgs(0);
            render(event.context, tickDelta);
        }
    }

    public void render(Object object, float tickDelta) {
        var lineMap = renderTraceSettings.get();
        var boxMap = renderBoxSettings.get();
        var renderBox = RenderCollectors.createBoxCollector(true, false, false);
        var renderTrace = RenderCollectors.createTracerCollector();
        List<Entity> entities = this.entities;
        for (var entity : entities) {
            Color color = getShaderColorByEntityType(entity);
            if (color != null) {

                Box box = RenderUtils.getLerpedBox(entity, tickDelta);
                if (boxMap.getEntryValueOr(entity.getType(), false)) {
                    renderBox.submit(box, color.getRGB());
                }
                if (lineMap.getEntryValueOr(entity.getType(), false)) {
                    renderTrace.submit(box.getCenter(), color.getRGB());
                }
            }
        }
        if (object instanceof MatrixStack stack) {
            renderBox.render3D(stack);
            renderTrace.render3D(stack);
        } else if (object instanceof VDrawContext vdraw) {
            renderBox.render2D(vdraw);
            renderTrace.render2D(vdraw);
        }
        renderBox.clear();
        renderTrace.clear();
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
