package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Objects;
import javax.annotation.Nullable;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.managers.Tasks;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.WorldUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class PlayerInteractionMixin implements PlayerInteractionAccess {
    @Shadow
    private float currentBreakingProgress;

    @Shadow
    private boolean breakingBlock;

    @Shadow
    private ItemStack selectedStack;

    @Shadow
    protected abstract void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

    @Shadow
    public abstract boolean breakBlock(BlockPos pos);

    @Shadow
    private int blockBreakingCooldown;

    @Shadow
    private float blockBreakingSoundCooldown;

    @Shadow
    private BlockPos currentBreakingPos;

    /**
     * doubleBreak / failMine 使用的备用挖掘槽位。
     *
     * <p>主挖掘位置切走后，旧位置如果仍值得继续复用，就暂存在这里，等待后续 stop 或自动完成逻辑消费。
     */
    @Nullable
    @Unique
    private BlockPos currentFailBreakPos = null;

    /**
     * failBreak 槽位建立时对应的 start tick。
     *
     * <p>它和 {@link #currentFailBreakPos} 一起构成“备用挖掘会话”的最小状态，用于按服务端 start/stop
     * 状态机推导理论进度，而不是依赖客户端原版破坏动画。
     */
    @Unique
    private int failBreakStartTick;

    /**
     * 返回当前主挖掘槽位绑定的位置。
     *
     * <p>这是服务端后续 stop 包默认要对应的位置，也是 optimizeOneBlock 复用的主状态位。
     */
    @Override
    public BlockPos getCurrentMiningPos() {
        return currentBreakingPos;
    }

    /**
     * 本地清空当前主挖掘位。
     *
     * <p>这里只处理客户端会话态，不主动补发 stop。调用方通常在确定该上下文已经无效、或者需要显式重建
     * start 上下文时使用它。
     */
    @Override
    public void resetCurrentMiningPos() {
        currentBreakingPos = new BlockPos(-1, -1, -1);
        resetLocalMiningProgress();
    }

    /**
     * 读取当前 failBreak 槽位。
     *
     * <p>这里直接暴露当前备用槽位本身，不再由 doubleBreak 开关决定可见性；是否允许写入或消费该槽位，
     * 由具体调用路径自行判断。
     */
    @Override
    @Nullable
    public BlockPos getCurrentFailBreakPos() {
        return currentFailBreakPos;
    }

    /**
     * 判断 failBreak 槽位当前是否空闲。
     *
     * <p>这是对外暴露的稳定语义，调用方不需要再依赖 null 细节自行拼装状态判断。
     */
    @Override
    public boolean isFailBreakEmpty() {
        return currentFailBreakPos == null;
    }

    /**
     * 用指定工具预测当前主挖掘位的理论进度。
     *
     * <p>这个方法不读取当前手持物，而是假设“如果现在使用 tool 继续挖”，服务端从最近一次 start 开始，
     * 理论上已经累计了多少进度。它服务于切工具收益估算，而不是本地动画显示。
     */
    @Override
    public float predictCurrentMiningProgressWithTool(ItemStack tool) {
        BlockState block = this.client.world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        float miningSpeed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(this.client.player, block, tool);
        float speed = WorldUtils.calcBlockBreakingDelta(block, this.client.world, currentBreakingPos, miningSpeed);
        int ticksSinceLastStart = Tasks.getTick() - MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
        return speed * ticksSinceLastStart;
    }

    /**
     * 读取当前主挖掘位进度。
     *
     * <p>优先返回原版仍然有效的本地缓存进度；如果当前不是原版持续挖掘路径，且允许预测，则回退为“最近一次
     * start tick × 当前速度”的理论值。
     *
     * <p>这样可以同时兼容：
     * <ul>
     *     <li>原版正在持续更新的本地挖掘动画</li>
     *     <li>optimizeOneBlock 对同位置状态的复用</li>
     *     <li>quickMine / bypass 对服务端 start-stop 状态机的推导</li>
     * </ul>
     */
    @Override
    public float getCurrentMiningProgress(boolean shouldPredict) {
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        if ((breakingBlock && isCurrentlyBreaking(currentBreakingPos))) {
            return this.currentBreakingProgress == 0.0F ? -1.0F : this.currentBreakingProgress;
        }
        if (!MineExtra.INSTANCE.optimizeOneBlock.get() && !shouldPredict) {
            return this.currentBreakingProgress == 0.0F ? -1.0F : this.currentBreakingProgress;
        }

        float speed = block.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getWorld(),
                currentBreakingPos);
        int ticksSinceLastStart = Tasks.getTick() - MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
        return speed * ticksSinceLastStart;
    }

    /**
     * 读取 failBreak 槽位按当前 tick 推导出的理论进度。
     *
     * <p>这条支线不依赖原版 {@code currentBreakingProgress}，因为 failBreak 本质上是“主挖掘位切走后仍然
     * 继续复用的一段服务端上下文”，其可信来源是 start tick 与当前方块速度。
     */
    @Override
    public float getFailBreakMiningProgress() {
        if (currentFailBreakPos == null) {
            return -1.0F;
        }
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
        if (block.isAir()) {
            return -1.0F;
        }
        float speed = block.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getWorld(),
                currentBreakingPos);
        return (Tasks.getTick() - failBreakStartTick) * speed;
    }

    /**
     * 显式建立一个 failBreak 槽位。
     *
     * <p>成功时会同时：
     * <ul>
     *     <li>登记备用位置</li>
     *     <li>把当前主挖掘位切到该位置，便于后续 stop 复用同一套位置语义</li>
     *     <li>记录该会话对应的 start tick</li>
     * </ul>
     *
     * <p>如果槽位已被占用，则拒绝覆盖，避免多个未完成的备用会话互相踩状态。
     */
    @Unique
    @Override
    public boolean beginFailBreak(BlockPos pos) {
        if (currentFailBreakPos == null) {
            currentFailBreakPos = pos;
            currentBreakingPos = pos;
            failBreakStartTick = MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
            return true;
        }
        return false;
    }

    /**
     * 尝试把当前主挖掘位整体迁入 failBreak 槽位。
     *
     * <p>这是 doubleBreak / 切块续挖最常用的入口，用于在开始处理新方块前，先保留旧方块的服务端挖掘上下文。
     */
    @Unique
    @Override
    public boolean moveCurrentMiningToFailBreak() {
        return beginFailBreak(currentBreakingPos);
    }

    /**
     * 清空 failBreak 槽位和它的时间基线。
     *
     * <p>一旦调用，表示这段备用挖掘上下文已经失效、完成或不再值得继续复用。
     */
    @Unique
    @Override
    public void clearFailBreak() {
        currentFailBreakPos = null;
        failBreakStartTick = 0;
    }

    /**
     * 只复位本地缓存的破坏进度。
     *
     * <p>这是一个纯本地 helper，不做位置切换，也不修改发包状态，用于把多个 stop/start 分支里的进度清理收口。
     */
    @Unique
    private void resetLocalMiningProgress() {
        currentBreakingProgress = 0.0F;
    }

    /**
     * 清理“原版仍在持续挖掘”的本地标记。
     *
     * <p>很多 bypass 分支在提前 stop 时，都需要先把原版 breaking 标志降下来，避免后续 tick 继续按普通挖掘流推进。
     */
    @Unique
    private void clearBreakingState() {
        this.breakingBlock = false;
    }

    /**
     * 执行一次 stop 之后的本地统一收尾。
     *
     * <p>它集中维护三类状态：
     * <ul>
     *     <li>是否清空本地进度缓存</li>
     *     <li>声音冷却归零</li>
     *     <li>交互冷却按 MineExtra 策略重置</li>
     * </ul>
     *
     * <p>这样不同 stop 路径就不需要再各自散写相同字段。
     */
    @Unique
    private void applyPostStopState(boolean resetProgress) {
        if (resetProgress) {
            resetLocalMiningProgress();
        }
        this.blockBreakingSoundCooldown = 0.0F;
        this.blockBreakingCooldown = MineExtra.INSTANCE.cooldownManaging();
    }

    /**
     * 只发送一个 STOP_DESTROY_BLOCK。
     *
     * <p>用于那些“服务端已有有效 start 上下文，当前只需要补一个 stop 包”的路径，例如同位置复用、
     * doubleBreak 补包等。
     */
    @Unique
    private void sendStopBreakPacketInternal(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    /**
     * 先执行一次本地 breakBlock，再发送 STOP_DESTROY_BLOCK 收尾。
     *
     * <p>这是更强语义的“完成一次 break”，用于 quickMine / fakeInstaBreak 这类希望立即把本地表现和 stop
     * 动作一起落地的路径。
     */
    @Unique
    private void sendBreakAndStopPacket(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
            this.breakBlock(pos);
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        });
    }

    @Unique
    private void continueSameBlockMining(BlockPos pos, Direction direction) {
        this.currentBreakingPos = pos;
        this.currentBreakingProgress = getCurrentMiningProgress(true);
        this.breakingBlock = true;
        this.selectedStack = this.client.player.getMainHandStack();
        this.client.world.setBlockBreakingInfo(
                this.client.player.getId(), this.currentBreakingPos, this.getBlockBreakingProgress());
        this.updateBlockBreakingProgress(pos, direction);
    }

    @Unique
    private void sendExtraGrimBadPacketsStop(BlockPos pos, Direction direction) {
        sendStopBreakPacketInternal(pos, direction);
    }

    @Unique
    private boolean tryAbortCurrentMiningIntoFailBreak() {
        if (!MineExtra.INSTANCE.doubleBreak.get() || !isFailBreakEmpty()) {
            return false;
        }
        ClientPlayerEntity playerEntity = MinecraftClient.getInstance().player;
        if (!playerEntity.canInteractWithBlockAt(this.currentBreakingPos, 1.0D)) {
            return false;
        }
        BlockState state = MinecraftClient.getInstance().world.getBlockState(this.currentBreakingPos);
        if (state.isAir() || state.isLiquid()) {
            return false;
        }
        float speed = state.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getEntityWorld(),
                currentBreakingPos);
        if (speed <= 0) {
            return false;
        }
        moveCurrentMiningToFailBreak();
        MineExtra.INSTANCE.onPostStopMiningFastBreak(currentBreakingPos, speed, currentBreakingProgress);
        return true;
    }

    /**
     * 判断当前 failBreak 槽位是否已经不值得继续保留。
     *
     * <p>清理条件包括：
     * <ul>
     *     <li>玩家或模式已经不再允许继续按生存挖掘处理</li>
     *     <li>槽位方块已空气化或液体化</li>
     *     <li>按 start tick 推导已经理论完成，不再需要继续挂起</li>
     *     <li>玩家与该位置距离过远，继续复用失去意义</li>
     * </ul>
     */
    @Unique
    private boolean shouldClearFailBreakBecauseInvalidState() {
        if (MinecraftClient.getInstance().world == null) {
            return false;
        }
        BlockState state = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
        if (client.player == null || gameMode != GameMode.SURVIVAL) {
            return true;
        }
        if (state == null || state.isAir() || state.isLiquid()) {
            return true;
        }
        float speed = state.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().world,
                currentFailBreakPos);
        if (speed > 0.0F && ((Tasks.getTick() - failBreakStartTick) * speed > 1.0F)) {
            return true;
        }
        return client.player != null
                && client.player.getPos().squaredDistanceTo(currentBreakingPos.toCenterPos()) > 225;
    }

    @Shadow
    private GameMode gameMode;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;

    /**
     * 对外暴露的 stop 语义入口。
     *
     * <p>这里只发送 stop 包，不附带本地 breakBlock；更强的“break + stop”组合由内部 helper 单独负责。
     */
    @Override
    @Unique
    public void sendBreakPacket(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    /**
     * 对外暴露的 start 语义入口。
     *
     * <p>调用时会复位本地进度，并在非 instant break 情况下切换主挖掘位置。这样外部模块就不需要再知道
     * “什么时候改 currentBreakingPos、什么时候只发 start 包” 这类内部细节。
     */
    @Override
    @Unique
    public void startMiningBlock(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            resetLocalMiningProgress();
            BlockState state = client.world.getBlockState(pos);
            if (!state.isAir()
                    && state.calcBlockBreakingDelta(this.client.player, this.client.player.getEntityWorld(), pos)
                            >= 1.0F) {
                // insta break do not change current breaking pos
            } else {
                currentBreakingPos = pos;
            }
            return new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    @Override
    @Unique
    public void syncSelectedHotbar(int x) {
        client.player.getInventory().setSelectedSlot(x);
        this.syncSelectedSlot();
    }
    //    public void autoSendStopPacket(){
    //        if(currentBreakingPos != null){
    //            sendStopBreakPacket(currentBreakingPos, Direction.UP);
    //        }
    //    }
    public float calculateBreakingSpeed(BlockPos blockPos) {
        if (this.gameMode.isCreative()) {
            return 100000.0f;
        } else {
            BlockState state = this.client.world.getBlockState(blockPos);
            return state.calcBlockBreakingDelta(this.client.player, this.client.player.getWorld(), blockPos);
        }
    }

    public boolean preCalculateInstantBreak(BlockPos blockPos) {
        if (this.gameMode.isCreative()) {
            return true;
        } else {
            BlockState state = this.client.world.getBlockState(blockPos);
            float speed =
                    state.calcBlockBreakingDelta(this.client.player, this.client.player.getEntityWorld(), blockPos);
            return MineExtra.INSTANCE.shouldTreatAsInstantBreak(speed);
        }
    }

    // speed up with early packet when progress>0.7
    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/tutorial/TutorialManager;onBlockBreaking(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;F)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void fastbreak(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        MineExtra mineExtra = MineExtra.INSTANCE;
        if (mineExtra.shouldExecuteFastBreak(this.currentBreakingProgress)) {
            clearBreakingState();
            sendBreakAndStopPacket(pos, direction);
            float speed = blockState.calcBlockBreakingDelta(
                    MinecraftClient.getInstance().player, MinecraftClient.getInstance().world, pos);
            mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
            applyPostStopState(!mineExtra.optimizeOneBlock.get());
            cir.cancel();
        }
    }

    //
    // fixme: fix
    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 1,
                            shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true)
    public void samePositionOptimize(
            BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, BlockState blockState) {
        // remove the flag, can work even if fastbreak off
        MineExtra mineExtra = MineExtra.INSTANCE;
        if (mineExtra.optimizeOneBlock.get()) {

            if (Objects.equals(pos, currentBreakingPos)) {

                if (!mineExtra.shouldExecuteOptimizeOneBlock()) {
                    return;
                }
                continueSameBlockMining(pos, direction);
                cir.setReturnValue(true);
            } else {
                float predictedProgress = getCurrentMiningProgress(true);
                if (mineExtra.shouldTryDoubleBreak(predictedProgress) && tryAbortCurrentMiningIntoFailBreak()) {
                    sendStopBreakPacketInternal(currentBreakingPos, direction);
                    if (mineExtra.shouldSendGrimBadPacketsExtraStop()) {
                        sendExtraGrimBadPacketsStop(pos, direction);
                    }
                }
            }
        }
    }

    @Redirect(
            method = "cancelBlockBreaking",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;currentBreakingProgress:F"))
    private void sameBlockOptimizeDoNotResetProgress(ClientPlayerInteractionManager instance, float value) {
        // do not set the fucking value
        if (!MineExtra.INSTANCE.optimizeOneBlock.get()) {
            ((PlayerInteractionMixin) (Object) instance).currentBreakingProgress = value;
        }
    }

    @Redirect(
            method = "cancelBlockBreaking",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak(ClientPlayNetworkHandler instance, Packet packet) {

        if (!MineExtra.INSTANCE.optimizeOneBlock.get() && tryAbortCurrentMiningIntoFailBreak()) {
            // we make optimizeOneBlockMine delay its destroy packet to changing the currentPosition in method
            // sameBlockOptimize
            Vec3d shouldFacing = currentBreakingPos
                    .toCenterPos()
                    .subtract(MinecraftClient.getInstance().player.getEyePos());
            Direction dir = Direction.getFacing(shouldFacing).getOpposite();
            sendStopBreakPacketInternal(currentBreakingPos, dir);
            return;
        }

        instance.sendPacket(packet);
    }

    @Redirect(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak2(
            ClientPlayNetworkHandler instance, Packet packet, @Local(argsOnly = true) Direction direction) {
        // conflict with optimizeOneBlock

        if (!MineExtra.INSTANCE.optimizeOneBlock.get() && tryAbortCurrentMiningIntoFailBreak()) {
            // we make optimizeOneBlockMine delay its destroy packet to check onDoubleBreakAbort() and  changing the
            // currentPosition in method sameBlockOptimize
            sendStopBreakPacketInternal(currentBreakingPos, direction);
            return;
        }

        instance.sendPacket(packet);
    }

    @Shadow
    protected abstract int getBlockBreakingProgress();

    @Shadow
    public abstract boolean updateBlockBreakingProgress(BlockPos pos, Direction direction);

    @Shadow
    protected abstract boolean isCurrentlyBreaking(BlockPos pos);

    @Shadow
    protected abstract void syncSelectedSlot();

    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 0,
                            shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacket(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        MineExtra.INSTANCE.onStartingMine(pos, Float.MAX_VALUE, true);
    }

    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I",
                            shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            cancellable = true)
    public void fastBreakCreative(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        applyPostStopState(false);
        if (MineExtra.INSTANCE.quickMine.get()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        float speed = blockState.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getWorld(),
                pos);
        MineExtra mineExtra = MineExtra.INSTANCE;
        mineExtra.onStartingMine(pos, speed, false);
        if (!mineExtra.shouldUseQuickMine() || blockState.isAir()) {
            return;
        }
        if (mineExtra.shouldTriggerEarlyStop(speed)) {
            clearBreakingState();
            sendBreakAndStopPacket(pos, direction);
            mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
            applyPostStopState(!mineExtra.optimizeOneBlock.get());
        } else if (mineExtra.shouldQueueNextTickEarlyBreak(speed)) {
            mineExtra.queueNextTickEarlyBreak();
        }
    }

    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 0,
                            shift = At.Shift.AFTER),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacketWhenUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MineExtra.INSTANCE.onStartingMine(pos, Float.MAX_VALUE, true);
        applyPostStopState(false);
    }

    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",
                            ordinal = 0),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakNextTickPacketSend(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MineExtra mineExtra = MineExtra.INSTANCE;
        if (mineExtra.shouldApplyNextTickFirstBreak()) {
            BlockState blockState = MinecraftClient.getInstance().world.getBlockState(pos);
            float speed = blockState.calcBlockBreakingDelta(
                    MinecraftClient.getInstance().player,
                    MinecraftClient.getInstance().player.getWorld(),
                    pos);
            clearBreakingState();
            sendBreakAndStopPacket(pos, direction);
            mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress + speed);
            applyPostStopState(!mineExtra.optimizeOneBlock.get());
            //
            MinecraftClient.getInstance()
                    .world
                    .setBlockBreakingInfo(MinecraftClient.getInstance().player.getId(), this.currentBreakingPos, -1);
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER))
    private void onCommonBlockBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MineExtra.INSTANCE.onPostStopMiningLegally(pos);
    }

    @Redirect(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;currentBreakingProgress:F",
                            ordinal = 4))
    private void onSameBlockDoNotResetProgress(ClientPlayerInteractionManager instance, float value) {
        if (!MineExtra.INSTANCE.optimizeOneBlock.get()) {
            ((PlayerInteractionMixin) (Object) instance).currentBreakingProgress = value;
        }
    }

    //    @Inject(method = "getReachDistance",at = @At(value = "HEAD"),cancellable = true)
    //    public void widerReachDistance(CallbackInfoReturnable<Float> cir){
    //
    //    }
    @Inject(method = "hasLimitedAttackSpeed", at = @At(value = "HEAD"), cancellable = true)
    public void cancelAttackSpeedLimit(CallbackInfoReturnable<Boolean> cir) {
        if (CombatTasks.getCombatExtra().noCooldown.get()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo ci) {
        if (!isFailBreakEmpty() && shouldClearFailBreakBecauseInvalidState()) {
            clearFailBreak();
        }
    }

    @ModifyExpressionValue(
            method = "clickSlot",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/entity/player/PlayerEntity;currentScreenHandler:Lnet/minecraft/screen/ScreenHandler;"))
    public ScreenHandler onClickSlot(ScreenHandler original, @Local(argsOnly = true) PlayerEntity player) {
        return player instanceof ClientPlayerAccess clientPlayer ? clientPlayer.getServerScreenHandler() : original;
    }

    @Inject(method = "isCurrentlyBreaking", at = @At("HEAD"), cancellable = true)
    public void onCurrentlyBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // completely ignore the damage change
        cir.setReturnValue(Objects.equals(pos, currentBreakingPos)
                && ItemStackUtils.matchItemMiningAbility(this.client.player.getMainHandStack(), this.selectedStack));
    }
}
