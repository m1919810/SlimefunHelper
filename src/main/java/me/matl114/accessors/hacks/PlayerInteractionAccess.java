package me.matl114.accessors.hacks;

import javax.annotation.Nullable;
import me.matl114.utils.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
    default void sendBreakPacket(boolean silent) {
        BlockPos currentPos = getCurrentMiningPos();
        sendBreakPacket(currentPos, silent);
    }

    default void sendBreakPacket(BlockPos currentPos, boolean silent) {
        Vec3d shouldFacing = currentPos
                .toCenterPos()
                .subtract(MinecraftClient.getInstance().player.getEyePos());
        Direction dir = Direction.getFacing(shouldFacing).getOpposite();
        sendBreakPacket(currentPos, dir, silent);
    }

    public void sendBreakPacket(BlockPos pos, Direction direction, boolean silent);

    public boolean breakIfComplete();

    public void abortBreak(Direction direction);

    /**
     * 强制同步客户端当前选中的快捷栏槽位。
     *
     * <p>用于挖矿辅助模块在切工具后，立即把本地选择状态和交互管理器的服务端同步状态对齐。
     */
    public void syncSelectedHotbar(int x);

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
     * <p>这是一个独立的备用挖掘槽位视图，不应由 doubleBreak 开关直接屏蔽；是否允许建立或消费该槽位，
     * 由具体触发路径自己决定。
     */
    public BlockPos getCurrentFailBreakPos();

    /**
     * 判断 failBreak 槽位是否为空。
     *
     * <p>调用方应优先使用这个语义方法，而不是自己通过 {@code getCurrentFailBreakPos() == null}
     * 推断内部状态。
     */
    public boolean isFailBreakEmpty();

    public int getCurrentMiningTicks();
    /**
     * 假设使用给定工具，预测当前主挖掘位的理论进度。
     *
     * <p>主要用于切工具后的收益评估与策略决策，不会直接修改本地挖掘状态。
     */
    /**
     * 用指定工具预测当前主挖掘位的理论进度。
     *
     * <p>这个方法不读取当前手持物，而是假设“如果现在使用 tool 继续挖”，服务端从最近一次 start 开始，
     * 理论上已经累计了多少进度。它服务于切工具收益估算，而不是本地动画显示。
     */
    default float predictCurrentMiningProgressWithTool(ItemStack tool) {
        BlockPos currentBreakingPos = getCurrentMiningPos();
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        float miningSpeed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                MinecraftClient.getInstance().player, block, tool);
        float speed = WorldUtils.calcBlockBreakingDelta(
                block, MinecraftClient.getInstance().world, currentBreakingPos, miningSpeed);
        int ticksSinceLastStart = getCurrentMiningTicks();
        return speed * ticksSinceLastStart;
    }

    public int getFailBreakMiningTicks();
    /**
     * 读取 failBreak 槽位按当前 tick 推导出的理论进度。
     *
     * <p>这条支线不依赖原版 {@code currentBreakingProgress}，因为 failBreak 本质上是“主挖掘位切走后仍然
     * 继续复用的一段服务端上下文”，其可信来源是 start tick 与当前方块速度。
     */
    default float getFailBreakMiningProgress() {
        BlockPos currentFailBreakPos = getCurrentFailBreakPos();
        if (currentFailBreakPos == null) {
            return -1.0F;
        }
        return predictFailMiningProgressWithTool(
                MinecraftClient.getInstance().player.getMainHandStack(), 0);
    }

    /**
     * 假设使用给定工具，预测当前主挖掘位的理论进度。
     *
     * <p>主要用于切工具后的收益评估与策略决策，不会直接修改本地挖掘状态。
     */
    default float predictFailMiningProgressWithTool(ItemStack tool, int extraTick) {
        BlockPos currentBreakingPos = getCurrentFailBreakPos();
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        float miningSpeed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                MinecraftClient.getInstance().player, block, tool);
        float speed = WorldUtils.calcBlockBreakingDelta(
                block, MinecraftClient.getInstance().world, currentBreakingPos, miningSpeed);
        int ticksSinceLastStart = getFailBreakMiningTicks() + extraTick;
        return speed * ticksSinceLastStart;
    }

    /**
     * 读取当前主挖掘位的进度。
     *
     * <p>当传入 tool 时，按该工具推导理论进度；传入 null 时，优先返回原版本地缓存进度，再按当前主手回退到理论值。
     */
    public float getCurrentMiningProgress(@Nullable ItemStack tool);

    /**
     * 兼容旧调用名：语义等价于 {@link #startMiningBlock(BlockPos, Direction)}。
     */
    default void sendStartBreakPacket(BlockPos pos, Direction direction) {
        startMiningBlock(pos, direction);
    }

    default void sendStartBreakPacket(BlockPos pos) {
        Vec3d shouldFacing =
                pos.toCenterPos().subtract(MinecraftClient.getInstance().player.getEyePos());
        Direction direction = Direction.getFacing(shouldFacing).getOpposite();
        sendStartBreakPacket(pos, direction);
    }

    default void sendAbortBreakPacket() {
        abortBreak(Direction.DOWN);
    }

    public boolean sendFailBreakCurrentPos(@Nullable Direction direction);

    public int getMiningCooldown();

    public void setMiningCooldown(int vla);

    public ActionResult simulateInteractBlock(Hand hand, BlockHitResult hitResult);

    public ActionResult simulateInteractItem(Hand hand);

    /**
     * 把原版 {@link ClientPlayerInteractionManager} 视为本接口语义边界。
     */
    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager) {
        return (PlayerInteractionAccess) manager;
    }
}
