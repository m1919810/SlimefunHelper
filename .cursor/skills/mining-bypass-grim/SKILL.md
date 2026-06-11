---
name: mining-bypass-grim
description: 理解并维护 SlimefunHelper 中围绕 `PlayerInteractionMixin`、`MineExtra`、`PacketMine` 构建的挖矿加速与 Grim 绕过体系。用于分析 fastbreak、same-block-optimize、double-break、packet mine、`BYPASS_GRIM_BAD_PACKETS`，以及它们在 start/stop/currentBreakingPos/failMine/cooldown 惩罚上的关系。
disable-model-invocation: true
---

# mining-bypass-grim

## 目标

在处理 `me.matl114.mixins.hack.PlayerInteractionMixin`、`me.matl114.hacks.modules.mine.MineExtra`、`me.matl114.hacks.modules.mine.PacketMine` 时，始终按同一套运行时模型理解，不要退回到原版客户端 `miningProgress` 的直觉。

## 核心心智模型

### 1. 客户端进度条不是事实源

客户端侧的 `miningProgress/currentBreakingProgress` 只是本地表现层。

这套体系里真正重要的是：

- 发过哪些 `START_DESTROY_BLOCK`
- 发过哪些 `STOP_DESTROY_BLOCK`
- 服务器当前认定的挖掘位置
- 服务器何时更新/保留该位置
- Grim 如何按时间差累计惩罚

分析问题时，先看包序列和服务端状态机，不要先信客户端进度条。

### 2. 单次 start 之后可以重复 stop

服务端不是“一个 start 只允许一个 stop”的朴素模型。

在同一挖掘位置未被正确重置前，可以围绕同一个 start 反复尝试 stop。`PacketMine` 正是把这一点用到最直接的模块：在预测进度足够时连续发 `STOP_DESTROY_BLOCK`，即使玩家本身挖掘速度不足，但是通过累计挖掘时间，可以逼近秒挖效果。

### 3. 高速挖掘时，只发 start 也可能完成破坏

当玩家本身挖掘速度足够高时，服务端可能在单次收到 `START_DESTROY_BLOCK` 后直接完成破坏，而不会修改其认定的挖掘位置。

这意味着：

- 本地 `currentBreakingPos` 可以继续被复用
- “位置没变”本身可以被当成可继续压榨的状态

### 3.5 服务器永远不会同步挖掘状态，我们只能通过经验理解其是否正确工作

### 4. 只要当前挖掘位置不变，就能重复利用旧进度

如果服务端/客户端链路上“当前挖掘位置”没有被切走，那么旧 start 建立的上下文仍可被继续利用。

这正是 `same-block-optimize` / `optimizeOneBlock` 的基础。

### 5. optimizeOneBlock 的本质

`optimizeOneBlock` 不是单纯“减少发包”，而是：

- 玩家 `ABORT` 后又重新对同一位置开始挖
- 拦截新的 start 初始化路径
- 不接受“重新从 0 开始”的客户端模型
- 直接加载旧进度，继续沿用同一位置的挖掘上下文

理解它时，重点看“是否保住同一个 currentBreakingPos”，而不是看动画是否连续。

### 6. PacketMine 是这套秒挖模型的显性样板

`PacketMine` 最直观地展示了这套思路：

- 读取当前挖掘位置
- 预测当前工具下的挖掘进度
- 达到阈值后连续发 stop
- 不依赖原版每 tick 正常推进

因此，分析 `MineExtra` 时，如果抽象层太高，就回到 `PacketMine` 看最小闭环。

### 7. 双挖的本质是利用服务端 failMine

双挖不是“同时维护两个完整挖掘状态机”，而是：

- 在服务端计算进度尚未到阈值时，提前发 `STOP_DESTROY_BLOCK`
- 让服务端把该位置转入 failMine 优化链路
- 该位置会在不久后被服务端自动挖掉
- 无需客户端继续手动补包完成它

必须记住的约束：

- 同一时刻只有一个 failMine
- 当前 failMine 未终止时，不会发起新的 failMine
- 当前 failMine终止当且仅当该方块被挖掉了或者区块被卸载（客户端视角为收到了对应的方块状态更新发包，或者观测到该地方为AIR）
- `currentFailBreakPos` 是这个“挂起中的服务端自动完成位”在客户端的映射

## Grim 的核心威胁

Grim 对这套体系最大的威胁不是“你发了 start/stop”，而是“你发得太快”。

重点看两段时间：

- 从 `start -> break/stop` 的总挖掘时间
- 从 `break/stop -> 下一个 start` 的冷却时间

Grim 会围绕这两条时间轴维护惩罚/阈值。对应到 `MineExtra` 的抽象就是两类风险积累：

- 挖掘总时长风险
- break 到下一个 start 的 cooldown 风险

## BYPASS_GRIM_BAD_PACKETS 的理解方式

`BYPASS_GRIM_BAD_PACKETS` 的目标不是把行为伪装成完全合法的人类挖矿，而是主动污染 Grim 用来记账的时间样本。

### 方法 1 `onGrimSBFastBreakExplode`
```java
var packet = event.context();
if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
        && packet.getPos().getY() < 1145) {
    int duplicate = (doubleBreak.get() && (Tasks.getTick() - lastFinishBreakingTick) >= 5) ? 6 : 1;
    List<PlayerActionC2SPacket> actionPackets = new ArrayList<>();

    for (var i = 0; i < duplicate; ++i) {
        actionPackets.add(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                Direction.DOWN,
                NetworkUtils.generateNextSequence()));
        if ((Tasks.getTick() - lastFinishBreakingTick) >= 5) {
            gainedAdvantageCooldown = (int) (gainedAdvantageCooldown * 0.9);
        } else {
            gainedAdvantageCooldown += (300 - (Tasks.getTick() - lastFinishBreakingTick) * 50);
        }
    }
    PacketManager.schedulePostCallback(packet, () -> {
        for (var pkt : actionPackets) {
            mc.getNetworkHandler().sendPacket(pkt);
        }
    });
}
```
这个方法的作用是让 Grim 记录一个错误的“最小挖掘时间”。

结果是：

- Grim 观察到了一些挖掘空气方块的 start 发包
- Grim认为当前在挖空气
- Grim将最小挖掘时间设置为0
- 这些虚假的start发包由于过于遥远既通过不了grim的检测也通过不了原版的检测，不会担心污染grim/原版维护的当前挖掘位置

理解时不要只看“发了额外 start 包”，要看“Grim 被迫把错误数据写进统计值”。

同时该方法里加入了一些优化方案，在条件充裕（(Tasks.getTick() - lastFinishBreakingTick) >= 5） 通过重复发送START包以降低cooldown惩罚，否则则只发送一个start避免增大惩罚

### 方法 2 `onGrimCooldownResetPackets`

这个方法处理的是另一条时间线：

- 从 break 到下一个 start 的 cooldown 惩罚

策略是在 start 发出后不久，再发送大量错误 start，强行冲刷/重置 Grim 的 cooldown 惩罚累积。

理解时把它看成：

- grim对cooldown的记录是这样的 
- 如果break到下一个start >300ms(6 ticks)则降低惩罚 （*0.9）
- 否则增大惩罚( + 300 - ticks * 50)
- 所以我们过了足够多的时间再发大量的start即可重置其惩罚阈值

### 结论

整个 `GRIM_BADPACKETS` 模式是在同时维护两套东西：

- 对服务端足够有效的真实挖矿包序列
- 专门针对 Grim 的错误发包

前者保证真能挖掉，后者保证 Grim 不容易稳定累计惩罚。

## 代码职责分工

### `PlayerInteractionMixin`

它是执行层，负责：

- 改写 `attackBlock`
- 改写 `updateBlockBreakingProgress`
- 拦截 `cancelBlockBreaking`
- 维护 `currentBreakingPos/currentFailBreakPos`
- 在关键时机提前发 `START/STOP_DESTROY_BLOCK`
- 把 `optimizeOneBlock` / `doubleBreak` / `fakeInstaBreak` 落到真实包序列

### `MineExtra`

它是策略层，负责：

- 暴露 fastbreak 系列配置
- 维护时序统计量
- 判断这次是否还能继续快挖
- 在 Grim 模式下何时注入错误 start
- 管理 cooldown 惩罚与 mining 惩罚

### `PacketMine`

它是最小可见样板，负责：

- 用预测进度驱动 stop 包
- 直接展示“当前挖掘位置不变时如何用 stop 做秒挖”

## 调试顺序

遇到挖矿异常、假进度、Grim 惩罚、双挖失效时，按这个顺序检查：

1. 当前真实目标位置是谁
2. `currentBreakingPos` 是否被错误切走
3. `currentFailBreakPos` 是否仍占用 failMine 槽位
4. 本次是否是 same-block reuse，而不是全新 start
5. 真实 start/stop 是否仍能完成服务端破坏
6. 额外错误 start 是否成功污染 Grim 的总时长统计
7. cooldown reset 包是否在正确窗口发出
8. 是服务端已不买账，还是 Grim 时间惩罚重新稳定了

## 修改时必须遵守的约束

- 不要把客户端显示进度当作唯一依据,我们专门有startMiningTicks记录并且会在必要时直接修改客户端进度
- 不要把 `ABORT`、`START`、`STOP` 看成原版单通道线性流程
- 修改 `optimizeOneBlock` 时，先保护“同位置复用旧进度”这个本质
- 修改双挖时，先确认 failMine 单槽约束是否被破坏
- 修改 Grim 绕过时，分别验证“总挖掘时间污染”和“cooldown 惩罚重置”两条链，而不是混在一起看
- 如果一个修复会让 `currentBreakingPos` 提前变化，要重新评估 `PacketMine` 和 `same-block-optimize`

## 关键文件

- `src/main/java/me/matl114/mixins/hack/PlayerInteractionMixin.java`
- `src/main/java/me/matl114/hacks/modules/mine/MineExtra.java`
- `src/main/java/me/matl114/hacks/modules/mine/PacketMine.java`

## 输出要求

当需要向用户解释这套体系时，优先输出：

- 当前服务端视角的挖掘状态机
- start / stop / abort / failMine 的演变关系
- 哪个模块在记账，哪个模块在发包，哪个模块在扰乱 Grim
- 哪条时间轴出了问题

除非用户明确要求，不要退回函数级逐行讲解。