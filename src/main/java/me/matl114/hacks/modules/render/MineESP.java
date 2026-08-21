package me.matl114.hacks.modules.render;

import java.awt.Color;
import java.util.Comparator;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.mine.MiningProgressManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.WidgetPos;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.inventory.ItemStackSample;
import me.matl114.utils.render.RenderCollector;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

public class MineESP extends BaseModule {

    private static final int GRID_SIZE = 5;
    private static final int GRID_VISIBLE_SIZE = 3;

    public MineESP() {
        super("MineESP");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.RENDER_CONFIG, "mine-render.mine-esp");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef renderName = flagBuilder(root.add("render-name")).build();

    public final FlagRef renderBox = flagBuilder(root.add("render-box")).build();
    public final FlagRef ghostHandPredict =
            flagBuilder(root.add("ghost-hand-predict")).build();

    public final NBTRef<WrapColor> colorName = builder(root.add("name-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.WHITE)))
            .build();

    public final NBTRef<WrapColor> colorFrame = builder(root.add("frame-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.AQUA)))
            .build();

    public final NBTRef<WrapColor> colorFrameDouble = builder(root.add("frame-double-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.AQUA))
            .build();

    public final NBTRef<WrapColor> colorProgress = builder(root.add("progress-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.GOLD)))
            .build();

    public final FlagRef renderGrid2D = flagBuilder(root.add("render-grid-2d")).build();

    public final NBTRef<WidgetPos> gridPos = builder(root.add("grid-pos"), WidgetPos.class)
            .defaultValue(new WidgetPos(0, 0.5D, 0.7D, 0, 0))
            .build();

    public final IntRef gridCellSize = intBuilder(root.add("grid-cell-size"))
            .defaultValue(12)
            .validator(Configs.intRange(6, 40))
            .build();

    public final IntRef gridGap = intBuilder(root.add("grid-gap"))
            .defaultValue(1)
            .validator(Configs.intRange(0, 6))
            .build();

    public final NBTRef<WrapColor> colorGridBreaking = builder(root.add("grid-breaking-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.RED))
            .build();

    public final NBTRef<WrapColor> colorGridDouble = builder(root.add("grid-double-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.AQUA))
            .build();

    public final NBTRef<WrapColor> colorGridSolid = builder(root.add("grid-solid-color"), WrapColor.class)
            .defaultValue(new WrapColor(new Color(96, 96, 96)))
            .build();

    public final NBTRef<WrapColor> colorGridAir = builder(root.add("grid-air-color"), WrapColor.class)
            .defaultValue(new WrapColor(new Color(24, 24, 24)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onUpdate);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(RenderListener.getRender2DEvent(), this::onRender2D);
    }

    final RenderCollector<Box> frameRenderer = RenderCollectors.createBoxCollector(true, false, false);
    final RenderCollector<Box> progressRenderer = RenderCollectors.createBoxCollector(true, true, false);
    final RenderCollector<RenderElements.Text> textRenderer = RenderCollectors.createTextCollector();

    public void onUpdate(Event<ClientPlayerEntity> eventUpdate) {
        textRenderer.clear();
        frameRenderer.clear();
        progressRenderer.clear();
        if (checkNull()) return;
        if (enable.get()) {
            for (var re : MiningProgressManager.INSTANCE.getBreakingMap().values()) {
                if (re.blockPos != null) {
                    BlockPos currentMining = re.blockPos;
                    int startTick = re.breakingStartTick;
                    int progress = re.breakingProgress;
                    String breakState;
                    int progressPercentage;
                    if (ghostHandPredict.get()) {
                        float prediction = predictGhostHandBreakSpeed(re.player, currentMining);
                        float predictProgress = prediction * (Tasks.getTick() - startTick);
                        if (predictProgress > 0.7F) {
                            progressPercentage = 100;
                            breakState = "&cInstant";
                        } else {
                            progressPercentage = (int) (predictProgress * 100);
                            breakState = "&e%d%%".formatted(progressPercentage);
                        }
                    } else {
                        if (progress > 0) {
                            progressPercentage = (int) (progress * 10);
                            breakState = "&e%d%%".formatted(progressPercentage);
                        } else {
                            progressPercentage = 0;
                            breakState = "&aStop";
                        }
                    }
                    if (renderName.get()) {
                        Text text = ChatUtils.stringToText(re.player.getNameForScoreboard() + "\n" + breakState);
                        textRenderer.submit(
                                new RenderElements.Text(
                                        text, currentMining.toCenterPos().add(0, 0.2, 0), 0.5f),
                                colorName.get().withAlpha(255));
                    }
                    if (renderBox.get()) {
                        frameRenderer.submit(
                                new Box(currentMining), colorFrame.get().withAlpha(255));
                        float progressPF = Math.clamp(progressPercentage / 100.0F, 0.0F, 1.0F);
                        progressRenderer.submit(
                                new Box(
                                        currentMining.toCenterPos().add(RenderTasks.FROM.multiply(progressPF)),
                                        currentMining.toCenterPos().add(RenderTasks.TO.multiply(progressPF))),
                                colorProgress.get().withAlpha(64));
                    }
                }
                if (re.potentialDoubleBreak != null) {
                    BlockPos currentMining = re.potentialDoubleBreak;
                    String breakState = "Double";
                    int progressPercentage = re.doubleBreakProgress * 10;
                    if (renderName.get()) {
                        Text text = Text.literal(re.player.getNameForScoreboard() + "\n" + breakState);
                        textRenderer.submit(
                                new RenderElements.Text(
                                        text, currentMining.toCenterPos().add(0, 0.2, 0), 0.5f),
                                colorName.get().withAlpha(255));
                    }
                    if (renderBox.get()) {
                        frameRenderer.submit(
                                new Box(currentMining), colorFrame.get().withAlpha(255));
                        float progressPF = Math.clamp(progressPercentage / 100.0F, 0.0F, 1.0F);
                        progressRenderer.submit(
                                new Box(
                                        currentMining.toCenterPos().add(RenderTasks.FROM.multiply(progressPF)),
                                        currentMining.toCenterPos().add(RenderTasks.TO.multiply(progressPF))),
                                colorProgress.get().withAlpha(64));
                    }
                }
            }
        }
    }

    public float predictGhostHandBreakSpeed(PlayerEntity player, BlockPos pos) {
        PlayerStateManager.PlayerStatus status = PlayerStateManager.INSTANCE.getPlayerStatus(player);
        BlockState blockState = mc.world.getBlockState(pos);
        ItemStack bestTool = status.trackedInventoryItems.stream()
                .max(Comparator.comparingDouble(s -> {
                    return WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(player, blockState, s.sample());
                }))
                .map(ItemStackSample::sample)
                .orElse(ItemStack.EMPTY);
        float speed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(player, blockState, bestTool);
        return WorldUtils.calcBlockBreakingDelta(blockState, mc.world, pos, speed);
    }

    public void onRender3D(Event<MatrixStack> event) {
        if (checkNull()) return;
        if (enable.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                frameRenderer.render3D(event.context);
                progressRenderer.render3D(event.context);
                textRenderer.render3D(event.context);
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public void onRender2D(Event<VDrawContext> event) {
        if (checkNull()) return;
        if (!enable.get() || !renderGrid2D.get() || event.<Boolean>getArgs(1)) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        GridCell[][] gridCells = buildProjectedGrid(player);
        if (gridCells == null) {
            return;
        }

        WidgetPos pos = gridPos.get();
        float startX = (float) pos.getWindowXFloat(mc.getWindow());
        float startY = (float) pos.getWindowYFloat(mc.getWindow());
        float playerOffsetX = getPlayerCellOffsetX(player);
        float playerOffsetY = getPlayerCellOffsetY(player);

        VDrawContext vdraw = event.context;
        vdraw.pushMatrix();
        try {
            vdraw.getMatrices().translate(startX, startY);
            //
            try {
                float rotationRadians = getGridRotationRadians();
                vdraw.pushMatrix();
                try {
                    rotateGridToView(vdraw, rotationRadians);
                    vdraw.getMatrices().translate(-playerOffsetX, -playerOffsetY);
                    renderProjectedGrid(vdraw, gridCells, playerOffsetX, playerOffsetY);
                } finally {
                    vdraw.popMatrix();
                }

                vdraw.fill(-2, -2, 2, 2, Colors.RED);

                renderGridOverlay(vdraw, gridCells, rotationRadians, playerOffsetX, playerOffsetY);
            } finally {
                // vdraw.disableScissor();
            }
        } finally {
            vdraw.popMatrix();
        }
    }

    private GridCell[][] buildProjectedGrid(ClientPlayerEntity player) {
        int[] occupiedBlocks = new int[] {0};
        int footY = (int) Math.floor(player.getBoundingBox().minY);
        int centerX = MathHelper.floor(player.getX());
        int centerZ = MathHelper.floor(player.getZ());

        GridCell[][] baseStates = new GridCell[GRID_SIZE][GRID_SIZE];
        for (int row = 0; row < GRID_SIZE; ++row) {
            for (int col = 0; col < GRID_SIZE; ++col) {
                int worldX = centerX + (col - GRID_SIZE / 2);
                int worldZ = centerZ + (row - GRID_SIZE / 2);
                baseStates[row][col] = projectColumnState(worldX, worldZ, footY, footY + 1, occupiedBlocks);
            }
        }
        if (occupiedBlocks[0] < 3) {
            return null;
        }
        return baseStates;
    }

    private GridCell projectColumnState(int x, int z, int minY, int maxYExclusive, int[] occupiedBlocks) {
        boolean hasFullCube = false;
        MiningProgressManager.BlockBreakTracker bestTracker = null;
        GridState bestState = null;
        int maxProgress = -1;

        for (int y = minY; y < maxYExclusive; ++y) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() && !state.isLiquid()) {
                if (state.isFullCube(mc.world, pos)) {
                    occupiedBlocks[0] += 1;
                    hasFullCube = true;
                }
            }
            for (var tracker : MiningProgressManager.INSTANCE.getBreakingMap().values()) {
                if (tracker.blockPos != null && tracker.blockPos.equals(pos)) {
                    int progress = getBreakingProgressPercentage(tracker);
                    if (progress > maxProgress) {
                        maxProgress = progress;
                        bestState = GridState.BREAKING;
                        bestTracker = tracker;
                    }
                }
                if (tracker.potentialDoubleBreak != null && tracker.potentialDoubleBreak.equals(pos)) {
                    int progress = getDoubleBreakProgressPercentage(tracker);
                    if (progress > maxProgress) {
                        maxProgress = progress;
                        bestState = GridState.DOUBLE_BREAK;
                        bestTracker = tracker;
                    }
                }
            }
        }

        if (bestState != null) {
            return new GridCell(bestState, bestTracker, maxProgress);
        }
        return new GridCell(hasFullCube ? GridState.SOLID : GridState.HOLLOW, null, -1);
    }

    private float getGridRotationRadians() {
        float yaw = mc.gameRenderer.getCamera().getYaw();
        return (float) Math.toRadians(180 - yaw);
    }

    private float getPlayerCellOffsetX(ClientPlayerEntity player) {
        return (float) ((player.getX() - (MathHelper.floor(player.getX()) + 0.5)) * gridCellSize.get());
    }

    private float getPlayerCellOffsetY(ClientPlayerEntity player) {
        return (float) ((player.getZ() - (MathHelper.floor(player.getZ()) + 0.5)) * gridCellSize.get());
    }

    private void rotateGridToView(VDrawContext vdraw, float rotationRadians) {
        if (rotationRadians != 0.0F) {
            vdraw.getMatrices().multiply3D(new Quaternionf().rotationZ(rotationRadians));
        }
    }

    private int getGridPixelSize() {
        return getGridPixelSize(GRID_SIZE);
    }

    private int getGridPixelSize(int size) {
        int cell = gridCellSize.get();
        int gap = gridGap.get();
        return cell * size + gap * (size - 1);
    }

    private int getGridBaseOrigin() {
        return -(int) (2.5D * (gridCellSize.get() + gridGap.get()));
    }

    private double getGridLimitSize() {
        return (2.0D * (gridCellSize.get() + gridGap.get()));
    }

    private double getCellCenterX(int col, float playerOffsetX) {
        int cell = gridCellSize.get();
        int gap = gridGap.get();
        return getGridBaseOrigin() + col * (cell + gap) + cell / 2.0D - playerOffsetX;
    }

    private double getCellCenterY(int row, float playerOffsetY) {
        int cell = gridCellSize.get();
        int gap = gridGap.get();
        return getGridBaseOrigin() + row * (cell + gap) + cell / 2.0D - playerOffsetY;
    }

    private void fillLimited(
            VDrawContext vdraw,
            int x1,
            int y1,
            int x2,
            int y2,
            int color,
            int limitX1,
            int limitY1,
            int limitX2,
            int limitY2) {
        x1 = Math.clamp(x1, limitX1, limitX2);
        x2 = Math.clamp(x2, limitX1, limitX2);
        y1 = Math.clamp(y1, limitY1, limitY2);
        y2 = Math.clamp(y2, limitY1, limitY2);
        if (x1 == x2 || y1 == y2) {
            return;
        }
        vdraw.fill(x1, y1, x2, y2, color);
    }

    private void renderProjectedGrid(VDrawContext vdraw, GridCell[][] states, double centerX, double centerY) {
        int cell = gridCellSize.get();
        int gap = gridGap.get();
        int outlineColor = ColorUtils.getColorInt(255, 255, 255, 96);
        int backgroundColor = ColorUtils.getColorInt(0, 0, 0, 96);
        int gridSize = getGridPixelSize();
        int origin = getGridBaseOrigin();
        int limitXMin = (int) (centerX - getGridLimitSize());
        int limitXMax = (int) (centerX + getGridLimitSize());
        int limitYMin = (int) (centerY - getGridLimitSize());
        int limitYMax = (int) (centerY + getGridLimitSize());

        fillLimited(
                vdraw,
                origin - 2,
                origin - 2,
                origin + gridSize + 2,
                origin + gridSize + 2,
                backgroundColor,
                limitXMin,
                limitYMin,
                limitXMax,
                limitYMax);

        for (int row = 0; row < GRID_SIZE; ++row) {
            for (int col = 0; col < GRID_SIZE; ++col) {
                int x1 = origin + col * (cell + gap);
                int y1 = origin + row * (cell + gap);
                int x2 = x1 + cell;
                int y2 = y1 + cell;
                int color = getGridColor(states[row][col].state());
                fillLimited(vdraw, x1, y1, x2, y2, color, limitXMin, limitYMin, limitXMax, limitYMax);
                ;
                fillLimited(vdraw, x1, y1, x2, y1 + 1, outlineColor, limitXMin, limitYMin, limitXMax, limitYMax);
                ;
                fillLimited(vdraw, x1, y2 - 1, x2, y2, outlineColor, limitXMin, limitYMin, limitXMax, limitYMax);
                ;
                fillLimited(vdraw, x1, y1, x1 + 1, y2, outlineColor, limitXMin, limitYMin, limitXMax, limitYMax);
                ;
                fillLimited(vdraw, x2 - 1, y1, x2, y2, outlineColor, limitXMin, limitYMin, limitXMax, limitYMax);
                ;
            }
        }
    }

    private void renderGridOverlay(
            VDrawContext vdraw, GridCell[][] cells, float rotationRadians, float playerOffsetX, float playerOffsetY) {
        for (int row = 0; row < GRID_SIZE; ++row) {
            for (int col = 0; col < GRID_SIZE; ++col) {
                GridCell cell = cells[row][col];
                if (cell.progressPercentage() < 0) {
                    continue;
                }
                Point point = rotatePoint(
                        getCellCenterX(col, playerOffsetX), getCellCenterY(row, playerOffsetY), rotationRadians);
                drawCellProgress(vdraw, point.x(), point.y(), cell.progressPercentage());
            }
        }
    }

    private Point rotatePoint(double x, double y, float rotationRadians) {
        double sin = Math.sin(rotationRadians);
        double cos = Math.cos(rotationRadians);
        return new Point(x * cos - y * sin, x * sin + y * cos);
    }

    private void drawCellProgress(VDrawContext vdraw, double centerX, double centerY, int progressPercentage) {
        String text = progressPercentage + "%";
        int textWidth = mc.textRenderer.getWidth(text);
        int fontHeight = mc.textRenderer.fontHeight;
        float maxSize = Math.max(1.0F, gridCellSize.get() - 2.0F);
        float scale = Math.min(1.0F, Math.min(maxSize / textWidth, maxSize / fontHeight));
        int color = colorProgress.get().withAlpha(255);

        vdraw.pushMatrix();
        try {
            vdraw.getMatrices().translate((float) centerX, (float) centerY);
            vdraw.getMatrices().scale(scale, scale);
            vdraw.drawText(mc.textRenderer, text, -textWidth / 2, -fontHeight / 2, color, true);
        } finally {
            vdraw.popMatrix();
        }
    }

    private int getBreakingProgressPercentage(MiningProgressManager.BlockBreakTracker tracker) {
        if (tracker.blockPos == null) {
            return -1;
        }
        if (ghostHandPredict.get()) {
            float prediction = predictGhostHandBreakSpeed(tracker.player, tracker.blockPos);
            float predictProgress = prediction * (Tasks.getTick() - tracker.breakingStartTick);
            if (predictProgress > 0.7F) {
                return 100;
            }
            return Math.clamp((int) (predictProgress * 100), 0, 100);
        }
        return Math.clamp(tracker.breakingProgress * 10, 0, 100);
    }

    private int getDoubleBreakProgressPercentage(MiningProgressManager.BlockBreakTracker tracker) {
        return tracker.potentialDoubleBreak == null ? -1 : Math.clamp(tracker.doubleBreakProgress * 10, 0, 100);
    }

    private int getGridColor(GridState state) {
        return switch (state) {
            case BREAKING -> colorGridBreaking.get().withAlpha(220);
            case DOUBLE_BREAK -> colorGridDouble.get().withAlpha(220);
            case SOLID -> colorGridSolid.get().withAlpha(220);
            case HOLLOW -> colorGridAir.get().withAlpha(180);
        };
    }

    private enum GridState {
        BREAKING,
        DOUBLE_BREAK,
        SOLID,
        HOLLOW
    }

    private record GridCell(GridState state, MiningProgressManager.BlockBreakTracker tracker, int progressPercentage) {}

    private record Point(double x, double y) {}
}
