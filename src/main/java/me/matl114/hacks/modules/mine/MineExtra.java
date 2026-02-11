package me.matl114.hacks.modules.mine;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Vector2i;

public class MineExtra extends BaseModule {
    public static final String[] QUICK_MINE_HOTKEY = {"hotkeys-toggle", "quick-mine"};
    //    public static final String[] MINE_REACH_HOTKEY = {"hotkeys-toggle", "reach"};
    public static final String[] MINE_ENABLE_FAKE_INSTANT_BREAK = {"fast-break", "use-fake-instant-break"};

    public static final String[] MINE_BYPASS_FAST_BREAK_BYPASS_MODE = {"fast-break", "bypass-mode"};
    public static final String[] REACH_TOGGLE = {"hotkeys-toggle", "reach"};

    public static final String[] MINE_FASTBREAK_THRESHOLD = {"fast-break", "break-threshold"};
    public static final String[] MINE_FASTBREAK_BREAKCOOLDOWN = {"fast-break", "break-cooldown"};
    public static final String[] MINE_FASTBREAK_REACH = {"fast-break", "reach-distance"};

    public static final String[] MINE_DOUBLE_BREAK = {"fast-break", "double-break"};

    public static final String[] MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE = {"fast-break", "same-block-optimize"};
    public static final String[] MINE_RENDER_CURRENT_MINING_BLOCK = {"fast-break", "render-current-break-pos"};
    public static final String[] FAST_BREAK_GRIMAC_THRESHOLD = {"fast-break", "grim-punishment-threshold"};

    public MineExtra() {}

    private List<Vec3i> blocksAround = new ArrayList<>();

    private List<Vector2i> platesAround = new ArrayList<>();

    private double lastRange;

    public List<Vec3i> getBlocksAround() {
        if (mc.player != null) {
            refreshInteractionRange(getReachDistance());
        }
        return Collections.unmodifiableList(blocksAround);
    }

    public List<Vector2i> getPlatesAround() {
        if (mc.player != null) {
            refreshInteractionRange(getReachDistance());
        }
        return Collections.unmodifiableList(platesAround);
    }

    public void refreshInteractionRange(double val) {
        if (mc.player != null) {
            if (lastRange != val) {
                // update interaction range lazily
                lastRange = val;
                List<Vec3i> points = new ArrayList<>();
                int range = (int) lastRange;
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        for (int z = -range; z <= range; z++) {
                            points.add(new Vec3i(x, y, z));
                        }
                    }
                }
                points.sort(Comparator.comparingDouble(
                        v -> v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ()));
                blocksAround = points;
                List<Vector2i> plates = new ArrayList<>();
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        plates.add(new Vector2i(x, y));
                    }
                }
                plates.sort(Comparator.comparingDouble(v -> v.x * v.x + v.y * v.y));
                platesAround = plates;
            }
        }
    }

    public final FlagRef quickMine = toggle(QUICK_MINE_HOTKEY).build();

    public final KeyBindRef quickMineKeyBind = toggleHotkey(
                    QUICK_MINE_HOTKEY, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_M))
            .build();

    public final FlagRef fakeInstaBreak = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_ENABLE_FAKE_INSTANT_BREAK)
            .defaultValue(false)
            .build();

    public final EnumRef<Configs.BypassMode> fastBreakBypassMode = builder(
                    Configs.MINE_CONFIG, Configs.BypassMode.class)
            .path(MINE_BYPASS_FAST_BREAK_BYPASS_MODE)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final IntRef grimAcCounterThreshold = builder(Configs.MINE_CONFIG, Integer.class)
            .path(FAST_BREAK_GRIMAC_THRESHOLD)
            .defaultValue(750)
            .build();

    public final DoubleRef breakThreshold = builder(Configs.MINE_CONFIG, Double.class)
            .path(MINE_FASTBREAK_THRESHOLD)
            .defaultValue(0.99)
            .validator(Configs.doubleRange(0.0, 1.1))
            .build();

    public final IntRef breakCooldown = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_FASTBREAK_BREAKCOOLDOWN)
            .defaultValue(5)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef enableReach = toggle(REACH_TOGGLE).showConfig().build();

    public final KeyBindRef reachKeybind = toggleHotkey(
                    REACH_TOGGLE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_R))
            .build();

    public final DoubleRef reachDistance = builder(Configs.MINE_CONFIG, Double.class)
            .path(MINE_FASTBREAK_REACH)
            .defaultValue(0.0)
            .build();

    public final FlagRef doubleBreak = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_DOUBLE_BREAK)
            .defaultValue(false)
            .build();

    public final FlagRef optimizeOneBlock = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE)
            .defaultValue(false)
            .build();

    public final FlagRef mineRender = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_RENDER_CURRENT_MINING_BLOCK)
            .defaultValue(false)
            .build();

    public double getReachDistance() {
        return mc.player.getAttributeValue(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE) + reachDistance.get();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    public void onRender(Event<MatrixStack> renderEvent) {
        if (mineRender.get()) {
            RenderUtils.startDrawVirtual(renderEvent.context);
            try {
                if (mc.interactionManager != null && mc.player != null && mc.world != null) {
                    BlockPos blockPos =
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
                    Vec3d pos = Vec3d.of(blockPos);
                    // 超过200格的不渲染
                    if (mc.player.getPos().squaredDistanceTo(pos) < 40000) {
                        RenderUtils.setAsCurrentShaderColor(Color.BLUE, 1.0F);
                        RenderUtils.drawOutlinedBox(renderEvent.context, pos, pos.add(1.0, 1.0, 1.0));
                        float progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .getCurrentMiningProgress(true);
                        if (progress > 0.0F) {
                            BlockState state = mc.world.getBlockState(blockPos);
                            Box box;
                            if (state.isAir()) {
                                box = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                            } else {
                                VoxelShape shape = state.getOutlineShape(mc.world, blockPos);
                                box = shape.isEmpty() ? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) : shape.getBoundingBox();
                            }
                            Vec3d vec3 =
                                    box.getMaxPos().subtract(box.getMinPos()).multiply(0.5);

                            RenderUtils.setAsCurrentShaderColor(Color.YELLOW, 0.25F);
                            Vec3d vec3d = pos.add(box.getCenter());
                            float clamped = MathHelper.clamp(progress, 0.0F, 1.0F);
                            RenderUtils.drawSolidBox(
                                    renderEvent.context,
                                    vec3d.add(vec3.multiply(-clamped)),
                                    vec3d.add(vec3.multiply(clamped)));
                        }
                    }

                    BlockPos doubleMinePos =
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
                    if (doubleMinePos != null) {
                        Vec3d doubleMineVec = Vec3d.of(doubleMinePos);
                        if (mc.player.getPos().squaredDistanceTo(doubleMineVec) < 40000
                                && !Objects.equals(doubleMineVec, pos)) {
                            float progressFail = PlayerInteractionAccess.of(mc.interactionManager)
                                    .getFailBreakMiningProgress();
                            RenderUtils.setAsCurrentShaderColor(Color.MAGENTA, 1.0F);
                            RenderUtils.drawOutlinedBox(
                                    renderEvent.context, doubleMineVec, doubleMineVec.add(1.0, 1.0, 1.0));
                            if (progressFail > 0.0F) {
                                BlockState state = mc.world.getBlockState(doubleMinePos);
                                Box box;
                                if (state.isAir()) {
                                    box = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                                } else {
                                    VoxelShape shape = state.getOutlineShape(mc.world, doubleMinePos);
                                    box = shape.isEmpty()
                                            ? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                                            : shape.getBoundingBox();
                                }
                                Vec3d vec3 = box.getMaxPos()
                                        .subtract(box.getMinPos())
                                        .multiply(0.5);

                                RenderUtils.setAsCurrentShaderColor(Color.ORANGE, 0.25F);
                                Vec3d vec3d = doubleMineVec.add(box.getCenter());
                                float clamped = MathHelper.clamp(progressFail, 0.0F, 1.0F);
                                RenderUtils.drawSolidBox(
                                        renderEvent.context,
                                        vec3d.add(vec3.multiply(-clamped)),
                                        vec3d.add(vec3.multiply(clamped)));
                            }
                        }
                    }
                }
            } finally {
                RenderUtils.stopDrawVirtual(renderEvent.context);
            }
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        var modulePreset = presetEvent.context().getValue();
        switch (modulePreset) {
            case AC_GRIM -> {
                fastBreakBypassMode.set(Configs.BypassMode.BYPASS_GRIM);
            }
            default -> {
                fastBreakBypassMode.set(Configs.BypassMode.NO_BYPASS);
            }
        }
    }
}
