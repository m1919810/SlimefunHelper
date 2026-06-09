package me.matl114.accessors.hacks;

import javax.annotation.Nullable;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess {
    /**
     * 以当前交互管理器的主挖掘语义，对一个方块建立新的 mining 上下文。
     *
     * <p>这个动作不只是“发一个 START_DESTROY_BLOCK 包”。实现方还需要同步维护当前主挖掘位置、
     * 本地进度复位时机，以及与 MineExtra 统计链保持一致的 start 上下文。
     *
     * <p>对于可瞬间破坏的方块，实现方允许保留当前 {@code currentBreakingPos}，以兼容同位置复用和
     * 部分 bypass 流程对服务端状态机的依赖。
     */
    public void startMiningBlock(BlockPos pos, Direction direction);

    /**
     * 对指定位置发送“break 收尾”语义。
     *
     * <p>这里的 break 明确表示结束一次服务端认可的挖掘流程；默认实现是发送
     * {@code STOP_DESTROY_BLOCK}，而不是简单地表达“本地把方块敲掉”。
     *
     * <p>调用方应把它理解成一次 stop/finish 动作入口，而不是自由组合的底层包接口。
     */
    public void sendBreakPacket(BlockPos pos, Direction direction);

    /**
     * 强制同步客户端当前选中的快捷栏槽位。
     *
     * <p>用于挖矿辅助模块在切工具后，立即把本地选择状态和交互管理器的服务端同步状态对齐。
     */
    public void syncSelectedHotbar(int x);

    /**
     * 基于当前位置和当前游戏模式，预判一次 start 后是否会立刻满足 instant break 条件。
     *
     * <p>这个判断主要服务于快速挖掘、伪秒挖和 bypass 策略选择，不要求一定与客户端展示进度一致；
     * 它更接近“当前这一击是否值得按 instant 分支处理”的策略信号。
     */
    public boolean preCalculateInstantBreak(BlockPos pos);

    /**
     * 计算当前位置的单 tick 挖掘速度。
     *
     * <p>返回值用于上层估算完成时间、判断 quickMine 阈值，以及评估 fakeInstaBreak 是否成立。
     */
    public float calculateBreakingSpeed(BlockPos pos);

    /**
     * 读取当前主挖掘槽位绑定的位置。
     *
     * <p>这是客户端当前正在服务端状态机里复用的主挖掘位，不等价于画面上显示的破坏动画来源。
     */
    public BlockPos getCurrentMiningPos();

    /**
     * 清空当前主挖掘位置，并把本地缓存的挖掘进度复位。
     *
     * <p>它只负责本地会话态清理，不承诺向服务端补发 stop 包。
     */
    public void resetCurrentMiningPos();

    /**
     * 读取当前 failBreak 槽位的位置。
     *
     * <p>当 doubleBreak/failMine 语义未启用时，实现方可以直接返回 {@code null}，表示外部不应继续
     * 依赖这一槽位。
     */
    public BlockPos getCurrentFailBreakPos();

    /**
     * 判断 failBreak 槽位是否为空。
     *
     * <p>调用方应优先使用这个语义方法，而不是自己通过 {@code getCurrentFailBreakPos() == null}
     * 推断内部状态。
     */
    public boolean isFailBreakEmpty();

    /**
     * 把一个位置登记为 failBreak 槽位。
     *
     * <p>成功时，实现方需要同时建立该槽位的起始 tick 和进度基线；失败通常表示当前已经存在未消费的
     * failBreak 槽位，不能被新的位置覆盖。
     */
    public boolean beginFailBreak(BlockPos pos);

    /**
     * 尝试把当前主挖掘位置转移到 failBreak 槽位。
     *
     * <p>这是 doubleBreak/切块续挖场景的主要入口。返回值明确表示这次转移是否真的建立了新的
     * failBreak 上下文。
     */
    public boolean moveCurrentMiningToFailBreak();

    /**
     * 清空 failBreak 槽位及其关联起始时间。
     *
     * <p>调用后表示该备用挖掘会话不再可继续复用，后续逻辑应重新建立新的 failBreak 上下文。
     */
    public void clearFailBreak();

    /**
     * 读取 failBreak 槽位按当前 tick 推算得到的进度。
     *
     * <p>这个值是基于服务端 start 时刻和当前方块速度做的推演值，不是客户端原生的破坏动画进度。
     * 当槽位为空、方块已失效或空气化时，返回负值表示不可用。
     */
    public float getFailBreakMiningProgress();

    /**
     * 假设使用给定工具，预测当前主挖掘位的理论进度。
     *
     * <p>主要用于切工具后的收益评估与策略决策，不会直接修改本地挖掘状态。
     */
    public float predictCurrentMiningProgressWithTool(ItemStack tool);

    /**
     * 读取当前主挖掘位的进度。
     *
     * <p>当 {@code shouldPredict} 为 true 时，实现方允许在本地原版进度不可直接复用时，按 start tick
     * 和当前速度推导一个理论进度；为 false 时，应尽量返回更贴近本地缓存的值。
     */
    public float getCurrentMiningProgress(boolean shouldPredict);

    /**
     * 兼容旧调用名：语义等价于 {@link #startMiningBlock(BlockPos, Direction)}。
     */
    default void sendStartBreakPacket(BlockPos pos, Direction direction) {
        startMiningBlock(pos, direction);
    }

    /**
     * 兼容旧调用名：语义等价于 {@link #sendBreakPacket(BlockPos, Direction)}。
     */
    default void sendStopBreakPacket(BlockPos pos, Direction direction) {
        sendBreakPacket(pos, direction);
    }

    /**
     * 兼容旧的 failBreak 设置入口。
     *
     * <p>传入 {@code null} 时表示清空 failBreak；传入有效位置时表示尝试建立新的 failBreak 上下文。
     */
    default boolean setStartFailBreakPos(@Nullable BlockPos pos) {
        if (pos == null) {
            clearFailBreak();
            return true;
        }
        return beginFailBreak(pos);
    }

    /**
     * 把原版 {@link ClientPlayerInteractionManager} 视为本接口语义边界。
     */
    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager) {
        return (PlayerInteractionAccess) manager;
    }
}
