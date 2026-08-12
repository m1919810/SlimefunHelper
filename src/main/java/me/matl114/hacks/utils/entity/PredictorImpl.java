package me.matl114.hacks.utils.entity;

import java.util.*;
import me.matl114.events.Event;
import me.matl114.managers.Tasks;
import me.matl114.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Vec3d;

public class PredictorImpl implements Predictor {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final Entity owner;
    private final Deque<KnownPosition> positions = new ArrayDeque<>();
    private static final int MAX_HISTORY = 30;

    public PredictorImpl(Entity owner) {
        this.owner = owner;
    }

    public void tick() {
        while (positions.size() > MAX_HISTORY) {
            positions.removeFirst();
        }
        if (mc.player == this.owner) {
            addRecord(new KnownPosition(owner.getPos(), Tasks.getTick()));
        }
    }

    public void onEntityPositionPost(Event<EntityPositionS2CPacket> event) {
        EntityPositionS2CPacket packet = event.context();
        if (packet.getEntityId() != owner.getId()) return;
        addRecord(new KnownPosition(owner.getPos(), Tasks.getTick()));
    }

    public void onEntityPositionMove(Event<EntityS2CPacket> event) {
        EntityS2CPacket packet = event.context();
        if (packet.getEntity(mc.world) == owner) {
            addRecord(new KnownPosition(owner.getPos(), Tasks.getTick()));
        }
    }

    /**
     * 基于最近两个已知位置（收到的记录）计算当前移动向量
     */
    /**
     * 基于最近两个已知位置（收到的记录）计算当前移动速度（每 tick 的位移向量）
     * @return 速度向量；若 tick 差为 0，则返回零向量（同一时刻无有效速度）
     */
    public Vec3d getKnownDeltaMovement() {
        if (positions.size() < 2) return Vec3d.ZERO;
        Iterator<KnownPosition> it = positions.descendingIterator();
        KnownPosition newest = it.next();
        KnownPosition second = it.next();
        int dt = newest.tick() - second.tick();
        if (dt == 0) {
            // 同一 tick 内无法计算速度，返回零向量（或根据需求返回位移差）
            return newest.vec3d().subtract(second.vec3d());
        }
        Vec3d displacement = newest.vec3d().subtract(second.vec3d());
        return displacement.multiply(1.0 / dt);
    }

    /**
     * 预测未来位置
     * @param ticksLater 未来刻数（>0）
     * @param method 1=线性回归, 2=二次回归, 3=NVPredictor
     * @param useTicksBefore 只使用过去 useTicksBefore 刻内的历史记录
     */
    public Vec3d predict(int ticksLater, int method, int useTicksBefore) {
        if (ticksLater == 0) return owner.getPos();
        int currentTick = Tasks.getTick();
        Vec3d currentPos = owner.getPos();

        List<KnownPosition> histRecords = new ArrayList<>();
        KnownPosition lastKnown = null;
        // 如果当前在范围内，则将其前一个加入。？
        boolean add = false;
        for (KnownPosition pos : positions) {
            if (pos.tick() >= currentTick - useTicksBefore) {
                add = true;
                if (lastKnown != null) {
                    histRecords.add(lastKnown);
                }
            }
            lastKnown = pos;
        }
        // 如果最后一个需要加入。那么add必然为true
        if (lastKnown != null && add) {
            histRecords.add(lastKnown);
        }
        // 如果没有，则直接返回
        if (histRecords.isEmpty()) {
            return currentPos;
        }
        // 最后刻
        // 例如: 我们选取10000 - 10005 6个时间刻进行预测
        // 当前 数据点 10001 10003 10004
        // lastTick0 = 10004
        // firstTick0 = 10001
        // startTick = 10000
        // 我们需要取history = 10000, 10001, 10002, 10003, 10004 这5个点
        // 10000, 10001 <- 10001 数据点
        // 10002 <- 10001, 10003插值
        // 这样》
        int lastTick0 = histRecords.get(histRecords.size() - 1).tick();
        int firstTick0 = histRecords.get(0).tick();
        int startTick0 = currentTick - useTicksBefore;
        if (currentTick + ticksLater < firstTick0) {
            return histRecords.get(0).vec3d();
        }
        if (startTick0 >= lastTick0) {
            // 窗口内没有记录，直接返回当前
            return currentPos;
        }
        // 同步到10000
        if (firstTick0 > startTick0) {
            KnownPosition firstPosition = histRecords.get(0);
            histRecords.add(0, new KnownPosition(firstPosition.vec3d(), startTick0));
            firstTick0 = startTick0;
        }
        // startTick0, statTick0 + 1,.... lastTick0
        // usableTicks - 10000, ... 10004 = 5个点
        int usableTicks = lastTick0 - startTick0 + 1;
        // lastTick0 + 1.。。 currentTick left for empty
        // 10005是empty的 需要在ticksAfter加入
        int blankTicks = currentTick - lastTick0;
        if (usableTicks < 2) {
            return currentPos;
        }

        Vec3d[] history = new Vec3d[usableTicks];
        int currentIndex = 0;
        KnownPosition pos = histRecords.get(currentIndex);
        KnownPosition lastPos = null;
        for (int i = 0; i < history.length; ++i) {
            int realTick = startTick0 + i;
            // realTick <= historyRecords.getLast().tick()
            while (true) {
                if (pos.tick() == realTick) {
                    history[i] = pos.vec3d();
                    break;
                }
                if (lastPos != null && lastPos.tick() < realTick && pos.tick() > realTick) {
                    // pos.tick > realTick > lastPos.tick
                    history[i] = pos.vec3d()
                            .multiply(pos.tick() - realTick)
                            .add(lastPos.vec3d().multiply(realTick - lastPos.tick()))
                            .multiply(1.0D / (pos.tick() - lastPos.tick()));
                    break;
                }
                lastPos = pos;
                currentIndex += 1;
                if (currentIndex >= histRecords.size()) {
                    // impossible
                    throw new RuntimeException("?   WTF");
                }
                pos = histRecords.get(currentIndex);
            }
        }
        int futureSteps = blankTicks + ticksLater;
        if (futureSteps <= 0) {
            return history[history.length - 1 + futureSteps];
        }
        switch (method) {
            case 1 -> {
                return MathUtils.linearPrediction(history, futureSteps);
            }
            case 2 -> {
                return MathUtils.quadraticPrediction(history, futureSteps);
            }
            case 3 -> {
                Vec3d[] ring = Arrays.copyOf(history, history.length);
                int currentIdx = history.length - 1;
                return new MathUtils.NVPredictor(ring, () -> currentIdx).compute(futureSteps);
            }
            case 4 -> {
                Vec3d[] ring = Arrays.copyOf(history, history.length);
                int currentIdx = history.length - 1;
                return new MathUtils.RotationalPredictor(ring, () -> currentIdx).compute(futureSteps);
            }

            default -> {
                return currentPos;
            }
        }
    }

    public List<KnownPosition> getLastKnownPositions(int lastNumber) {
        if (lastNumber <= 0) return Collections.emptyList();

        // 先收集已有的历史记录（从旧到新）
        List<KnownPosition> result = new ArrayList<>(positions);

        // 如果历史记录超过所需数量，只保留最后 lastNumber 个
        if (result.size() > lastNumber) {
            result = result.subList(result.size() - lastNumber, result.size());
        }

        // 如果不足，用当前实体位置补全（添加在末尾）
        int missing = lastNumber - result.size();
        if (missing > 0) {
            Vec3d currentPos = owner.getPos();
            int currentTick = Tasks.getTick();
            for (int i = 0; i < missing; i++) {
                result.add(new KnownPosition(currentPos, currentTick));
            }
        }

        return result;
    }

    private void addRecord(KnownPosition record) {
        // 若与队尾 tick 相同，则替换（避免重复记录同一时刻）
        var pos = positions.peekLast();
        // remove duplicate packets
        if (!Objects.equals(pos, record)) {
            positions.add(record);
        }
    }

    public static record KnownPosition(Vec3d vec3d, int tick) {}
}
