package me.matl114.hacks.modules.survival;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import java.util.*;
import java.util.function.Supplier;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.hooks.XaeroHooks;
import me.matl114.hooks.impl.xaeroplus.IMapDrawFeature;
import me.matl114.hooks.impl.xaeroplus.wrapper.LineWrapper;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.collections.FPoint;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xaeroplus.feature.highlights.ChunkHighlightCache;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.LavaColumns;
import xaeroplus.module.impl.LiquidNewChunks;

public class XaeroMapScanner extends BaseModule {
    public XaeroMapScanner() {
        super("XaeroScanner");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "xaero-map-extra.xaero-scanner");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final IntRef chunkRange =
            intBuilder(root.add("chunk-range")).defaultValue(24).build();

    public final IntRef rescheduleRange =
            intBuilder(root.add("reschedule-range")).defaultValue(8).build();

    public final EnumRef<Mode> mode = builder(root.add("mode"), Mode.class)
            .defaultValue(Mode.LIQUID)
            .updateListener(s -> this.currentGoals.values().forEach(Goal::reset))
            .build();

    public final FlagRef useInverseNewChunks = flagBuilder(root.add("use-inverse-new-chunks"))
            .show(() -> mode.get().isIn(Mode.LIQUID))
            .build();

    public final DoubleRef unloadedRatio =
            doubleBuilder(root.add("new-chunk-ratio")).defaultValue(0.25D).build();

    public final FlagRef render =
            builder(root.add("render"), Boolean.class).defaultValue(true).build();
    public final NBTRef<WrapColor> color = builder(root.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.RED)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTickPost);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        currentGoals = new HashMap<>();
        currentGoals.put(Mode.LIQUID, newInstance(() -> new GoalLiquidImpl(this)));
    }

    public IMapDrawFeature searchDirectionDrawFeature;
    public static final String RENDER_ID = "slimefun_xaeroscanner_scann_render";

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        reset();
        searchDirectionDrawFeature = XaeroHooks.getInstance()
                .getMapDrawFactory()
                .lines(
                        RENDER_ID,
                        this::supplyLoadedChunkLines,
                        () -> this.color.get().withAlpha(255),
                        () -> 0.1F,
                        100);
        searchDirectionDrawFeature.register();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        reset();
        BaritoneHooks.getInstance().cancelBaritone();
        if (searchDirectionDrawFeature != null) {
            searchDirectionDrawFeature.unregister();
            searchDirectionDrawFeature = null;
        }
    }

    public List<LineWrapper<?>> supplyLoadedChunkLines(int x, int y, int w, RegistryKey<World> dimension) {
        if (enable.get() && render.get()) {
            return currentGoals.get(mode.get()).supplyGoalInfo();
        } else {
            return List.of();
        }
    }

    private Goal newInstance(Supplier<Goal> init) {
        try {
            if (XaeroHooks.getInstance().isXaeroWorldMapEnable()
                    && XaeroHooks.getInstance().isXaeroPlusEnable()
                    && BaritoneHooks.getInstance().isBaritoneAPISupported()) {
                return init.get();
            } else {
                return (b, p) -> null;
            }
        } catch (Throwable e) {
            return (b, p) -> null;
        }
    }

    ChunkPos lastChunkPos = null;
    BlockPos lastGoal = null;

    private void reset() {
        lastChunkPos = null;
        lastGoal = null;
        currentGoals.values().forEach(Goal::reset);
    }

    public void onWorldSwitch(Event<World> event) {
        reset();
    }

    Map<Mode, Goal> currentGoals;

    public void onTickPost(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            if (!XaeroHooks.getInstance().isXaeroWorldMapEnable()
                    || !XaeroHooks.getInstance().isXaeroPlusEnable()
                    || !BaritoneHooks.getInstance().isBaritoneAPISupported()) {
                enable.set(false);
                return;
            }
            BlockPos currentDestination = BaritoneHooks.getInstance().getBaritoneCurrentElytraDestination();
            if (!Objects.equals(currentDestination, lastGoal)) {
                lastGoal = currentDestination;
                currentGoals.get(mode.get()).reset();
            }

            ChunkPos chunkPos = mc.player.getChunkPos();
            if (lastGoal == null
                    || lastChunkPos == null
                    || lastChunkPos.getSquaredDistance(chunkPos) > MathUtils.s2(rescheduleRange.get())) {
                lastChunkPos = chunkPos;
                BlockPos goal = currentGoals.get(mode.get()).tickGoal(lastGoal, lastChunkPos);
                if (!Objects.equals(goal, lastGoal)) {
                    lastGoal = goal;
                    if (goal != null) {
                        BaritoneHooks.getInstance().setBaritoneCurrentElytraDestination(goal);
                    }
                }
            }
        }
    }

    public enum Mode implements ConfigEnum {
        LIQUID;

        @Override
        public String getConfigEnumType() {
            return "xaero_map_scanner_mode";
        }
    }

    public static interface Goal {
        public BlockPos tickGoal(BlockPos lastGoal, ChunkPos chunkPos);

        default List<LineWrapper<?>> supplyGoalInfo() {
            return List.of();
        }

        default void reset() {}
    }

    public static class GoalLiquidImpl implements Goal {

        public GoalLiquidImpl(XaeroMapScanner in) {
            this.scanner = in;
            this.lavaColumns = ModuleManager.getModule(LavaColumns.class);
            this.liquidNewChunks = ModuleManager.getModule(LiquidNewChunks.class);
        }

        LavaColumns lavaColumns;
        LiquidNewChunks liquidNewChunks;
        XaeroMapScanner scanner;

        private Set<ChunkPos> extractNearbyHighlights(ChunkHighlightCache cache, ChunkPos pos) {
            Long2LongMap chunkMap = cache.getCacheMap(mc.world.getRegistryKey());
            if (chunkMap == null) return Set.of();
            Set<ChunkPos> ret = new HashSet<>();
            int range = scanner.chunkRange.get();
            for (var x = -range; x <= range; ++x) {
                for (var z = -range; z <= range; ++z) {
                    long longValue = ChunkPos.toLong(pos.x + x, pos.z + z);
                    if (chunkMap.containsKey(longValue)) {
                        ret.add(new ChunkPos(pos.x + x, pos.z + z));
                    }
                }
            }
            return ret;
        }

        private Set<ChunkPos> extractNearbyHighlightsLavaColumn(ChunkHighlightCache cache, ChunkPos pos) {
            Long2LongMap chunkMap = cache.getCacheMap(mc.world.getRegistryKey());
            if (chunkMap == null) return Set.of();
            Set<ChunkPos> ret = new HashSet<>();
            int range = scanner.chunkRange.get();
            for (var x = -range; x <= range; ++x) {
                for (var z = -range; z <= range; ++z) {
                    long longValue = ChunkPos.toLong(pos.x + x, pos.z + z);
                    if (chunkMap.get(longValue) > 5) {
                        ret.add(new ChunkPos(pos.x + x, pos.z + z));
                    }
                }
            }
            return ret;
        }

        private double goalDistance() {
            return Math.min(400, scanner.rescheduleRange.get() * 64);
        }

        private double minDistance() {
            return Math.max(100, scanner.rescheduleRange.get() * 16);
        }

        private static final double RED_SIDE_CORRECTION_DISTANCE = 80.0;
        private FitResult result = null;
        private Vec3d lastDirection = null;
        private Double lastSignedDistance = null;

        @Override
        public void reset() {
            result = null;
            lastDirection = null;
            lastSignedDistance = null;
        }

        @Override
        public BlockPos tickGoal(BlockPos lastGoal, ChunkPos chunkPos) {
            if (lastDirection == null && lastGoal != null) {
                lastDirection =
                        lastGoal.toCenterPos().subtract(mc.player.getPos()).withAxis(Direction.Axis.Y, 0);
            }
            Set<ChunkPos> chunkWithLavaColumns =
                    new HashSet<>(extractNearbyHighlightsLavaColumn(lavaColumns.lavaColumnsCache.get(), chunkPos));
            Set<ChunkPos> chunkWithNewChunks = extractNearbyHighlights(liquidNewChunks.newChunksCache.get(), chunkPos);
            if (scanner.useInverseNewChunks.get()) {
                chunkWithLavaColumns.addAll(
                        extractNearbyHighlights(liquidNewChunks.inverseNewChunksCache.get(), chunkPos));
            }
            chunkWithLavaColumns.removeAll(chunkWithNewChunks);
            double ratio = scanner.unloadedRatio.get();
            Vec3d playerPos = mc.player.getPos();
            if (result != null) {
                double signedDistance = signedDistance(result, playerPos.x, playerPos.z);
                if (lastSignedDistance != null) {
                    if (signedDistance > 0) {
                        return lastGoal;
                    } else {
                        lastSignedDistance = null;
                    }
                }
            }
            if (chunkWithNewChunks.isEmpty()
                    || chunkWithLavaColumns.isEmpty()
                    || chunkWithNewChunks.size()
                            < (ratio * (chunkWithLavaColumns.size() + chunkWithNewChunks.size()))) {
                // no direction
                if (chunkWithLavaColumns.isEmpty()) {
                    return null;
                }
                // all loaded, just continue
                if (lastGoal != null) {
                    if (lastDirection != null) {
                        return BlockPos.ofFloored(mc.player.getPos().add(lastDirection.multiply(goalDistance())));
                    } else if (lastGoal.toCenterPos()
                                    .subtract(mc.player.getPos())
                                    .horizontalLengthSquared()
                            > MathUtils.s2(minDistance())) {
                        return lastGoal;
                    } else {
                        Vec3d direction = lastGoal.toCenterPos()
                                .subtract(mc.player.getPos())
                                .withAxis(Direction.Axis.Y, 0)
                                .normalize();
                        return BlockPos.ofFloored(mc.player.getPos().add(direction.multiply(goalDistance())));
                    }
                } else {
                    lastDirection = null;
                    Vec3d horizontalLook = EntityUtils.pitchYawToRotation(0, mc.player.getYaw());
                    lastDirection = horizontalLook;
                    return BlockPos.ofFloored(mc.player.getPos().add(horizontalLook.multiply(goalDistance())));
                }
            } else {

                List<FPoint> red = toPoints(chunkWithNewChunks);
                List<FPoint> blue = toPoints(chunkWithLavaColumns);
                FitResult best = findBestLine(red, blue, mc.player.getPos());
                result = best;
                if (best == null) {
                    return lastGoal;
                }

                double signedDistance = signedDistance(best, playerPos.x, playerPos.z);
                Vec3d currentDirection;
                if (lastDirection != null) {
                    currentDirection = lastDirection;
                } else if (lastGoal != null) {
                    Vec3d delta = lastGoal.toCenterPos().subtract(playerPos);
                    delta = new Vec3d(delta.x, 0, delta.z);
                    if (delta.lengthSquared() > EPS) {
                        currentDirection = delta.normalize();
                    } else {
                        currentDirection = EntityUtils.pitchYawToRotation(0, mc.player.getYaw());
                        currentDirection = new Vec3d(currentDirection.x, 0, currentDirection.z).normalize();
                    }
                } else {
                    currentDirection = EntityUtils.pitchYawToRotation(0, mc.player.getYaw());
                    currentDirection = new Vec3d(currentDirection.x, 0, currentDirection.z).normalize();
                }

                Vec3d tangent = chooseTangent(best, currentDirection);
                lastDirection = tangent;
                if (signedDistance > RED_SIDE_CORRECTION_DISTANCE) {
                    lastSignedDistance = signedDistance;
                    Vec3d projected = projectToLine(best, playerPos);
                    // should revert fly
                    Vec3d target = projected.multiply(2).subtract(playerPos).add(tangent.multiply(goalDistance()));

                    return BlockPos.ofFloored(target);
                }

                Vec3d target = playerPos.add(tangent.multiply(goalDistance()));

                return BlockPos.ofFloored(target);
            }
        }

        @Override
        public List<LineWrapper<?>> supplyGoalInfo() {
            if (result == null) {
                return List.of();
            } else {
                return List.of(toLineWrapper(result));
            }
        }

        private List<FPoint> toPoints(Set<ChunkPos> chunkWithNewChunks) {
            return chunkWithNewChunks.stream()
                    .map(s -> new FPoint(16.0 * s.x + 8.0D, 16.0 * s.z + 8.0D))
                    .toList();
        }

        private FPoint calculateCenter(Collection<ChunkPos> chunks) {
            if (chunks == null || chunks.isEmpty()) return null;
            double sumX = 0, sumZ = 0;
            int count = 0;
            for (ChunkPos pos : chunks) {
                // 取区块中心的世界坐标 (区块坐标 * 16 + 8)
                sumX += pos.x * 16.0 + 8.0;
                sumZ += pos.z * 16.0 + 8.0;
                count++;
            }
            return new FPoint(sumX / count, sumZ / count);
        }

        private FitResult lineThrough(FPoint p1, FPoint p2) {

            double a = p1.y() - p2.y();

            double b = p2.x() - p1.x();

            double c = -(a * p1.x() + b * p1.y());

            double len = Math.sqrt(a * a + b * b);

            if (len < EPS) {
                return null;
            }

            return new FitResult(a / len, b / len, c / len);
        }

        private double signedDistance(FitResult line, double x, double z) {
            return line.a() * x + line.b() * z + line.c();
        }

        private double signedDistance(FitResult line, FPoint point) {
            return signedDistance(line, point.x(), point.y());
        }

        private FitResult shiftLine(FitResult line, double delta) {
            return new FitResult(line.a(), line.b(), line.c() + delta);
        }

        private int scoreLine(FitResult line, List<FPoint> red, List<FPoint> blue) {

            int score = 0;

            for (FPoint p : red) {

                if (signedDistance(line, p) > EPS) {
                    ++score;
                }
            }

            for (FPoint p : blue) {

                if (signedDistance(line, p) < -EPS) {
                    ++score;
                }
            }

            return score;
        }

        private static final double EPS = 1e-9;

        private IndexEntry<FitResult> evaluateCandidateLine(
                FitResult base, List<FPoint> red, List<FPoint> blue, Vec3d playerPos) {

            List<FPoint> all = new ArrayList<>(red.size() + blue.size());

            all.addAll(red);
            all.addAll(blue);

            /*
             * 找到离 base 最近的非零点距离。
             */
            double minAbs = Double.POSITIVE_INFINITY;

            for (FPoint p : all) {

                double d = Math.abs(signedDistance(base, p));

                if (d > EPS) {
                    minAbs = Math.min(minAbs, d);
                }
            }

            /*
             * 所有点都在线上。
             *
             * 这种情况非常退化。
             */
            if (!Double.isFinite(minAbs)) {

                /*
                 * 直接尝试 base 两个方向。
                 */
                FitResult plus = shiftLine(base, EPS);

                FitResult minus = shiftLine(base, -EPS);

                return betterFit(plus, scoreLine(plus, red, blue), minus, scoreLine(minus, red, blue), playerPos);
            }

            /*
             * 取一半。
             *
             * 这样从 base 向任意一侧移动 eps 后，
             * 不会跨过其他非线上的数据点。
             */
            double delta = minAbs * 0.5;

            FitResult plus = shiftLine(base, delta);

            FitResult minus = shiftLine(base, -delta);

            int plusScore = scoreLine(plus, red, blue);

            int minusScore = scoreLine(minus, red, blue);

            return betterFit(plus, plusScore, minus, minusScore, playerPos);
        }

        /**
         * 比较两个候选线。
         *
         * 第一优先级：
         *     正确分类数量
         *
         * 第二优先级：
         *     离玩家更近
         */
        private IndexEntry<FitResult> betterFit(
                FitResult line1, int score1, FitResult line2, int score2, Vec3d playerPos) {

            if (score1 > score2) {
                return new IndexEntry<>(score1, line1);
            }

            if (score2 > score1) {
                return new IndexEntry<>(score2, line2);
            }

            /*
             * 分类数量相同。
             *
             * 选择距离玩家更近的线。
             */
            double d1 = Math.abs(signedDistance(line1, playerPos.x, playerPos.z));

            double d2 = Math.abs(signedDistance(line2, playerPos.x, playerPos.z));

            if (d1 <= d2) {
                return new IndexEntry<>(score1, line1);
            }

            return new IndexEntry<>(score2, line2);
        }

        /**
         * 精确寻找最大分类准确率的直线。
         *
         * 核心：
         *
         * 对于有限个点的线性分类问题，
         * 存在一个最优分类状态，其边界可以
         * 移动/旋转到经过至少两个数据点。
         *
         * 因此只需要枚举所有两点确定的直线。
         *
         * 复杂度：
         *
         *     O(n^3)
         *
         * n <= 100 时完全足够。
         */
        private FitResult findBestLine(List<FPoint> red, List<FPoint> blue, Vec3d playerPos) {

            List<FPoint> all = new ArrayList<>(red.size() + blue.size());

            all.addAll(red);
            all.addAll(blue);

            if (all.size() < 2) {
                return null;
            }

            FitResult best = null;
            int correct = 0;

            for (int i = 0; i < all.size(); ++i) {

                for (int j = i + 1; j < all.size(); ++j) {

                    FPoint p1 = all.get(i);

                    FPoint p2 = all.get(j);

                    FitResult base = lineThrough(p1, p2);

                    if (base == null) {
                        continue;
                    }

                    IndexEntry<FitResult> candidate = evaluateCandidateLine(base, red, blue, playerPos);

                    if (candidate == null) {
                        continue;
                    }

                    if (best == null) {

                        best = candidate.val();
                        correct = candidate.index();

                    } else if (candidate.index() > correct) {
                        best = candidate.val();
                        correct = candidate.index();
                    }
                }
            }

            return best;
        }

        private Vec3d projectToLine(FitResult line, Vec3d playerPos) {

            double d = signedDistance(line, playerPos.x, playerPos.z);

            return new Vec3d(playerPos.x - line.a() * d, playerPos.y, playerPos.z - line.b() * d);
        }

        private Vec3d chooseTangent(FitResult line, Vec3d currentDirection) {

            Vec3d d1 = new Vec3d(line.b(), 0, -line.a()).normalize();

            Vec3d d2 = d1.multiply(-1);

            if (currentDirection.lengthSquared() < EPS) {

                return d1;
            }

            currentDirection = currentDirection.normalize();

            if (d1.dotProduct(currentDirection) >= 0) {

                return d1;
            }

            return d2;
        }

        private LineWrapper<?> toLineWrapper(FitResult line) {
            double px = mc.player.getX();
            double pz = mc.player.getZ();

            // 玩家到分界线的有符号距离
            double d = line.a() * px + line.b() * pz + line.c();

            // 玩家在分界线上的投影点
            double cx = px - line.a() * d;
            double cz = pz - line.b() * d;

            // 分界线方向
            double dx = line.b();
            double dz = -line.a();

            // 前后各取 1024
            double x1 = cx - dx * 1024;
            double z1 = cz - dz * 1024;

            double x2 = cx + dx * 1024;
            double z2 = cz + dz * 1024;

            return new LineWrapper<>((int) x1, (int) z1, (int) x2, (int) z2);
        }

        private static record FitResult(double a, double b, double c) {
            public float getKDegree() {
                return EntityUtils.rotationToYaw(new Vec3d(b, 0, -a));
            }

            public FitResult add(FitResult other) {
                double a1 = a + other.a;
                double b1 = b + other.b;
                double c1 = c + other.c;
                double len = Math.sqrt(a1 * a1 + b1 * b1);
                return new FitResult(a1 / len, b1 / len, c1 / len);
            }

            public FitResult sub(FitResult other) {
                double a1 = a - other.a;
                double b1 = b - other.b;
                double c1 = c - other.c;
                double len = Math.sqrt(a1 * a1 + b1 * b1);
                return new FitResult(a1 / len, b1 / len, c1 / len);
            }

            public double dot(FitResult other) {
                return a * other.a + b * other.b;
            }
        }
    }
}
