---
name: elytra-axis-limit-target-climb
description: 理解并维护 SlimefunHelper 中 `ElytraExtra.applyAxisLimit2` 的 Elytra 烟花容错盒、两帧 look 叠加、目标逼近与爬升策略选择。用于分析 `currentMotion = currentRotation * 1.7` 一类上推方案，判断斜向追点、竖直拉升、前后折返等 rotation 序列的收益与约束。
disable-model-invocation: true
---

# elytra-axis-limit-target-climb

## 目标

处理 `me.matl114.hacks.modules.move.ElytraExtra` 的飞行追点问题时，统一按 `applyAxisLimit2` 这套运行时模型思考：

- 当前 tick 想发什么方向
- 上一 tick 留下了什么方向
- 两帧方向如何共同决定容错盒
- 真正想优化的是 `Y` 攀升、横向逼近、还是两者折中

不要退回到“单 tick 只看当前朝向”的直觉。

## 一句话模型

`applyAxisLimit2` 不是直接给一个“速度上限球”，而是用“当前 look + 上一 tick look”构出一个三轴容错盒，再把本 tick 请求速度压进这个盒允许的边界里。

所以 rotation 不是只影响“这一刻往哪飞”，还会影响“下一刻的允许空间长什么样”。

## 核心模块关系

- 入口策略层：`ElytraExtra.applyAxisLimit2`
- Elytra 本体递推：`EntityUtils.calculateGlidingVelocity`
- 最终注入到滑翔计算：`LivingEntityMixin.travelGliding`
- 历史状态来源：`PlayerStateManager.lastKnownRealMovementSpeed`、`lastPitch/lastYaw`

## 运行时状态流

### 1. 输入

这一层至少有 4 个量：

- `v1`：上一 tick 真实移动速度
- `lookNow`：当前 rotation
- `lookPrev`：上一 tick 的 pitch/yaw 对应 rotation
- `request`：本 tick 想申请的运动量，常见就是 `currentRotation * 1.7`

### 2. 先算本体滑翔递推

先把 `v1` 和 `lookNow` 丢进 Elytra 物理，得到本 tick 的基础可达结果 `v3`。

这一步的结构是：

- 重力先作用
- 下落量会转成沿 look 的推进
- 抬头/俯冲会继续改写 `Y` 和前向分量
- 水平速度再向当前 look 对齐
- 最后乘阻力

直接结论：

- 想要更高 `Y`，不能只看 `look.y`
- 还要看当前已有水平动量是否足够被转成向上的收益

### 3. 再构造两帧 look 容错盒

`applyAxisLimit2` 的盒子不是只由当前朝向决定，而是：

- 当前 look 每个轴给一份方向贡献
- 上一 tick look 每个轴再给一份方向贡献
- 再叠加 anti-tick-skipping 最小宽度
- 再乘 `threshold`，通常就是 `min(autoRescaleAmount, request.length)`

可以把它理解成：

- 如果两帧都朝同一侧，那个轴会被连续放大
- 如果两帧朝相反侧，那个轴会被摊成“双向都能解释一点”，但单侧峰值不会继续累加

### 4. request 不是直接通过，而是按轴压边界

系统最后看的不是 request 的模长，而是：

- `X` 是否超过 `uMinX/uMaxX`
- `Y` 是否超过 `uMinY/uMaxY`
- `Z` 是否超过 `uMinZ/uMaxZ`

谁先超，谁就决定整体缩放比例。

所以这是一个典型的三轴瓶颈问题：

- 不是“总能量不超就行”
- 而是“最先爆掉的那根轴”决定你最后还能剩多少速度

## 飞行优化目标的正确拆法

讨论“怎么飞最好”时，先明确目标函数，否则结论会错位。

### 目标 A：只要 `Y` 尽快增加

重点是：

- `uMaxY` 要大
- 本体滑翔递推后的 `v3.y` 要大
- 当前 request 的横向不要白白抢掉缩放空间

### 目标 B：横向尽快逼近移动目标

重点是：

- 当前 look 的水平投影必须指向目标预测位置
- 上一 tick look 最好也大体同向，这样横向轴的盒子会连续放大
- 不要为了纯爬升把水平投影缩得太小

### 目标 C：`Y` 攀升同时横向逼近

重点不是极端，而是同向复利：

- 当前和上一 tick 都保持“朝目标的水平分量 + 明显向上分量”
- 让 `Y` 和目标方向横向轴同时得到两帧增强
- 避免无意义地在相邻 tick 里把横向主轴来回打反

## 三种策略的验证结论

下面默认目标不是静止点，而是“横向仍在移动的点”，因此横向逼近不能被忽略。

### 1. 斜向飞行：横向对准目标，同时保留向上分量

这是当前模型下最合理的主策略。

#### 为什么有效

因为它同时满足 3 件事：

- 当前 tick 的水平投影直接朝目标，横向误差在收敛
- `look.y` 仍为正，`Y` 轴容错和本体递推仍能吃到上升收益
- 如果连续几 tick 都维持相近方向，上一 tick look 会继续给同一批轴加成

#### 它的真实优势

它不是“单 tick 最高飞”，而是“多 tick 综合收益最高”：

- `Y` 持续增加
- 横向持续逼近
- 盒子的主放大方向稳定
- 请求速度不容易因为某一根横向轴突然翻向而被整体缩掉

#### 适用条件

适合绝大多数“追一个移动点并继续拉高”的场景。

#### 约束

如果抬头过猛，水平投影会太小，横向收敛会变慢。

如果过于平飞，`Y` 轴收益又不够。

所以它本质上是一个折中角度问题，不是纯竖直，也不是纯前冲。

### 2. 竖向飞行：尽量只保留向上分量

这不是追移动目标的优先策略，只适合非常特殊的局面。

#### 为什么不适合常规追点

因为一旦水平投影过小，会同时出现两个问题：

- 横向对目标的收敛几乎停掉
- 两帧 look 叠出来的横向盒子也会变窄，后续想重新拉回目标时，横向轴更容易成为瓶颈

#### 它唯一可能占优的地方

如果目标几乎就在正上方，或者短窗口内只关心 `Y` 抬升、不关心横向误差，那么竖向策略可以作为临时动作。

#### 结构性缺点

- 无法稳定追移动目标
- 把未来 tick 的横向容错也一起做薄了
- Elytra 的向上收益仍然依赖已有水平动量参与转换，纯竖直不是“白拿最大上升”的简单模式

所以它更像“短时抢高度”，不是“追点爬升”的主方案。

### 3. 来回折返：第一刻前上，第二刻后上

这套策略对“逼近移动目标并尽快上升”的综合目标不优。

#### 看起来像有利的地方

如果两 tick 都保持向上，那么 `Y` 轴确实还能持续吃到正向贡献。

#### 真正的问题

它会把横向主轴做成相互对冲：

- 本 tick 朝前，下一 tick 朝后
- 两帧 look 在横向轴上的累计不再是“同侧放大”
- 结果更像“左右两边都留一点解释空间”，而不是“朝目标这一边继续加宽”

这会直接带来两个后果：

- 横向逼近目标的净位移变差，甚至接近抵消
- 本体滑翔的水平对齐项也会来回扯，导致速度方向振荡

#### 对当前目标函数的结论

如果目标是“边爬边追移动点”，折返策略基本劣于稳定斜向。

它最多只在一种偏门目标下有讨论价值：

- 只想保 `Y`
- 横向允许近似悬停
- 想利用对称摆动换取某种特殊观感或测试边界

但这已经不是“逼近移动目标”的同一问题了。

## 最终排序

如果目标是：`Y` 攀升尽量快，同时横向逼近一个仍在移动的点。

默认排序应当是：

1. 斜向飞行
2. 竖向飞行
3. 前后折返

其中：

- 斜向飞行是主策略
- 竖向飞行只适合短时抢高度
- 前后折返不适合作为追点策略

## 实战决策规则

### 规则 1

先预测目标下一小段时间的横向位置，不要只盯当前点。

### 规则 2

每个 tick 的 look 水平分量尽量持续指向目标预测位置，不要频繁反向。

### 规则 3

在能维持横向收敛的前提下，再尽量增大 `look.y`。

### 规则 4

如果连续两 tick 的横向主轴发生反转，要先默认这是坏信号，而不是优化。

### 规则 5

只有在“目标几乎正上方 / 短时只抢高度”时，才考虑临时切到更竖的姿态。

## 推荐的搜索思路

找“最佳 rotation 序列”时，不要把每 tick 独立贪心。

正确的搜索单位至少要是 2 tick：

- 当前 tick 产出即时 `Y` 与横向逼近
- 下一 tick 会继承这次的 look，继续影响盒子

也就是说，rotation 序列优化至少要同时评估：

- 当前 tick 的收益
- 对下一 tick 容错形状的影响

## 伪代码心智模型

```text
state:
  lastVelocity
  lastLook
  targetPredictedHorizontalPos

for tick in horizon:
  candidateLook = chooseLook(
    horizontal -> point to targetPredictedHorizontalPos,
    vertical -> as high as possible without killing horizontal convergence
  )

  box = buildBox(candidateLook, lastLook, threshold)
  v3 = glideStep(lastVelocity, candidateLook)
  request = candidateLook * 1.7
  accepted = clampRequestIntoBox(request, box, v3)

  score = gainY(accepted, v3) + approachTarget(accepted) - oscillationPenalty(candidateLook, lastLook)

  advance state
```

## 修改代码时必须守住的判断方式

- 不要只看当前 tick 的 `lookNow`
- 不要把 `1.7` 理解成“最终实际速度固定等于 1.7”
- 不要用“纯竖直一定最能涨高”的直觉替代递推链
- 不要把前后折返误判成“横向盒子更宽所以一定更强”
- 只要目标在移动，就优先保护横向主方向的连续性