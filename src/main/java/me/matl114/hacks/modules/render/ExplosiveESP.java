package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.CombatManager;
import me.matl114.hacks.utils.config.TracingOption;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.ExplosionUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ExplosiveESP extends BaseModule {
    public ExplosiveESP() {
        super("ExplosiveESP");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.RENDER_CONFIG, "combat-render.explosive-esp");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final IntRef distance = intBuilder(root.add("distance"))
            .defaultValue(16)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final NBTRef<TracingOption> option = builder(root.add("options"), TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    public final NBTRef<WrapColor> anchorColor = builder(root.add("anchor-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.GOLD))
            .build();

    public final NBTRef<WrapColor> crystalColor = builder(root.add("crystal-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.LIGHT_PURPLE))
            .build();

    private final RenderCollector<Box> boxOutlineCollector = RenderCollectors.createBoxCollector(true, false, false);
    private final RenderCollector<RenderElements.Text> textCollector = RenderCollectors.createTextCollector();
    private final RenderCollector<Vec3d> traceCollector = RenderCollectors.createTracerCollector();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(CombatManager.getRequestEnableEvent(), this::onRequestCombatService);
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        boxOutlineCollector.clear();
        textCollector.clear();
        traceCollector.clear();
        if (checkNull() || !enable.get()) {
            return;
        }
        var anchors = CombatManager.INSTANCE.trackedExplosives;
        var crystals = CombatManager.INSTANCE.trackedEndCrystals;
        double maxDistanceSq = MathUtils.s2(distance.get());
        TracingOption tracingOption = option.get();
        int anchorRgb = anchorColor.get().color().getRgb();
        int crystalRgb = crystalColor.get().color().getRgb();

        for (var pos : anchors.keySet()) {
            if (pos.getSquaredDistance(mc.player.getPos()) > maxDistanceSq) {
                continue;
            }
            Box box = new Box(pos);
            if (tracingOption.box()) {
                boxOutlineCollector.submit(box, ColorUtils.withAlphaInt(anchorRgb, 255));
            }
            if (tracingOption.line()) {
                traceCollector.submit(box.getCenter(), ColorUtils.withAlphaInt(anchorRgb, 255));
            }
            submitDamageText(pos.toCenterPos(), 5.0F, anchorRgb);
        }

        for (var crystal : crystals) {
            if (crystal.getPos().squaredDistanceTo(mc.player.getPos()) > maxDistanceSq) {
                continue;
            }
            if (tracingOption.box()) {
                boxOutlineCollector.submit(crystal.getBoundingBox(), ColorUtils.withAlphaInt(crystalRgb, 160));
            }
            if (tracingOption.line()) {
                traceCollector.submit(crystal.getBoundingBox().getCenter(), ColorUtils.withAlphaInt(crystalRgb, 255));
            }
            submitDamageText(crystal.getPos(), 6.0F, crystalRgb);
        }
    }

    private void submitDamageText(Vec3d explosionPos, float power, int color) {
        float damage = power == ExplosionUtils.RESPAWN_ANCHOR_POWER
                ? ExplosionUtils.respawnAnchorDamage(
                        mc.player.getBoundingBox(), explosionPos, mc.world, ExplosionUtils.ALL_TERRAIN)
                : ExplosionUtils.crystalDamage(
                        mc.player.getBoundingBox(), explosionPos, mc.world, ExplosionUtils.ALL_TERRAIN);
        textCollector.submit(
                new RenderElements.Text(
                        Text.literal("%.1f/%.1f"
                                .formatted(damage, ExplosionUtils.getMultipliedDamageByDifficulty(mc.world, damage))),
                        explosionPos,
                        0.66F),
                ColorUtils.withAlphaInt(color, 255));
    }

    public void onRender(Event<MatrixStack> event) {
        if (checkNull() || !enable.get()) {
            return;
        }
        MatrixStack stack = event.context();
        RenderUtils.startDrawVirtual(stack);
        try {
            textCollector.render3D(stack);
            boxOutlineCollector.render3D(stack);
            traceCollector.render3D(stack);
        } finally {
            RenderUtils.stopDrawVirtual(stack);
        }
    }

    private void onRequestCombatService(Event<CombatManager.Service> event) {
        event.context().enableExplosiveSearch(enable.get());
    }
}
