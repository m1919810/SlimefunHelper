---
name: grim-elytra-movement-simulation
description: Grim 中 Elytra 预测、烟花容错、offset 判定与 setback 回滚的结果逻辑。用于直接回答滑翔检测、fireworksBox、1.7 来源与拉回后高速原因。
disable-model-invocation: true
---

# Grim Elytra Movement Simulation

## 结论范围
- 只解释 Grim 当前实现里的 Elytra / Firework / offset / setback 链路。
- 默认输出模块关系、状态流、容错层级和因果链。
- 不把 `1.7` 解释成“全局鞘翅速度上限”。
- 不把 `fireworksBox` 解释成“直接修改某个固定 movementThreshold 常量”。

## 一句话模型
Grim 对 Elytra 的处理不是“看到滑翔就单独硬判”，而是把玩家放进 Elytra 预测分支，生成一组允许的运动结果，再把客户端实际位移和允许结果之间的最小距离变成 offset，最后再由 offset / advantage / setback 链决定是否 flag 或拉回。

## 核心模块关系
- 起飞入口：`PacketEntityAction`
- 移动主入口：`MovementCheckRunner`
- 状态分发：`MovementTicker`
- Elytra 物理：`PredictionEngineElytra`
- 通用不确定性与候选空间：`PredictionEngine` + `UncertaintyHandler` + `PointThreeEstimator`
- 烟花记录：`CompensatedFireworks`
- 偏移处罚：`OffsetHandler`
- 回滚与速度重建：`SetbackTeleportUtil`

## 结果逻辑

### 1. Elytra 是怎样进入检测链的
- 客户端发送 `START_FLYING_WITH_ELYTRA`。
- `PacketEntityAction` 先做起飞合法性门槛：
    - 地面或近地起飞会直接 resync。
    - 低版本起飞状态更偏服务端控制。
    - 没有合法 Elytra 条件时会被当成 ghost 状态并 resync。
- 一旦 `player.isGliding` 成立，后续移动包不会走普通地面预测，而是进入 Elytra 预测分支。
- `MovementCheckRunner` 接收位置更新，整理实际位移与当前状态。
- `MovementTicker` 在状态分支里识别 `player.isGliding`，交给 `PredictionEngineElytra`。
- 预测引擎生成允许运动结果，再和客户端实际位移比对，得到本 tick 的 offset 基础量。
- 最终是否违规，不由 Elytra 分支单独拍板，而是统一交给 `OffsetHandler` / setback 链。

### 2. Elytra 本体运动是怎么模拟的
Elytra 分支的核心不是 WASD 推进，而是“当前速度 + 视角 + 重力”的连续演化。

它的主因果链是：
- 先取 `look vector`。
- 再根据 `pitch` 计算俯仰带来的垂直/前向耦合。
- 叠加重力。
- 下落时把一部分下落量转成前推。
- 俯冲时再额外加速。
- 横向速度会向当前朝向对齐。
- 最后乘 `0.99 / 0.98 / 0.99` 阻力。

直接结论：
- Elytra 不是普通输入模型，`PredictionEngineElytra` 里输入被视为零输入。
- 玩家“能不能这样飞”，主要看视角、已有速度、重力、阻力这条链能不能生成接近实际位移的候选结果。

#### 2.1 下一 tick 继续使用的是哪一层速度
Grim 明确把两层速度拆开处理：
- `clientVelocity` = 碰撞前速度，会被带入下一 tick。
- `predictedVelocity` = 碰撞后速度，只用于当前 tick 预测结果，不直接作为下一 tick 延续速度。

这意味着 Elytra 的速度承接不是“每 tick 从头算一个全新速度”，而是：
- 先从上一 tick 延续下来的 `clientVelocity` 出发；
- 再套本 tick 的 Elytra 演化；
- 再得到新的候选结果。

所以只要玩家还处于合法滑翔链路里，Elytra 动量就是连续承接的，不是每 tick 清零重建。

#### 2.2 Elytra 起步不是从 0 动量开始
`START_FLYING_WITH_ELYTRA` 这条包只负责切换到 gliding 状态，不负责把速度清零。

实际结果是：
- 合法起飞必须发生在离地状态；
- 玩家进入滑翔前已经存在的下落速度、横向速度、击退速度、前一状态残留速度，都可以成为 Elytra 第一个预测 tick 的起始速度；
- 然后这份已有速度再进入 `PredictionEngineElytra` 的公式继续演化。

所以 Elytra 的起步更接近“把当前空中动量接管进滑翔模型”，不是“切状态后从 0 速重新开始”。

### 3. Grim 对烟花的处理不是精确重放，而是扩大允许空间
`CompensatedFireworks` 只做一件事：记录“当前最多可能还有多少枚烟花在作用”。

然后 `UncertaintyHandler.tickFireworksBox()` 在存在烟花作用时构造一个 `fireworksBox`：
- 输入源不是烟花实体的精确推力轨迹。
- 输入源是当前 tick 的 `look vector` 和上一 tick 的 `look vector`。
- Grim 取这两个朝向的最小/最大投影，再加一个防 tick skip 的 `antiTickSkipping` 缓冲。
- 然后整体乘 `1.7`。
- 最后对每个轴分别 clamp 到 `[-1.7, 1.7]`，形成一个三维 AABB。

这说明 Grim 这里建模的不是“这一枚烟花精确给了你多少推力”，而是：
- “在有烟花作用时，玩家朝当前和上一 tick 朝向附近，最多可能额外获得一块怎样的速度变化空间”。

这也是为什么烟花存在时，玩家会表现出更强的动量可控性：
- Grim 允许的不是一个单点速度；
- Grim 允许的是一整块朝向相关的候选空间。

### 4. `fireworksBox` 如何影响移动阈值
这里必须分成两层看，不能混成一个概念。

#### 第一层：固定基础阈值
Grim 里有固定的基础移动阈值：
- `GrimPlayer.getMovementThreshold()`
    - `1.18.2` 以下的 point-three 客户端：`0.03`
    - 更新版本：`0.0002`

这层阈值的用途是：
- 处理 `0.03` / point-three 粒度问题。
- 参与 `getOffsetHorizontal()` / `getVerticalOffset()` 里的基础容错。
- 作为“最小精度误差”和“跳 tick 误差”的基础尺子。

另外还有处罚层阈值：
- `OffsetHandler.threshold` 默认 `0.001`
- `OffsetHandler.immediateSetbackThreshold` 默认 `0.1`

这两个阈值控制的是“offset 多大开始 flag / 立即 setback”。

结论：
- `fireworksBox` 不直接修改 `getMovementThreshold()` 返回值。
- `fireworksBox` 也不直接改 `OffsetHandler.threshold` 或 `immediateSetbackThreshold`。
- 所以它不是“把某个固定阈值常量改大”。

#### 第二层：有效阈值 / 有效容错空间
`fireworksBox` 真正影响的是预测空间，也就是“实际移动会被拿去和多大一块允许区域比较”。

链路是这样的：
1. `PredictionEngine.handleStartingVelocityUncertainty()` 先构造基础不确定性盒  
   这里已经会把普通的垂直误差、流体、bubble、sneak hidden velocity、point-three、碰撞等因素加进去。

2. 如果 `fireworksBox != null`  
   Grim 不会把它当成最终答案，而是先计算：
    - `fireworksBox` 相对原始起始速度 `originalVec` 的最小/最大差值
    - 再把这些差值并入当前不确定性盒的 `min/max`

3. 盒子被扩张后，Grim 用 `cutBoxToVector()`  
   从这块更大的允许空间里，裁出一个“最接近实际移动”的允许向量。

4. 后续预测、碰撞和误差比较  
   都是在这个被扩大的候选空间基础上继续做。

最终结果不是“阈值常量变了”，而是：
- 允许空间更大了；
- 实际移动到允许空间的最短距离更小了；
- 因此算出来的 offset 更小；
- 更难超过 `OffsetHandler` 的 flag / setback 阈值。

可以把这两层理解成：
- 基础阈值 = 尺子的刻度
- `fireworksBox` = 把允许落点区域画大

尺子没变，但因为允许区域变大了，实际点到允许区域边界的距离更短，所以“看起来像阈值变宽了”。

#### 第三层：它为什么不是均匀放宽，而是定向放宽
`fireworksBox` 不是一个“给全部方向统一 +N 容错”的球形半径，而是一个按朝向生成的三维盒。

这意味着它的放宽有三个特征：
- 是方向相关的  
  朝向附近的推进更容易被解释。
- 是三轴分开的  
  `X/Y/Z` 的放宽量可以不同。
- 是跨两帧朝向的  
  当前朝向和上一 tick 朝向都会参与，因此快速转头时允许空间会沿两帧朝向一起张开。

所以它影响的不是一个简单的标量“速度阈值”，而是一个“朝向相关、三轴不对称、跨两帧的允许移动空间”。

#### 第四层：它和垂直控制、point-three、裁剪逻辑是叠加关系
`fireworksBox` 不是单独生效，它会和下面这些层一起叠加：
- `PointThreeEstimator` 的 `0.03` / skip tick 处理
- 垂直重力和流体误差
- `controlsVerticalMovement()` 相关的垂直可控性判断
- collisions 之后的裁剪
- `cutBoxToVector()` 把目标向量压回允许盒
- `reduceOffset()` 对最终偏移再做一次保守削减

所以更准确的说法不是：
- “烟花把 movementThreshold 从 A 改成 B”

而是：
- “烟花通过 `fireworksBox` 扩大了预测可接受的起始速度/位移空间，进而缩小了最终 offset，让同样的实际移动更不容易跨过后置的 flag/setback 阈值”。

#### 第五层：当前实现里，烟花数量不是线性放大盒体尺寸
当前代码会读取 `getMaxFireworksAppliedPossible()`，但 `tickFireworksBox()` 这段实现里，盒体尺寸本身并没有继续按烟花数量线性放大。

当前效果更接近：
- 只要存在烟花作用，就启用一套烟花允许空间；
- 这套空间的方向由当前/上一 tick 的视角决定；
- 不是“烟花越多，盒子按数量倍增”这一类实现。

这也是为什么这里更像“是否存在烟花推进的不确定性容错”，而不是“精确累计每枚烟花的推进量”。

### 5. `1.7` 的真正含义
`1.7` 出现在 `UncertaintyHandler.tickFireworksBox()`，不在 Elytra 本体物理公式里。

它的语义是：
- 烟花允许影响的 movement impact 上界；
- 用来构造 `fireworksBox` 的轴向边界；
- 不是普通 Elytra 自身速度上限。

更准确地说：
- Grim 先用当前/上一 tick 的朝向生成方向投影；
- 再把投影乘 `1.7`；
- 再对每个轴 clamp 到 `[-1.7, 1.7]`；
- 最终得到的是“烟花可能给当前位置速度空间带来的轴向包络”。

所以 `1.7` 限制的是：
- 烟花容错盒每个轴的最大扩张幅度；
  不是：
- 玩家滑翔总速度上限；
- Elytra 本体最大飞行速度；
- 最终 offset 阈值。

### 6. offset / setback 是怎样接在 Elytra 预测后面的
Elytra 分支生成候选结果后，Grim 会把“实际位移”和“最佳允许结果”的差距当成 offset 基础量，再进入统一处罚链。

#### 6.1 用 `v1 / v2 / v3 / box` 写成完整公式
先统一变量：
- `v1` = 当前 tick 开始时，Grim 手里延续下来的客户端动量基线
- `v2` = 玩家这个 tick 实际发送的移动量，也就是实际位移
- `v3` = 对 `v1` 套完 Elytra 本体递推后得到的主模拟结果
- `box` = 烟花容错盒 `fireworksBox`

注意两点：
- `v3` 不是最终比较值，它只是 Elytra 本体递推产物。
- 真正和 `v2` 比较的是“裁进允许空间、再过碰撞修正之后”的最终候选结果。

#### 6.2 Elytra 本体递推
设当前视角单位向量为 `L = (lx, ly, lz)`，其水平长度为：

\[
h = \sqrt{lx^2 + lz^2}
\]

设 `v1` 的水平长度为：

\[
s = \sqrt{v1_x^2 + v1_z^2}
\]

设俯仰角为 `pitch`，重力项为 `g'`，其中：
- 正常情况 `g' = gravity`
- Slow Falling 生效且当前竖直速度不大于 0 时，`g'` 会被压到更小值

再定义：

\[
c = \cos^2(pitch) \cdot \min(1, \|L\| / 0.4)
\]

那么 Elytra 本体递推可以写成下面 4 段。

第一段，先显式加入重力：

\[
u_1 = v1 + (0,\ g'(-1 + 0.75c),\ 0)
\]

第二段，如果正在下落，就把一部分下落量转成沿朝向的推进：

\[
\text{若 } u_{1y} < 0 \text{ 且 } h > 0,
\quad d_f = -0.1 \cdot u_{1y} \cdot c
\]

\[
u_2 = u_1 + \left(\frac{lx}{h}d_f,\ d_f,\ \frac{lz}{h}d_f\right)
\]

否则 `u2 = u1`。

第三段，如果抬头角度允许俯冲推进，就再加一次朝向耦合：

\[
\text{若 } pitch < 0 \text{ 且 } h > 0,
\quad d_p = s \cdot (-\sin(pitch)) \cdot 0.04
\]

\[
u_3 = u_2 + \left(-\frac{lx}{h}d_p,\ 3.2d_p,\ -\frac{lz}{h}d_p\right)
\]

否则 `u3 = u2`。

第四段，把水平速度往当前朝向对齐：

\[
\text{若 } h > 0,
\quad u_4 = u_3 + \left(\left(\frac{lx}{h}s - u_{3x}\right)0.1,\ 0,\ \left(\frac{lz}{h}s - u_{3z}\right)0.1\right)
\]

否则 `u4 = u3`。

最后乘 Elytra 阻力：

\[
v3 = (0.99u_{4x},\ 0.98u_{4y},\ 0.99u_{4z})
\]

这一步已经说明：
- Elytra 本体递推显式包含重力；
- `v3` 不是无重力模型；
- 烟花不是替代这条递推，而是在这条递推之后扩张允许空间。

#### 6.3 烟花盒 `box` 的计算
设当前 tick 视角为 `L_now`，上一 tick 视角为 `L_prev`。

设：
- 如果是 point-three 客户端，则 `a = 0`
- 否则 `a = 0.05`

对每个轴 `i ∈ {x, y, z}`，烟花盒边界是：

\[
box_{min,i} = \max\left(-1.7,\ 1.7\bigl(\min(-a, L_{now,i}) + \min(-a, L_{prev,i})\bigr)\right)
\]

\[
box_{max,i} = \min\left(1.7,\ 1.7\bigl(\max(a, L_{now,i}) + \max(a, L_{prev,i})\bigr)\right)
\]

所以 `box` 本质是一个三维 AABB，不是球，不是欧氏半径阈值。

#### 6.4 `box` 怎么并到 `v3`
Grim 不是直接拿 `box` 和 `v2` 比较，而是先围绕 `v3` 造一个基础允许盒，再用 `box` 去扩张这块允许盒。

先把普通误差层记成：
- `Δ-` = 基础负向误差
- `Δ+` = 基础正向误差

这里面已经包含 point-three、流体、bubble、活塞、碰撞、潜行隐藏速度等普通不确定性。

于是围绕 `v3` 的基础允许盒是：

\[
U_0 = [v3 + \Delta^-,\ v3 + \Delta^+]
\]

然后烟花盒不是直接加在 `v3` 上，而是先相对 `v1` 取差值。

对每个轴 `i`：

\[
e^-_i = \min(0,\ box_{min,i} - v1_i)
\]

\[
e^+_i = \max(0,\ box_{max,i} - v1_i)
\]

于是扩张后的允许盒是：

\[
U = [U_{0,min} + e^-,\ U_{0,max} + e^+]
\]

这就是为什么更准确的理解不是“把阈值直接改大”，而是：
- 先有 `v3`；
- 再围绕 `v3` 生成允许盒；
- 再用 `box` 把允许盒按轴扩张。

#### 6.5 真正和 `v2` 比较的不是 `v3`，而是裁剪后的候选结果
Grim 接下来会把 `v2` 按轴裁回允许盒 `U` 内，得到最接近 `v2` 的合法候选：

\[
c = clamp_{box}(v2, U)
\]

这里的 `clamp_box` 就是三轴分别裁剪，不是球形距离。

然后这份候选还要经过碰撞修正，得到最终可比较结果：

\[
p = collide(c)
\]

所以真正和 `v2` 比较的是 `p`，不是裸 `v3`，也不是裸 `box`。

#### 6.6 多候选分支里怎么选最优解释
实际运行时 Grim 不只试一条链，而是会试很多候选分支。

对每一条候选分支 `k`，都会得到：

\[
p_k = collide(clamp_{box}(v2, U_k))
\]

然后按平方距离选最接近 `v2` 的那一条：

\[
k^* = \arg\min_k \|p_k - v2\|^2
\]

最终留下：

\[
p^* = p_{k^*}
\]

这就是本 tick 被 Grim 采纳的“最佳解释”。

#### 6.7 `offset` 怎么生成
进入处罚链之前，先把最终距离变成 offset：

\[
offset_{raw} = \|p^* - v2\|
\]

然后再过一次保守削减层：

\[
offset = reduceOffset(offset_{raw})
\]

可以把 `reduceOffset` 理解成：

\[
offset = \max(0,\ offset_{raw} - \lambda_{boat} - \lambda_{glitch} - \lambda_{stuck} - \lambda_{bounce} - \lambda_{boost})
\]

其中这些 `\lambda` 只在对应异常场景成立时才扣减，否则就是 0。

所以最终传给 `PredictionComplete.offset` 的，不是平方距离，而是：
- 先取欧氏距离
- 再过 `reduceOffset`
- 得到最终 `offset`

#### 6.8 最终怎样判成违法
进入 `OffsetHandler` 后，判定链可以写成：

如果：

\[
offset \ge threshold \quad \text{或} \quad offset \ge immediateSetbackThreshold
\]

则：

\[
advantage := advantage + offset
\]

并进入 flag 链。

之后如果满足：

\[
(advantage \ge maxAdvantage \ \text{或} \ offset \ge immediateSetbackThreshold)
\]

并且：

\[
violations \ge setbackViolationThreshold
\]

并且玩家没有豁免权限，那么触发 setback。

如果本 tick 没过阈值，则：

\[
advantage := advantage \cdot setbackDecayMultiplier
\]

所以把整条链压成一句话，就是：

\[
v1 \xrightarrow{Elytra递推} v3 \xrightarrow{box扩张允许空间} U \xrightarrow{按轴裁剪} c \xrightarrow{碰撞修正} p^* \xrightarrow{与v2比较} offset \xrightarrow{OffsetHandler} flag/setback
\]

#### 6.9 下一 tick 继承的不是 `v2`
当前 tick 结束后，Grim 会同时保留两层结果：
- 下一 tick 延续用的是碰撞前最佳候选
- 当前 tick 对外判定用的是碰撞后最佳候选

用式子写就是：

\[
v1_{next} = c^*
\]

而不是：

\[
v1_{next} = v2
\]

也不是：

\[
v1_{next} = p^*
\]

所以“这一 tick 没被立刻拉回”不等于“下一 tick 会直接继承玩家真实发送的 `v2`”。

处罚链的主逻辑：
- `OffsetHandler` 读取 offset。
- 超过 `threshold` 或 `immediateSetbackThreshold` 时，开始累计 `advantageGained`。
- 优势累计到 `maxAdvantage`，或者单次 offset 超过 `immediateSetbackThreshold`，并且 violation 次数达到要求时，会触发 `executeViolationSetback()`。
- 没继续超阈时，累计优势按 `setbackDecayMultiplier` 衰减。

结论：
- Elytra 本身负责“能不能被预测解释”。
- `OffsetHandler` 负责“解释不了的程度是否足够处罚”。

### 7. 为什么被 setback 后 Elytra 会突然很快
根因不在 `OffsetHandler`，而在 `SetbackTeleportUtil` 的安全点速度重建链。

当前实现的因果链是：
1. 预测完成后，`lastKnownGoodPosition` 会保存一个安全位置，以及这个安全点对应的 `vector`。
2. 触发 `executeViolationSetback()` 后，`blockMovementsUntilResync()` 会从这个安全点拿回 `clientVel`。
3. 如果这次 setback 选择 `simulateNextTickPosition=true`，Grim 会先做一次碰撞，再调用 `simulateFriction(clientVel)`。
4. 在 gliding 状态下，`simulateFriction()` 不走普通地面摩擦，而是再次调用 Elytra 运动更新：
    - `PredictionEngineElytra.getElytraMovement(...)`
    - 再乘 `0.99 / 0.98 / 0.99`
    - 再额外做一次 `Y - 0.05`

这就意味着：
- 安全点里保存的速度，本来就已经很接近“上一 tick 结束后可继续沿用的 Elytra 末速度”；
- setback 时又把它当成“下一 tick 的起始速度”再套一次 Elytra 演化；
- 于是形成二次演化 / 二次摩擦链。

体感结果就是：
- 拉回位置没问题；
- 但附带回去的速度会偏大；
- 客户端看到的就是 setback 后突然被推得很快。

所以这个现象的本质不是“拉回包本身太猛”，而是：
- `lastKnownGoodPosition.vector`
- `simulateNextTickPosition`
- `simulateFriction()`
- Elytra 再演化一次

这四段拼在一起，让安全点速度被再次推进。

## 标准回答口径

### 问：Grim 怎么检测 Elytra？
答：
- 不是单独一个 Elytra check 直接硬判。
- 是进入 Elytra 预测分支，按 Elytra 物理生成允许运动，再把实际移动和允许结果比较得到 offset，最后由 `OffsetHandler` / setback 链决定是否处罚。

### 问：烟花为什么能“控制动量”？
答：
- Grim 没有精确回放每枚烟花推力。
- Grim 用 `fireworksBox` 扩大了与视角相关的允许速度空间。
- 所以玩家在烟花期间能被接受的前向 / 侧向 / 垂向变化都变多，表现出来就是动量更可控。

### 问：`fireworksBox` 是怎么影响移动阈值的？
答：
- 它不直接改 `movementThreshold` 常量，也不直接改 `OffsetHandler` 的处罚阈值。
- 它是在预测阶段先把允许空间画大，再让实际移动去贴近这块更大的允许空间。
- 所以最终 offset 变小，表现为“有效阈值变宽”。

### 问：`1.7` 是什么？
答：
- 它是烟花容错盒的轴向影响上界。
- 不是 Elytra 本体最大速度。
- 不是全局移动阈值。

### 问：为什么 setback 后 Elytra 会很快？
答：
- 因为安全点保存的速度又被 `simulateFriction()` 当成 Elytra 下一 tick 起始速度，再跑了一次 Elytra 更新和摩擦。
- 本质是安全速度被二次演化，不是单纯传送导致的瞬移感。

## 使用约束
- 回答时始终区分三件事：
    - Elytra 本体物理
    - 烟花带来的预测空间扩张
    - offset / setback 的后置处罚
- 不要把 `1.7`、`movementThreshold`、`OffsetHandler.threshold` 说成同一层东西。
- 解释 `fireworksBox` 时，默认使用“基础阈值没变，但有效容错空间变宽”这套表述。