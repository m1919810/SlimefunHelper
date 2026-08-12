---
name: minecraft-explosion-analysis
description: Analyze explosion sources, power, terrain destruction, entity damage, wind charge differences, and block occlusion in this merged Yarn Minecraft tree. Use when tracing `ExplosionImpl`, `ExplosionBehavior`, `createExplosion`, crystal/bed/respawn-anchor/TNT explosions, wind charge behavior, or when asking how blast damage reaches a player or box.
disable-model-invocation: true
---

# Minecraft Explosion Analysis

## Scope
- Core runtime
  - `net/minecraft/world/explosion/ExplosionImpl`
  - `net/minecraft/world/explosion/Explosion`
  - `net/minecraft/world/explosion/ExplosionBehavior`
  - `net/minecraft/world/explosion/AdvancedExplosionBehavior`
  - `net/minecraft/world/explosion/EntityExplosionBehavior`
- World dispatch
  - `net/minecraft/server/world/ServerWorld`
  - `net/minecraft/world/World`
- High-value source families
  - `net/minecraft/entity/decoration/EndCrystalEntity`
  - `net/minecraft/block/RespawnAnchorBlock`
  - `net/minecraft/block/BedBlock`
  - `net/minecraft/entity/TntEntity`
  - `net/minecraft/entity/vehicle/TntMinecartEntity`
  - `net/minecraft/entity/mob/CreeperEntity`
  - `net/minecraft/entity/projectile/AbstractWindChargeEntity`
  - `net/minecraft/entity/projectile/WindChargeEntity`
  - `net/minecraft/entity/projectile/BreezeWindChargeEntity`
  - `net/minecraft/entity/effect/WindChargedStatusEffect`
  - `net/minecraft/entity/projectile/WitherSkullEntity`
  - `net/minecraft/entity/projectile/FireballEntity`
  - `net/minecraft/enchantment/effect/entity/ExplodeEnchantmentEffect`
- Supporting data
  - `data/minecraft/tags/block/blocks_wind_charge_explosions.json`

## Core questions
优先回答下面几类问题：
1. 哪些路径会生成爆炸
2. 每类爆炸的 `power` 是多少，是否固定、是否可变
3. 风弹是否真的生成爆炸；如果生成，它与普通爆炸有什么不同
4. 爆炸如何破坏地形
5. 爆炸如何对实体造成伤害和击退
6. 伤害是否依赖大量 raycast
7. 对一个常规玩家或任意给定 box，哪些方块会影响它吃到的爆炸伤害

## Main runtime model
把爆炸拆成 3 层：

### 1. 世界调度层
- 统一入口是 `ServerWorld.createExplosion(...)`
- 这里把 `ExplosionSourceType` 映射成 `DestructionType`
- 同时决定：
  - 是否保留方块
  - 是否允许掉落衰减
  - 是否是“只触发方块而不直接炸掉方块”的模式

映射关系：
- `NONE -> KEEP`
- `BLOCK -> DESTROY / DESTROY_WITH_DECAY`
- `MOB -> KEEP` 或 `DESTROY / DESTROY_WITH_DECAY`，取决于 `DO_MOB_GRIEFING`
- `TNT -> DESTROY / DESTROY_WITH_DECAY`
- `TRIGGER -> TRIGGER_BLOCK`

### 2. 爆炸求解层
- 实际核心在 `ExplosionImpl`
- 主要分两段：
  - `getBlocksToDestroy()` 计算地形影响
  - `damageEntities()` 计算实体伤害、击退、抛射物重定向

### 3. 规则覆写层
- `ExplosionBehavior` 给出默认规则
- `EntityExplosionBehavior` 允许“爆炸源实体”改写方块抗爆和可破坏性
- `AdvancedExplosionBehavior` 允许按配置切换：
  - 是否破坏方块
  - 是否伤害实体
  - 击退倍率
  - 免疫方块集合

## High-value explosion source table
优先按下面结构输出，不要默认穷举所有调用点；先给高频和用户关注对象。

### 固定威力
- 床爆炸
  - 来源：`BedBlock.onUse`
  - 条件：当前维度规则要求 bed 爆炸
  - `power = 5.0`
  - `createFire = true`
  - 来源类型：`BLOCK`
- 重生锚爆炸
  - 来源：`RespawnAnchorBlock.explode`
  - 条件：当前维度不可用 respawn anchor
  - `power = 5.0`
  - `createFire = true`
  - 来源类型：`BLOCK`
  - 特别点：若锚本体旁边/上方有静水，会把锚位置视为水抗爆，改变近场传播
- 末地水晶爆炸
  - 来源：`EndCrystalEntity.damage`
  - 条件：被非爆炸伤害摧毁
  - `power = 6.0`
  - `createFire = false`
  - 来源类型：`BLOCK`
- TNT
  - 来源：`TntEntity.explode`
  - 默认 `power = 4.0`
  - 来源类型：`TNT`
- 苦力怕
  - 来源：`CreeperEntity.explode`
  - 默认半径 `3`
  - 普通苦力怕 `power = 3.0`
  - 高压苦力怕 `power = 6.0`
  - 来源类型：`MOB`
- 凋灵出生爆炸
  - 来源：`WitherEntity`
  - `power = 7.0`
  - 来源类型：`MOB`
- 凋灵之首碰撞爆炸
  - 来源：`WitherSkullEntity.onCollision`
  - `power = 1.0`
  - 来源类型：`MOB`

### 风弹家族
- 玩家风弹
  - 来源：`WindChargeEntity.createExplosion`
  - `power = 1.2`
  - 来源类型：`TRIGGER`
  - 使用专用 `AdvancedExplosionBehavior`
- Breeze 风弹
  - 来源：`BreezeWindChargeEntity.createExplosion`
  - `power = 3.0`
  - 来源类型：`TRIGGER`
  - 复用 `AbstractWindChargeEntity.EXPLOSION_BEHAVIOR`
- Wind Charged 状态死亡爆风
  - 来源：`WindChargedStatusEffect.onEntityRemoval`
  - `power = 3.0 ~ 5.0` 随机
  - 来源类型：`TRIGGER`

### 可变威力
- TNT 矿车
  - 来源：`TntMinecartEntity.explode`
  - 基础 `explosionPower = 4.0`
  - 实际威力 = `基础威力 + speedFactor * random * 1.5 * min(sqrt(horizontalSpeedSquared), 5.0)`
  - 所以速度越高，爆炸越强
- 火球
  - 来源：`FireballEntity.onCollision`
  - 默认 `power = 1`
  - 但实例可携带自定义 `explosionPower`
- 附魔驱动爆炸
  - 来源：`ExplodeEnchantmentEffect`
  - 半径由 `radius.getValue(level)` 决定
  - 是否伤害实体、是否破坏方块、击退倍率、免疫方块集合都可配

## Wind charge differences
回答风弹时，明确说下面几点：

### 1. 风弹确实生成“标准爆炸对象”
- 不是假击退
- 它同样走 `createExplosion -> ExplosionImpl`
- 所以也会进入实体伤害/击退和地形传播框架

### 2. 但它不是普通 TNT/水晶式爆炸
- 风弹用的是 `ExplosionSourceType.TRIGGER`
- 世界层把它映射成 `DestructionType.TRIGGER_BLOCK`
- 这意味着默认 `onExploded` 不会直接把方块炸成空气
- 更偏向“触发型爆炸”而不是“清空地形型爆炸”

### 3. 风弹通常不直接造成爆炸伤害
- 风弹行为使用 `AdvancedExplosionBehavior(... damageEntities = false ... )`
- 所以 `ExplosionImpl.damageEntities()` 里仍会算 exposure 与击退，但默认不通过爆炸流程直接扣血
- 玩家风弹命中实体的 1 点伤害来自风弹碰撞前的单独 `entity.damage(..., 1.0F)`，不是爆炸本体

### 4. 风弹的击退可被单独调高
- 玩家风弹把击退倍率设为 `1.22`
- Breeze 风弹与 Wind Charged 爆风走基类行为，默认击退倍率 `1.0`

### 5. 风弹有免疫方块名单
- `blocks_wind_charge_explosions` 标签里默认有：
  - `minecraft:barrier`
  - `minecraft:bedrock`
- 在该集合中的方块会被当作超高抗爆屏障

## Terrain destruction model
说明地形破坏时，用下面结构：

### 1. 不是直接对球体内所有方块逐块判定
- `ExplosionImpl.getBlocksToDestroy()` 从一个 `16 x 16 x 16` 立方体外壳发射射线
- 只取外壳格点，所以实际是“外壳采样射线”，不是完整 4096 条
- 外壳点数是 `16^3 - 14^3 = 1352`

### 2. 每条射线沿 0.3 格步进
- 初始能量：`power * (0.7 + random * 0.6)`
- 每前进一步先看当前方块/流体抗爆
- 若命中非空气/非空流体，能量减少：
  - `((blastResistance) + 0.3) * 0.3`
- 每步还会再减固定值 `0.22500001`
- 能量仍大于 0 且行为允许破坏时，该方块加入待处理集合

### 3. 方块是否真正消失，取决于 destruction type
- `KEEP` 不清方块
- `DESTROY` / `DESTROY_WITH_DECAY` 才真正走方块销毁和掉落
- `TRIGGER_BLOCK` 默认不直接清块；更像给方块一个“被爆炸触发”的机会

### 4. 掉落衰减
- `DESTROY_WITH_DECAY` 会把爆炸半径写入 loot context
- 方块掉落可因此衰减

## Entity damage model
说明实体伤害时，用下面结构：

### 1. 先做粗范围筛选
- 搜索盒是以爆炸中心为核心、半径 `2 * power + 1` 的 AABB
- 只有盒内实体进入后续计算

### 2. 每个实体的伤害由两部分相乘
- 距离衰减
- 遮挡暴露度 `exposure`

### 3. 暴露度不是单点，而是 box 采样
- `calculateReceivedDamage(pos, entity)` 取实体 bounding box
- 在 box 内做规则网格采样
- 从每个采样点向爆炸中心做一次 `world.raycast(...)`
- 无遮挡记为可见
- `exposure = 可见样本数 / 总样本数`

### 4. 默认伤害公式
- `f = power * 2`
- `d = distance / f`
- `e = (1 - d) * exposure`
- 最终伤害 = `((e * e + e) / 2) * 7 * f + 1`
- 距离越远、遮挡越多，伤害越低

### 5. 击退单独算
- 击退强度 = `(1 - d) * exposure * knockbackModifier * (1 - explosionKnockbackResistance)`
- 即使该爆炸不直接造成伤害，只要 `knockbackModifier != 0`，仍可能产生击退

## Does it use lots of raycasts?
分成两件事回答，不要混为一谈：

### 1. 地形破坏
- 不使用 `world.raycast`
- 用 1352 条离散射线 + 0.3 格步进手动推进
- 这是“很多射线传播计算”，但不是逐实体的那种 API raycast

### 2. 实体受伤/受击退遮挡
- 会使用很多 `world.raycast`
- 每个实体都要按其 box 采样点数量做多次视线检测
- 样本数和 box 尺寸有关

### 常规玩家的样本数
- 玩家默认 box 约 `0.6 x 1.8 x 0.6`
- 采样步长公式会得到：
  - X 方向 3 个采样层
  - Y 方向 5 个采样层
  - Z 方向 3 个采样层
- 合计约 `45` 次 raycast
- 所以“常规玩家吃一次爆炸”通常不是 1 条线，而是一批线

## Which blocks affect damage at a player or box?
回答“某个 box 位置受到哪些方块影响”时，使用下面结论：

### 1. 只看采样点到爆炸中心之间的遮挡链
- 真正有影响的，不是爆炸球内全部方块
- 只有那些位于“采样点 -> 爆炸中心”连线上的碰撞体方块会改变 exposure

### 2. 影响方式是按样本点独立计数
- 若某个方块挡住很多采样线，它会显著降伤
- 若只挡住少量采样线，它只降低部分 exposure
- 所以同一堵墙，对玩家站姿、半身露出、脚下高差都很敏感

### 3. 对给定 box 的最小判断模型
把 box 受伤理解成：
- 在 box 内取一批样本点
- 每个样本点向爆炸中心连线
- 所有与这些连线相交的碰撞方块，都是“影响伤害的方块”
- 这些方块的作用不是累计抗爆值，而是决定该样本线是否被挡住

### 4. 爆炸中心附近方块和实体贴身方块都重要
- 贴爆炸点的厚障碍，会同时挡住大量射线
- 贴玩家身体一侧的局部掩体，也可能只遮住部分样本点
- 因此“爆心附近方块”和“目标 box 附近方块”都可能是关键遮挡体

## When can box sampling be approximated by a single line?
当用户问“能不能把 box 多采样近似成一条从爆心到 box 的线”时，按下面规则回答。

### 1. 本质
- 原版实体伤害遮挡，默认是“box 内多采样点 -> 爆心”的可见率
- 单线近似，等价于把这些采样点压缩成一个代表点，通常取 `box center`
- 只有当 box 从爆心看过去足够小，且局部没有强边缘遮挡时，这个近似才稳定

### 2. 代表尺度
给定 box：
- `center = box.center`
- `halfX = (maxX - minX) / 2`
- `halfY = (maxY - minY) / 2`
- `halfZ = (maxZ - minZ) / 2`
- `rBox = sqrt(halfX^2 + halfY^2 + halfZ^2)`
- `dist = distance(explosionPos, center)`
- `spread = rBox / dist`

### 3. 经验判定区间
对常规玩家 box，优先使用下面分段：
- 很好近似
  - `spread <= 0.10`
  - 大约对应 `dist >= 10`
- 基本可近似
  - `0.10 < spread <= 0.16`
  - 大约对应 `6 ~ 10` 格
- 不建议近似
  - `spread > 0.16`
  - 大约对应 `dist < 6`

### 4. 即使距离够远，也会失效的情况
只要命中下面任一类，都不要把它当成稳定单线：
- 墙角探头
- 半身位
- 贴墙、贴门框、贴柱子
- 爆心附近 `1~2` 格内有窄柱、半砖、楼梯、墙、活板门等局部遮挡
- 目标 box 附近 `1` 格内有非满方块边缘掩体

### 5. 可用的保守判定
只有同时满足下面两条，才把多采样压成单线：
- `spread <= 0.16`
- 爆心近端和目标近端都不存在明显边缘敏感遮挡

### 6. 边缘敏感遮挡的保守定义
第一版可以直接把这些都当作“单线不稳”的高风险方块：
- 非满立方体
- 楼梯
- 半砖
- 墙
- 栅栏
- 铁栏杆
- 玻璃板
- 活板门
- 门

## Single-line approximation with custom BlockAccess
当用户已经有：
- 爆心
- 玩家 box
- 一个影响位置列表
- 一个 `BlockAccess`，可读取 block state、collision shape、自定义地形

则近似模型按下面流程输出。

### 1. 输入形状
最小输入结构：
- `explosionPos: Vec3`
- `playerBox: Box`
- `power: float`
- `influencingPositions: List<BlockPos>`
- `blockAccess`

`blockAccess` 至少要支持：
- 读 `BlockState`
- 读 collision shape
- 让调用方判断“某个方块的形状是否拦住了这条线”

### 2. 代表点
- `samplePoint = playerBox.center`
- 默认不要取眼睛点
- 这个近似是在压缩“整个 box 的可见率”，不是在模拟视线

### 3. 近似版中的影响方块
单线近似里，影响伤害的方块定义为：
- 所有与 `explosionPos -> samplePoint` 线段相交的阻挡方块

如果已经有 `influencingPositions`，则只需要在这批位置里筛：
- AABB 上可能靠近该线段的候选方块
- 再用 collision shape 判断是否真的与线段相交

### 4. 最小筛选流程
- 先取 `explosionPos -> samplePoint` 线段的包围盒
- 向外膨胀少量距离，例如 `0.5`
- 只保留与该包围盒相交的 `influencingPositions`
- 对保留下来的位置：
  - 读取 `state`
  - 读取 `shape`
  - 判断 `shape@worldPos` 是否与线段相交
- 命中的位置组成 `blockingBlocks`

### 5. exposure 近似
最粗版本：
- `blockingBlocks` 为空 -> `exposure = 1.0`
- `blockingBlocks` 非空 -> `exposure = 0.0`

更稳的工程版可用三档：
- 完全无遮挡 -> `1.0`
- 只被薄掩体、边缘掩体命中 -> `0.5`
- 被完整厚掩体明确挡住 -> `0.0`

### 6. 距离项与最终伤害
近似版默认继续沿用原版伤害公式：
- `f = power * 2`
- `d = distance(explosionPos, samplePoint) / f`
- 若 `d >= 1`，伤害为 `0`
- `e = (1 - d) * exposure`
- `rawDamage = ((e * e + e) / 2) * 7 * f + 1`

### 7. 输出结构
建议整理成：
- `canUseSingleLine`
- `representativePoint`
- `usedBlocks`
- `blockingBlocks`
- `exposure`
- `normalizedDistance`
- `rawDamage`

### 8. 结果解释
若用户问“给定爆心、玩家 box、影响方块 list、BlockAccess，最后怎么出伤害”，直接按下面解释：
- 先判断是否满足单线近似条件
- 若不满足，就回退到原版 box 多采样逻辑
- 若满足：
  - 取 `box center` 作为代表点
  - 在 `influencingPositions` 里找出所有拦住 `爆心 -> 代表点` 线段的碰撞方块
  - 用这些方块得到近似 `exposure`
  - 再代入原版默认爆炸伤害公式

### 9. 最短公式版
若已确认可以单线近似，则：
- `samplePoint = center(playerBox)`
- `blockingBlocks = { pos in influencingPositions | collisionShape(pos) intersects segment(explosionPos, samplePoint) }`
- `exposure = 1.0 / 0.5 / 0.0`，取决于遮挡强度
- `f = power * 2`
- `d = distance(explosionPos, samplePoint) / f`
- `e = max(0, 1 - d) * exposure`
- `damage = ((e * e + e) / 2) * 7 * f + 1`

## Output template
默认输出结构：
- 爆炸入口分层
- 重点来源与威力表
- 风弹是否生成爆炸，以及与普通爆炸的差异
- 地形破坏链路
- 实体伤害/击退链路
- raycast 与采样规模
- 对玩家/给定 box 的遮挡影响模型
- 若需要继续扩展，再补充其它低频爆炸来源

## Constraints
- 不做逐行解释
- 优先回答“运行路径、威力、差异、影响范围”
- 用户问“某来源有没有爆炸”，先判断它是否真的调用 `createExplosion`
- 用户问“为什么 `isExplosionProof` / 抗爆高还会怎样”，区分：
  - 地形传播的抗爆衰减
  - 实体 exposure 的遮挡判定
  - `TRIGGER_BLOCK` 是否真的清方块