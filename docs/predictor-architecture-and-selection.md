---
name: predictor-architecture-and-selection
description: 说明 SlimefunHelper 在 Minecraft 1.21.11 中使用位置预测器的原因、数据链路、PredictorImpl 实现、可用预测算法，以及针对不同目标运动状态的选择依据。
---

# 位置预测器：原理、实现与选型

本文说明当前代码中的实体位置预测器。这里的“预测”是客户端根据已经收到的服务器位置样本，估算目标在未来若干 tick 的位置。它不会改变 Minecraft 的服务器权威判定，也不会把客户端估算变成命中结果。

## 为什么需要预测器

Minecraft 的实体位置由服务器决定。客户端收到的是离散的 S2C 位置包，包到达时目标可能已经继续移动；网络延迟、服务器发送频率和客户端处理时机都会使客户端看到的目标位置落后于服务器当前状态。

普通客户端插值只能把“已收到的两个状态”之间的移动显示得平滑，不能回答“攻击包发出时目标会在哪里”。1.21.11 的 `PositionInterpolator` 默认使用 3 tick 插值周期（`minecraft-sourcecode-yarn-mapping-1.21.11/net/minecraft/entity/PositionInterpolator.java`），而 `Entity#getLerpedPos` 也只是对 `lastX/Y/Z` 与当前坐标做插值。因此，快速移动、滑翔、重锤追击和长矛瞄准仍需要未来位置估计。

项目中的攻击和移动逻辑使用预测位置来计算瞄准点、追击方向或是否可能相交，例如：

- `PositionPredict` 为普通攻击、飞行攻击和长矛攻击提供 `ticksLater`、历史窗口和预测模式。
- `ElytraBot` 在重锤、长矛和反长矛判断中读取预测位置。
- `CrystalAura` 可以选择使用预测位置计算伤害。

预测的目标是减少“根据过时位置瞄准”的误差，不是绕过服务器检查。最终移动、攻击和碰撞结果仍由服务器的实际状态决定。

## Minecraft 的同步边界

1.21.11 的客户端网络处理器维护一个实体的 `TrackedPosition`，并按包类型更新它：

- `EntitySpawnS2CPacket` 提供初始的绝对坐标。
- `EntityS2CPacket` 携带相对位移和可选旋转。`TrackedPosition.withDelta` 以 1/4096 的固定点精度还原相对位移。
- `EntityPositionS2CPacket` 携带 `EntityPosition` 和 `PositionFlag`。`EntityPosition.apply` 先合并相对坐标、旋转和速度，再得到绝对状态。
- `EntityPositionSyncS2CPacket` 直接携带同步位置、速度和旋转。

客户端网络处理器收到这些包后，会更新 `TrackedPosition`，并在合适的情况下调用 `updateTrackedPositionAndAngles`。实体的插值器随后逐 tick 靠近包中的目标位置。这个过程解决的是显示平滑和包间过渡，不会生成尚未收到的未来样本。

## 非本地玩家为什么难以预测

`PredictorImpl` 面对的是远程 `OtherClientPlayerEntity`。它拿不到目标的输入、速度计算过程或服务器上的碰撞结果，只能看到服务器选择发送到客户端的结果位置。因此，同一段位移可能来自普通行走、跳跃、滑翔、击退、液体、载具或碰撞修正，单靠当前位置不能确定下一步运动。

非本地玩家还同时受到几种不同步的影响：

- **观测时刻不明确**：样本按包到达客户端的 `Tasks.getTick()` 记录，不等于服务器产生该位置的 tick。延迟变化会让相邻样本的时间间隔变化。
- **包的表达方式不同**：服务器可能发送相对位移、绝对传送或完整同步；相对包还要经过 1/4096 固定点量化，累加时会产生微小误差。
- **客户端有第二层状态**：`TrackedPosition` 是网络同步基线，实体坐标可能正在经过 `PositionInterpolator` 插值。渲染位置平滑，并不等于服务器又提供了新的运动样本。
- **运动状态会突然切换**：落地、起跳、滑翔、撞墙、击退、传送和反作弊纠正都会让速度或方向不连续；旧历史对新状态的解释会立即失效。
- **旋转和位移不绑定**：目标可以只转身不移动，也可以被速度或碰撞推动而不改变视角。不能把 yaw/pitch 变化直接当成位移方向。

所以 `PredictorImpl` 的目标不是重建服务器完整物理，而是在短时间窗口内用最近的网络观测估算未来位置。历史窗口越长，样本越平滑但越容易混入旧运动状态；预测步数越大，延迟和模型误差都会被放大。

## PredictorImpl 的职责

`Predictor` 接口只定义位置查询、速度查询和预测入口；本文关注其中保存远程玩家网络历史的 `PredictorImpl`。`PositionPredict.getPlayerPredictor` 在玩家出生包到达时为远程玩家建立实例，实体销毁或重新进入世界时清理映射。预测器尚未建立时，调用方只能使用当前实体状态，不能假定已经存在可用历史。

## PredictorImpl 的数据链路

### 1. 建立同步基线

`initializeTrackedPosition` 保存实体 ID、初始位置、俯仰角和偏航角，并把位置写入 Minecraft 的 `TrackedPosition`。之后每个位置包都必须匹配 `ownerId`，避免不同实体的包污染同一条历史。

### 2. 记录网络观测

`PositionPredict.onPacket` 将以下包转交给对应的 `PredictorImpl`：

- `EntityS2CPacket`：使用 `currentTrackedPosition.withDelta(getDeltaX(), getDeltaY(), getDeltaZ())` 累加相对位移；若有旋转则更新角度。
- `EntityPositionS2CPacket`：用 `EntityPosition.apply` 按 `PositionFlag` 合并相对/绝对坐标和旋转。
- `EntityPositionSyncS2CPacket`：直接使用包中的位置和旋转。

更新后都以 `KnownPosition(Vec3d, Tasks.getTick())` 记录一次。相同对象的重复记录会被 `addRecord` 丢弃，队列最多保留 80 条记录。远程实体的样本来自包到达客户端的 tick，因此天然包含观察延迟。

`PlayerEntityMixin` 在玩家每个 `tick` 结束时调用当前预测器的 `tick`。对于远程玩家，`PredictorImpl` 的有效样本仍来自位置包，tick 调用主要负责维护历史队列；只有实例明确绑定本地玩家时，`PredictorImpl` 才会把客户端输入后的实际位置写入历史。正常本地玩家不使用这条远程网络预测路径。

### 3. 从稀疏包重建连续历史

`predict(ticksLater, method, useTicksBefore)` 的处理顺序如下：

1. 取最近 `useTicksBefore` 个 tick 范围内的记录，并保留窗口前的一个记录作为插值锚点。
2. 如果窗口起点没有精确样本，在第一个样本前补一个相同位置的起点。
3. 对样本之间缺失的 tick 做线性插值，生成连续的 `Vec3d[] history`。
4. 计算 `blankTicks = currentTick - lastKnownTick`，再使用 `futureSteps = blankTicks + ticksLater`。
5. 当 `futureSteps <= 0` 时直接返回历史中的对应位置；否则把连续历史交给选定算法。

因此，`ticksLater` 是相对于客户端当前评估时刻的额外预测步数；`blankTicks` 则补偿最后一个服务器样本尚未追上客户端时钟的部分。`ticksLater=0` 返回当前同步位置，不会调用数学预测器。

`PositionPredict.PredictArgument` 允许小数 tick：它分别计算 `floor(ticksLater)` 和 `floor(ticksLater)+1` 的结果，再做线性插值。历史长度和未来步数越大，模型误差越容易累积，不能把它们当成命中保证。

## 当前可用的预测算法

`PredictionMode.ordinal()` 直接作为 `PredictorImpl.predict` 的 `method` 参数，编号必须保持下面的对应关系：

| 编号 | 配置枚举 | 实现 | 原理与适用条件 |
| ---: | --- | --- | --- |
| 0 | `NO_PREDICT` | 直接返回当前同步位置 | 没有足够历史、目标基本静止，或需要避免外推风险时使用 |
| 1 | `LINEAR` | `MathUtils.linearPrediction` | 对 x/y/z 分别做线性回归，至少需要 2 个连续样本；假设短时间内速度近似恒定 |
| 2 | `QUADRATIC` | `MathUtils.quadraticPrediction` | 对 x/y/z 分别做二次回归；当前实现少于 4 个样本时回退到当前点，矩阵退化时回退为线性回归；可表达平滑加速度，但对噪声和长外推敏感 |
| 3 | `PREDICTOR_NV` | `MathUtils.NVPredictor` | 计算连续位置差，并用递推折半累积得到偏向最近样本的速度，再按未来步数外推；对包间隔和短时速度变化较稳健 |
| 4 | `PREDICTOR_ROTATION` | `MathUtils.RotationalPredictor` | 把水平位移拆成长度和方位角，限制单次转向不超过 60 度，分别预测水平长度、垂直位移和平均转角；适合方向持续改变的飞行目标，当前标记为实验功能 |
| 5 | `PREDICTOR_ACCELERATE` | `MathUtils.AcceleratePredictor2` | 由位移差得到速度，再由速度差得到加速度；每个未来 tick 更新速度并累加位置；适合方向稳定且加速度明显的运动，当前标记为实验功能 |
| 6 | `PREDICTOR_FLY_ACC` | `MathUtils.AcceleratePredictor` | 在加速度外推前尝试按方向夹角和 Y 方向变化裁剪历史，再进行速度/加速度累加；代码仍处于实验状态，方向比较使用历史位置参与判断，不能视为通用物理模型 |

线性和二次回归使用相对时间轴 `-(n-1)..0`，最后一个样本对应时间 0，预测值在正的未来步数处求值。NV 和两种加速度预测器则直接以最新位置为起点累加位移。旋转预测器只生成一个未来位移向量，因此长 horizon 时误差会快速累积。

## 针对目标运动状态的选择理由

这里的“目标”指被瞄准或追踪的实体。选择应基于目标运动和可用历史，而不是仅看某一次欧氏距离误差。

| 目标状态 | 建议 | 原因 |
| --- | --- | --- |
| 静止、刚生成、历史不足 | `NO_PREDICT` | 速度和加速度没有可靠估计，外推只会引入噪声 |
| 地面直线移动、攻击只需补偿一两个 tick | `LINEAR`，历史约 2 | 恒速假设简单，方差小；适合把最近同步点补到当前攻击时刻 |
| 包间隔明显、目标短时间内变速，或普通滑翔/长矛瞄准 | `PREDICTOR_NV`，短到中等历史 | 最近位移权重更高，比长窗口回归更快响应方向和速度变化；当前飞行和长矛配置默认使用它 |
| 轨迹平滑且确实存在加速度，历史至少 4 点 | `QUADRATIC` | 二次项能表达弯曲轨迹，但必须限制 horizon 并检查异常值 |
| 水平转向连续、速度变化相对平稳的飞行目标 | `PREDICTOR_ROTATION` | 把转向和速度分开建模；遇到急转、停顿或地面移动时不应默认使用 |
| 方向稳定且速度逐 tick 改变的飞行目标 | `PREDICTOR_ACCELERATE` 或 `PREDICTOR_FLY_ACC` | 加速度模型能提前量化变速；两者都是实验实现，需要残差、速度和加速度上限保护 |

当前配置的默认值反映了这个取舍：`attackPredictArgument` 默认不预测，`flyPredictArgument` 和 `spearPredictArgument` 默认使用 `PREDICTOR_NV`，未来步数为 2、历史窗口为 5。它们是稳健起点，不代表所有服务器延迟、距离和飞行轨迹都适用同一组参数。

已有的长矛客户端/服务器实验也支持“短窗口、短 horizon”的原则：在固定实验模型中，非退化的当前时刻补偿使用 `LINEAR/history=2/ticksLater=1`，一 tick 额外提前量使用 `NV/history=3/ticksLater=2`。实验中的 `NV/history=1` 虽然命中率最高，但本质是重复最新点形成的零速度基线，不应当作为运动目标的证据或默认策略。详细几何和实验限制见 `docs/predictor-spear-algorithms.md`。

## 使用时的边界

- 预测位置是实体底部中心附近的坐标；攻击时还需要根据实体包围盒选择瞄准高度，并进行实际射线/碰撞检查。
- `PredictorImpl` 只保存最多 80 条位置记录，历史窗口过长不会增加真实信息，反而会把已经改变的运动状态混入模型。
- 服务器瞬移、急停、碰撞反弹、转向突变和丢包都会破坏恒速或平滑加速度假设。预测结果应配合速度、加速度和一步残差阈值使用。
- `getCurrentSyncedPos` 与 `getCurrentPos` 在 `PredictorImpl` 中都返回 `TrackedPosition` 的当前值；预测结果必须通过 `predict` 获取，不能把同步位置当成未来位置。
- 预测器只处理位置历史，不负责方块遮挡、攻击距离、冷却、旋转发送时序或服务端回溯。这些因素仍需由调用模块单独判断。

## 相关源码

- `src/main/java/me/matl114/hacks/utils/entity/Predictor.java`
- `src/main/java/me/matl114/hacks/utils/entity/PredictorImpl.java`
- `src/main/java/me/matl114/utils/MathUtils.java`
- `src/main/java/me/matl114/hacks/modules/combat/PositionPredict.java`
- `minecraft-sourcecode-yarn-mapping-1.21.11/net/minecraft/entity/TrackedPosition.java`
- `minecraft-sourcecode-yarn-mapping-1.21.11/net/minecraft/entity/PositionInterpolator.java`
- `minecraft-sourcecode-yarn-mapping-1.21.11/net/minecraft/client/network/ClientPlayNetworkHandler.java`
