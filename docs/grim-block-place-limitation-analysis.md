---
title: grim-block-place-limitation
description: 分析 Grim 对连续放置方块的限制机制，包括 BlockPlace、MultiPlace、AirLiquidPlace、handleQueuedPlaces、CompensatedWorld 写入时序以及 queue/post 行为。
---
# Grim 如何限制玩家放置方块

## 问题与结论

本文仅分析 `common/` 中的 `BlockPlace`、`MultiPlace`、`AirLiquidPlace` 和 `CheckManagerListener.handleQueuedPlaces`。

结论：这些逻辑没有实现通用的“每秒最多放置 N 个方块”速率上限。不过，即时放置检查可能先于先前快照的队列模拟执行，因此 `CompensatedWorld` 暂时看不到刚放置的方块；连续放置会受到这个状态可见性窗口影响（详见文末补充）。`MultiPlace` 检查同一客户端 tick 片段内连续放置的面、光标坐标或目标位置是否变化；`AirLiquidPlace` 检查放置位置是否为空气或不可作为支撑的液体。违规经过阈值和权限等门控后可能导致取消与重同步。`handleQueuedPlaces` 是延迟处理放置/使用物品包的队列及状态恢复流程，不是放置计数器。

`BlockPlace` 是单次放置的上下文对象，`BlockPlaceCheck` 是检查基类；两者都不是次数限制器。

## 行为链路

```text
PLAYER_BLOCK_PLACEMENT
  -> CheckManagerListener.onPacketReceive
  -> 构造 BlockPlace
  -> CheckManager.onBlockPlace
       -> MultiPlace / AirLiquidPlace / 其他 BlockPlaceListener
  -> 被取消：取消包并确认 sequence 或重同步方块/物品
  -> 通过：保存 BlockPlaceSnapshot
  -> 后续 handleQueuedPlaces 按相应位置/姿态处理快照
```

反空气放置检查在收到放置包时运行；通过检查的放置快照之后进入队列，用于交互模拟及补偿世界更新。这是两个阶段。

## BlockPlace 与取消

`utils/anticheat/update/BlockPlace.java` 保存目标/点击位置、手、面及 faceId、物品、材质、命中数据、光标、协议 `sequence`、`replaceClicked` 等上下文。方块状态来自 `player.compensatedWorld`。

`BlockPlace.resync()` 只设置对象的 `isCancelled`。监听器随后负责取消包，并按版本发送 1.19+ 的 `AcknowledgeBlockChanges(sequence)`，或对旧客户端重同步目标及相邻位置。这里 `sequence` 用于结束客户端预测，不是 `MultiPlace` 的次数值。

`checks/type/BlockPlaceCheck.java` 提供公共 `cancelVL`、`shouldCancel()` 和碰撞盒辅助逻辑。取消条件为 `cancelVL >= 0 && violations >= cancelVL`；基类默认阈值是 5，子类可覆盖。

## MultiPlace：同 tick 多目标检测

`checks/impl/scaffolding/MultiPlace.java` 每次放置记录 `face`、`cursor` 和 `position`。第一次只建立基准；当 `hasPlaced` 已为真，且之后某次放置这三项中至少一项不同，才触发 flag 条件。相同三元组不会因次数本身触发该条件，因此它不是严格的“一个 tick 只准一个包”计数器。

`onPacketReceive` 在视角实体不是玩家自身，或 `isTickPacket(packetType)` 时重置 `hasPlaced`。`isTickPacket` 定义于 `checks/GrimProcessor.java`：按飞行/移动包识别 tick，排除 teleport 与 1.17 duplicate 等情况；新客户端 tick-end 也有特殊处理。因此这是协议/客户端 tick 边界逻辑，不是固定墙钟秒数窗口。

该 check 的 `@CheckData` 标注 `experimental = true`。`Check.recordFlag` 会在玩家未启用实验性检查时抑制违规记录。

### 跳 tick 处理

若 `player.canSkipTicks()` 为假，候选行为会立即 flag；若为真，证据先进入内部 `flags` 列表。在 `onPredictionComplete` 中，仅当 `player.isTickingReliablyFor(3)` 成立时逐条 flag，之后清空列表。该延迟路径仅提交违规记录，不调用普通分支的 `place.resync()`，所以不能推断跳 tick 时会即时取消那次放置。

### 违规到取消

普通分支需要依次满足：`flag(verbose)` 成功、`shouldModifyPackets()` 为真、`shouldCancel()` 为真，才调用 `place.resync()`。`MultiPlace` 没有覆盖基类默认取消阈值 5。`shouldModifyPackets()` 还受检查启用状态、全局 Grim 状态和 exemption / no-modify-packet 权限门控。对象被标记后，数据包监听器再处理 event cancel 和客户端重同步。

## AirLiquidPlace：无效支撑面检测

`checks/impl/scaffolding/AirLiquidPlace.java` 首先排除创造模式，再读取 `place.position` 在补偿世界中的方块类型。为避免把合法破坏-放置序列误判，它遍历 `player.blockHistory` 中同位置、当前 tick 差小于 2、原因是 `START_DIGGING` 或 `HANDLE_NETTY_SYNC_TRANSACTION` 的修改；若旧方块既非空气，也非 `Materials.isNoPlaceLiquid(...)`，则直接返回。

否则，若补偿世界中该位置是空气或不可放置液体就 flag。此 check 覆盖 `getDefaultCancelVL()` 返回 0，`common/src/main/resources/config/en.yml` 也设置 `AirLiquidPlace.cancelvl: 0`。成功 flag 后，在 `shouldModifyPackets()` 和 `shouldCancel()` 成立时调用 `place.resync()`。实际 event cancel / 重同步由 listener 完成。

它检查合法支撑状态，而非频率。源码注释提到快速破坏、放置短草、延迟和世界状态不同步可能引入误报；近期方块历史过滤是缓解逻辑之一。

## handleQueuedPlaces：排队与时序补偿

玩家队列 `GrimPlayer.placeUseItemPackets` 保存 `BlockPlaceSnapshot`，其中包括原始 packet wrapper 与收到时的 sneaking 状态。`CheckManagerListener.handleQueuedPlaces(...)` 循环排空队列，对每个快照：

1. 暂存玩家当前位置，并临时切换到 `lastClaimedPosition`。
2. 恢复快照中的 sneaking；骑乘时按载具偏移修正临时位置。
3. 若距最近一次放置/使用不足 15ms，或客户端早于 1.9，并且本次有 look，则临时采用当前 yaw/pitch。
4. 调用 `compensatedWorld.startPredicting()`，执行 `handleBlockPlaceOrUseItem(...)`，再调用 `stopPredicting(wrapper)`。
5. 恢复原坐标与 sneaking 状态。

调用点包括移动包处理（在更新新声明位置之前）、事务确认、快捷栏切换，以及 `PacketPlayerDigging` 的槽位变更路径。其目的是按对应位置、姿态、物品和补偿世界顺序处理交互，不是限制队列长度或放置频率。

普通 `PLAYER_BLOCK_PLACEMENT` 在收到时先调用 `onBlockPlace`。通过检查后才把 packet/sneaking 快照排入队列；取消的包不会进入该分支。`USE_ITEM` 和旧版兼容路径也可能排队，因为这些交互会触发桶、睡莲等模拟。

## 是否存在硬性放置次数上限？

在本文所分析的几个组件中，没有按“每秒/每 tick N 次”工作的通用计数器：

- `MultiPlace` 比较同 tick 连续放置的面、光标和位置差异，不维护整数放置上限。
- `AirLiquidPlace` 校验支撑方块，并参考最近方块历史，不统计频率。
- `handleQueuedPlaces` 逐个排空并模拟快照，没有队列长度或速率阈值。

该结论仅限这些实现，不代表 `common/` 中其他 packet、crash 或 multi-action 检查没有其他限制。要回答全局每秒放置数量，需另行分析其他检查及服务端处理链。

## 关键源码

- `common/src/main/java/ac/grim/grimac/events/packets/CheckManagerListener.java`：包检查、队列写入、`handleQueuedPlaces` 与后续处理。
- `common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/MultiPlace.java`：同 tick 多目标判定与跳 tick flag。
- `common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/AirLiquidPlace.java`：无效支撑面判定与历史例外。
- `common/src/main/java/ac/grim/grimac/checks/type/BlockPlaceCheck.java`：取消阈值与公共检查逻辑。
- `common/src/main/java/ac/grim/grimac/checks/Check.java`：flag、违规累计、实验性检查和包修改门控。
- `common/src/main/java/ac/grim/grimac/checks/GrimProcessor.java`：`isTickPacket` 定义。
- `common/src/main/java/ac/grim/grimac/manager/CheckManager.java`：构造处理器和分发放置回调。
- `common/src/main/java/ac/grim/grimac/utils/anticheat/update/BlockPlace.java`：单次放置上下文与 `resync()` 标志。
- `common/src/main/java/ac/grim/grimac/utils/data/BlockPlaceSnapshot.java`、`common/src/main/java/ac/grim/grimac/player/GrimPlayer.java`：放置/使用物品快照队列。
- `common/src/main/resources/config/en.yml`：`AirLiquidPlace.cancelvl` 默认值。

## 证据范围与未确认项

- 结论来自当前 `common/` 静态源码；未运行服务器，也未构造客户端包流实验。
- “无全局速率限制”仅限本页提及的三个实现，不是对 Grim 所有检查、平台或服务器网络层的整体断言。
- `MultiPlace` 的跳 tick 路径提交违规但自身不即时 `resync()`；实际 flag 还依赖实验性检查状态、tick 可靠性、检查配置/权限和事件结果等运行条件。

## 补充：队列造成的补偿世界滞后窗口

收包时的 `onBlockPlace` 检查与 `handleQueuedPlaces` 的世界模拟并非同一时刻。一个有效放置包通过即时检查后，只把 `BlockPlaceSnapshot` 加入 `placeUseItemPackets`；此处尚未调用 `handleBlockPlaceOrUseItem`，因此尚未通过 `BlockPlaceResult` / `BlockPlace.set(...)` 将该放置模拟写入 `CompensatedWorld`。

若下一个放置包在队列排空前到达，它的即时检查（包括 `AirLiquidPlace` 对 `place.position` 的支撑方块查询）仍可能读到旧的补偿世界状态，看不到前一个刚放置的方块。于是玩家尝试以新方块为支撑继续放置时，可能被判定为无效支撑并取消；这与 `MultiPlace` 是否通过或是否启用是独立的，因为触发点是检测读状态早于前一个包的延迟模拟。

当 `handleQueuedPlaces` 开始排空队列，它按 FIFO 对快照逐个执行 `startPredicting -> handleBlockPlaceOrUseItem -> stopPredicting`。前一快照的放置模拟会在 `BlockPlace.set(...)` 中同步调用 `CompensatedWorld.updateBlock(...)`，所以同一次排空中后续快照可以看到前一项的预测状态。需要注意，这解释的是“队列排空后的模拟顺序”；它并不追溯重跑已在收包时完成的 `AirLiquidPlace` 检查。

```text
放置 A 收包：检查 A -> 入队 A（CompensatedWorld 尚未模拟 A）
放置 B 收包：检查 B 读到旧世界 -> 可能判无效
队列排空：模拟 A -> updateBlock(A) -> 再模拟队列中的后续交互
```

所以更准确的结论是：`handleQueuedPlaces` 不设置放置速率上限，但“直到队列被处理才模拟客户端放置并更新补偿世界”的时序会形成连续放置的可见性窗口。若某次队列排空先于 B 的收包，B 的即时检查是否能看到 A，则取决于调用时序。`MultiPlace` 只影响其自身的同 tick 参数差异判定，不会提前提交 A 的补偿世界状态。

**源码直接显示**：检查先于快照入队；队列排空时才调用放置模拟；`BlockPlace.set(...)` 调用 `CompensatedWorld.updateBlock(...)`；处于预测阶段时 `updateBlock` 立即写入 chunk 缓存并记录预测变更。

**由调用顺序推断**：如果 B 在 A 的模拟/写入前进入即时检查，B 会看到旧补偿状态。是否在具体运行环境触发，取决于移动包、确认事务等触发 `handleQueuedPlaces` 的相对时序。

**未验证**：尚未通过运行时包序实验确定所有协议版本及所有触发条件下的窗口长度；本文也未修改或建议修改运行时代码。

