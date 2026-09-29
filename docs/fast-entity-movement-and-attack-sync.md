---
name: fast-entity-movement-attack-sync
description: 分析实体高速移动时客户端命中而服务端判定未命中的原因，追踪 ServerPlayNetworkHandler、EntityTrackerEntry、客户端插值与位置同步机制，并解释预测器的作用。
---
# 生物高速移动时的攻击落空与位置同步

## 原问题

为什么生物高速移动时看起来近在眼前，却有时无法攻击到？实体位置如何同步？为什么需要预测器？重点关注网络监听器与实体同步机制。

## 范围与命名说明

分析基于本仓库合并的 Minecraft 1.21.11 Yarn 源码。用户提到的 `PacketListenerImpl`、`ServerEntity` 是另一套映射常见的名称；本源码内相应的主要类分别是 `ServerPlayNetworkHandler` 和 `EntityTrackerEntry`。以下按仓库实际类名引用。

## 直接结论

**客户端看到的位置、客户端选择攻击目标时的位置，与服务器收到攻击包并验证时的位置并非同一时刻的状态。** 服务端实体按追踪间隔发送相对位移或绝对位置同步包，网络传输与客户端应用包也会增加延迟。高速目标在这段时间内移动得更远，所以玩家可能看到生物仍在准星附近，但服务器用它当前的包围盒做距离判定时，目标已经超出有效范围，攻击就不会执行。

位置同步是服务器权威的状态复制：`EntityTrackerEntry` 根据服务端实体状态产生 S2C 包，客户端 `ClientPlayNetworkHandler` 接收后更新客户端实体的位置/插值状态。普通生物（`LivingEntity`）默认用三 tick 的 `PositionInterpolator` 平滑追踪更新。它让画面不至于逐包跳动，但并不让服务器回滚到玩家看到的状态，也不保证插值中的目标位置与服务端当前目标位置一致。

预测器的价值在于利用已知速度/轨迹估算“现在或攻击到达服务器时目标会在哪里”，减少高延迟、高速运动下的瞄准滞后。但客户端预测**不能**使服务器接受不符合服务器判定的攻击：最终是否命中仍由服务端状态与判定规则决定。若目标是对抗网络延迟而不是改善客户端瞄准，必须由服务器设计有界的历史状态回溯/延迟补偿，不能只靠客户端预测。

## 关键执行链

### 1. 客户端选目标并发送攻击包

玩家攻击时，客户端从当前 `crosshairTarget` 取目标实体，调用 `ClientPlayerInteractionManager.attackEntity`；该方法发送 `PlayerInteractEntityC2SPacket.attack`，包中使用目标实体 ID，而不是客户端报告的命中时刻或一段运动历史。若配置了 `AttackRangeComponent`，客户端会先以准星命中点做本地距离检查；这只是本地门槛，不取代服务端校验。

- `net/minecraft/client/MinecraftClient.java:1757`（攻击输入分支）、`:1760`（客户端攻击距离检查）、`:1761`（攻击命中实体）
- `net/minecraft/client/network/ClientPlayerInteractionManager.java:433-435`（构造并发送攻击包）
- `net/minecraft/network/packet/c2s/play/PlayerInteractEntityC2SPacket.java:42-43`（包由实体 ID 构造）

### 2. 服务端按接收时的状态重新校验

`ServerPlayNetworkHandler.onPlayerInteractEntity` 将包切回服务器主线程，按 ID 从服务器世界找实体，检查世界边界，然后对**此时服务器实体的 bounding box** 调用 `packet.canInteractWithEntityIn(..., 3.0)`。攻击类型最终路由至 `player.attack(entity)`。如果校验失败，`packet.handle` 和攻击动作都不会执行；这是视觉上似乎已击中但服务器不认的直接原因之一。

该距离并非简单比较“玩家到生物模型中心”的欧氏距离：攻击包分支走 `PlayerEntity.canAttackEntityIn`，最终交给 `AttackRangeComponent` 对玩家眼睛到目标包围盒的距离做平方距离换算，并结合有效最小/最大距离、hitbox margin 和传入的额外范围。客户端的预检可能用准星命中点，而服务端的包校验用包围盒；两边依据也不同。

- `net/minecraft/server/network/ServerPlayNetworkHandler.java:1758-1772`（线程、实体解析、边界与距离检查）、`:1800-1822`（攻击分支与真正执行）
- `net/minecraft/network/packet/c2s/play/PlayerInteractEntityC2SPacket.java:84-85`（攻击和普通交互选择不同范围检查）
- `net/minecraft/entity/player/PlayerEntity.java:1941-1942`（攻击距离委托）、`:1935-1939`（普通实体交互距离）
- `net/minecraft/component/type/AttackRangeComponent.java:98-109`（点/包围盒距离及有效范围）

### 3. 服务器周期性推送实体位置

`EntityTrackerEntry.tick` 保存最近一次跟踪位置 `trackedPos`。达到 `tickInterval`，或实体速度/数据追踪器有变化时，它比较当前 `getSyncedPos()` 与缓存位置：通常发相对移动/旋转包；满足重同步条件时发 `EntityPositionSyncS2CPacket` 绝对状态包。位置变化小于阈值时可不发送位置包，并存在周期性校正。速度包则在速度变化或实体类型/状态要求时发送。

因此同步并不是“生物每移动一个游戏刻，服务器就即时让所有客户端知道一次”。包的产生有追踪间隔、阈值与条件，随后还要经过网络并由客户端处理；具体间隔由构造 `EntityTrackerEntry` 时传入，不能把它误说成所有实体固定同一个值。

- `net/minecraft/server/network/EntityTrackerEntry.java:54-85`（追踪状态、间隔及初值）
- `net/minecraft/server/network/EntityTrackerEntry.java:92-104`（每次 tick 与追踪数据处理）
- `net/minecraft/server/network/EntityTrackerEntry.java:127-138`（更新门槛和载具分支）
- `net/minecraft/server/network/EntityTrackerEntry.java:156-183`（位移判断、相对包/绝对同步包选择）
- `net/minecraft/server/network/EntityTrackerEntry.java:195-208`（速度/位置包发送及缓存推进）

### 4. 客户端接收并插值

客户端的 `onEntity` 用相对位移更新本地 `TrackedPosition`，再通过 `updateTrackedPosition[AndAngles]` 更新实体；完整位置同步则由 `onEntityPositionSync` 更新跟踪位置，并在合适时调用相同追踪更新接口。`Entity.updateTrackedPositionAndAngles` 会把目标状态交给实体插值器（若存在）。`LivingEntity` 持有默认时长为三 tick 的 `PositionInterpolator`；插值器在 tick 中逐步将客户端实体坐标移向收到的状态。

这解释了为什么渲染出来的运动是连续的，却不等于实时服务器坐标：插值处理的是客户端收到的离散更新，并按数个本地 tick 平滑应用。视觉上“近”说明客户端显示/瞄准上下文中的目标接近玩家，不是服务器此刻的权威包围盒必然也在范围内。

- `net/minecraft/client/network/ClientPlayNetworkHandler.java:615-637`（绝对位置同步包处理）
- `net/minecraft/client/network/ClientPlayNetworkHandler.java:692-716`（相对位置与旋转包处理）
- `net/minecraft/entity/Entity.java:2559-2583`（追踪更新交给 `PositionInterpolator`）
- `net/minecraft/entity/LivingEntity.java:228`（生物默认插值器）
- `net/minecraft/entity/PositionInterpolator.java:11-12`、`:50-62`（默认时长和目标状态）
- `net/minecraft/entity/PositionInterpolator.java:76-103`（插值 tick 及坐标推进）

## 为什么需要预测器

从上述机制可推导出：如果只跟随最近收到的状态，客户端观察到的目标状态天然滞后于服务器当前状态；在一个延迟窗口内，目标移动距离约为“目标速度 × 有效延迟”，高速生物更容易把这段偏差放大到足以改变命中/距离判定的程度。预测器可以在客户端对目标短期位置作外推，帮助准星和攻击意图朝更接近当前的位置移动；它也可用于平滑同步间隔之间的运动。

但须区分三个机制：

1. **插值（本源码明确实现）**：在两个收到的位置样本间平滑过渡，主要改善视觉连续性。
2. **预测/外推（问题所说的 predictor）**：根据速度等状态估算尚未收到的新位置，属于对未来状态的估计。
3. **服务端回溯/延迟补偿（权威判定机制）**：服务端保存历史状态，并在受限规则下按攻击发生/到达时间重建判定状态。它能改变延迟命中判定，但本次检查的普通 `onPlayerInteractEntity` 距离链路中没有看到这种按历史目标位置回溯的步骤。

本源码存在客户端 `PositionInterpolator`，但它的职责是位置/角度插值，并未在此攻击包路径中构成服务端命中回溯。故“需要预测器”不是说 Minecraft 这条链路会用某个叫 `Predictor` 的类做命中判定，而是从客户端陈旧样本与服务端当前状态之间的时差推导出的设计需求。客户端预测可能预测错；服务器权威和反作弊约束仍决定最终结果。

## 注意事项

- 目标移动快只是可能原因。客户端与服务器实体状态不同步、攻击包到达时距离检查失败，是本文从源码确认的机制；遮挡、攻击冷却、目标存活/可攻击状态等也可能导致攻击无效，本报告不逐项展开。
- `ServerPlayNetworkHandler` 里的额外距离参数 `3.0` 是调用时传给范围判定的额外范围；不要把它直接误读成目标可离玩家三格的固定攻击距离。有效范围还受 `AttackRangeComponent` 规则影响。
- 本报告未声称完整复原跨网络的精确延迟、服务端每类实体的 `tickInterval` 配置或具体命中失败概率；这些取决于连接、追踪配置、实体状态和玩法属性。

## 一句话总结

客户端看到的是经过网络传输和插值的目标状态，服务端校验的是处理攻击包时的权威包围盒；高速移动把两者差异放大，于是会出现“眼前却打不到”。预测器可减轻客户端瞄准滞后，但除非服务端也实现历史回溯，否则它不会改变服务器的最终判定。
