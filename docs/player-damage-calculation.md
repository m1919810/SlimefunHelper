---
name: player-damage-calculation
description: 基于 Minecraft Java Edition 1.21.11 Yarn build 4 合并源码，追踪玩家从收到 DamageSource 到护盾、护甲、抗性、附魔、吸收量和生命扣除的完整伤害计算流程。
---

# Minecraft 玩家伤害计算机制源码分析

- 分析日期：2026-10-01
- 目标版本：本仓库中的 Minecraft Java Edition 1.21.11 Yarn build 4 合并源码
- 原始问题：从玩家受伤开始，了解伤害经过护甲等因素减免后如何扣除生命值，并总结完整流程

## 1. 结论先行

对服务端玩家而言，一次伤害的核心顺序是：

```text
攻击/环境构造 DamageSource 与原始 amount
  -> Entity.serverDamage（仅转发到服务端）
  -> PlayerEntity.damage
       -> 无敌、游戏规则、玩家能力无敌拦截
       -> 按伤害类型和难度调整 amount
  -> LivingEntity.damage
       -> 无敌/死亡/火焰抵抗检查
       -> 护盾阻挡
       -> 冻结额外伤害、砧/方块/钟乳石对头盔的伤害
       -> 无敌帧（damage cooldown）合并或拒绝本次伤害
  -> PlayerEntity.applyDamage
       -> 护甲耐久损耗与护甲/护甲韧性减伤
       -> Resistance 抗性效果
       -> Protection 等伤害保护附魔
       -> 吸收量（Absorption）
       -> 食物 exhaustion、DamageTracker、生命值扣除、游戏事件
```

这里的 `amount` 是进入受伤流程时已经确定的伤害值；攻击力、武器附魔、暴击、摔落距离等如何生成这个值，属于调用 `damage` 之前的攻击或环境专用逻辑，不在本报告的主链中。

## 2. 入口与服务端权威

`Entity.serverDamage` 只在实体所在世界是 `ServerWorld` 时调用 `damage(serverWorld, source, amount)`，因此玩家最终生命值的权威计算发生在服务端：

- `net/minecraft/entity/Entity.java:1884-1887`

`DamageSource` 同时携带伤害类型、直接伤害实体、攻击者和位置。它还可以通过 `getWeaponStack()` 取得直接伤害实体的武器；这个武器会参与 `Breach` 等改变护甲减伤效率的附魔逻辑：

- `net/minecraft/entity/damage/DamageSource.java:16-65`
- `net/minecraft/entity/damage/DamageSource.java:94-104`

伤害类型的行为不是完全写死在 `DamageSource` 类中，而是通过 `DamageTypeTags` 判断，例如 `bypasses_armor`、`bypasses_resistance`、`bypasses_enchantments`、`is_fire`、`is_projectile` 和 `damages_helmet`：

- `net/minecraft/registry/tag/DamageTypeTags.java:8-46`

## 3. 玩家专属前置处理

### 3.1 无敌、游戏规则和死亡状态

`PlayerEntity.isInvulnerableTo` 先调用父类逻辑。父类会检查实体本身的永久免疫以及装备附魔提供的伤害免疫：

- `net/minecraft/entity/LivingEntity.java:3924-3925`
- `net/minecraft/enchantment/EnchantmentHelper.java:176-182`

玩家随后根据伤害类型检查游戏规则：

- 溺水受 `DROWNING_DAMAGE` 控制
- 摔落受 `FALL_DAMAGE` 控制
- 火焰受 `FIRE_DAMAGE` 控制
- 冻结受 `FREEZE_DAMAGE` 控制

对应实现位于 `net/minecraft/entity/player/PlayerEntity.java:666-680`。

`PlayerEntity.damage` 还会拦截开启 `abilities.invulnerable` 的玩家，以及已经死亡的玩家；`BYPASSES_INVULNERABILITY` 可以绕过能力无敌，但不能绕过所有前置判断：

- `net/minecraft/entity/player/PlayerEntity.java:682-691`

### 3.2 难度缩放

只有 `DamageSource.isScaledWithDifficulty()` 为真时才执行玩家难度缩放。伤害类型的 scaling 规则由 `DamageType` 决定：

- `NEVER`：不缩放
- `WHEN_CAUSED_BY_LIVING_NON_PLAYER`：由非玩家生物造成时缩放
- `ALWAYS`：始终缩放

证据：`net/minecraft/entity/damage/DamageSource.java:94-104`。

对满足条件的伤害，`PlayerEntity.damage` 使用以下规则：

- 和平：`amount = 0`
- 简单：`amount = min(amount / 2 + 1, amount)`
- 普通：不修改
- 困难：`amount = amount * 3 / 2`

证据：`net/minecraft/entity/player/PlayerEntity.java:692-707`。

这一步发生在护盾、护甲和抗性之前，所以难度缩放会成为后续所有减伤层的输入。

需要区分“玩家接收伤害时的额外增伤”和“伤害来源在调用 `damage` 之前生成了更大的 `amount`”：

- `PlayerEntity.damage` 自身对 `amount` 的直接增量只有困难难度的 `amount * 3 / 2`；简单难度是降低或保持原值，和平难度直接归零。当前源码没有其他玩家专属的 `amount *= ...` 增伤分支。
- `LivingEntity.damage` 中的冻结额外伤害 `amount *= 5` 不是玩家专属逻辑，而且当前 `freeze_hurts_extra_types` 只有 `strider`、`blaze`、`magma_cube`，不包含玩家：`net/minecraft/entity/LivingEntity.java:1191-1193`、`data/minecraft/tags/entity_type/freeze_hurts_extra_types.json:1-6`。
- 攻击力、暴击、武器附魔、重锤下落攻击、爆炸距离等造成的更大 `amount`，发生在目标进入 `PlayerEntity.damage` 之前，属于伤害来源生成阶段，不是玩家接收伤害阶段的额外增伤。

当前数据中，`scaling = always` 的伤害类型是 `bad_respawn_point`、`explosion`、`player_explosion` 和 `sonic_boom`；它们对满足 `PlayerEntity.damage` 入口的玩家始终执行难度缩放。`scaling = when_caused_by_living_non_player` 则只有攻击者是非玩家生物时才缩放；即使伤害类型名是 `player_attack`，攻击者为玩家时也不会因为该规则缩放：`net/minecraft/entity/damage/DamageTypes.java:72-110`、`net/minecraft/entity/damage/DamageSource.java:94-104`。

## 4. 通用受伤阶段：护盾、特殊伤害与无敌帧

### 4.1 基础拦截与负数归零

`LivingEntity.damage` 再次检查实体是否免疫、是否死亡，以及火焰抵抗效果是否拦截火焰伤害：

- `net/minecraft/entity/LivingEntity.java:1169-1175`

之后负伤害会被归零：`net/minecraft/entity/LivingEntity.java:1181-1184`。

### 4.2 护盾阻挡发生在护甲之前

这里的“护盾阻挡”指玩家正在使用具有 `BLOCKS_ATTACKS` 数据组件的可格挡物品；原版 `Shield` 就是这种物品。它不是 Absorption，也不是护甲值减伤。`getDamageBlockedAmount` 先检查玩家是否正在使用可格挡物品，然后按以下因素计算阻挡量：

1. 伤害类型是否被该物品的 `bypassed_by` 标签绕过；
2. 远程投射物是否带有穿透等级；
3. 伤害位置与玩家朝向的夹角；
4. 物品 `BlocksAttacksComponent` 中配置的 `DamageReduction`。

实现入口和顺序：`net/minecraft/entity/LivingEntity.java:1186-1193`、`net/minecraft/entity/LivingEntity.java:1301-1338`。

`BlocksAttacksComponent` 会把多个减伤项相加，并将总阻挡量限制在 `[0, damage]`：

- `net/minecraft/component/type/BlocksAttacksComponent.java:92-100`
- `net/minecraft/component/type/BlocksAttacksComponent.java:119-120`
- 原版盾牌注册了 `DataComponentTypes.BLOCKS_ATTACKS`：`net/minecraft/item/Items.java:3276`

因此可以把护盾阶段抽象为：

```text
shieldBlocked = clamp(所有匹配的 DamageReduction 之和, 0, 当前伤害)
amount = amount - shieldBlocked
```

若护盾完全挡住伤害，后续仍可能发生某些受击状态处理，但生命值扣除路径中的 `amount` 已经是 0。

### 4.3 冻结额外伤害和头盔处理

护盾之后，如果伤害带有 `IS_FREEZING` 且目标实体类型属于 `FREEZE_HURTS_EXTRA_TYPES`，伤害乘以 5：

- `net/minecraft/entity/LivingEntity.java:1191-1193`

如果伤害带有 `DAMAGES_HELMET` 且玩家的 `HEAD` 装备槽不为空（通常是头盔）：

1. 先按当前 `amount` 损耗头盔；
2. 再将伤害乘以 `0.75`。

实现：`net/minecraft/entity/LivingEntity.java:1195-1197`；玩家头盔损耗槽位：`net/minecraft/entity/player/PlayerEntity.java:741-743`。

当前数据中，`DAMAGES_HELMET` 包含下落铁砧、下落方块和下落钟乳石伤害：`data/minecraft/tags/damage_type/damages_helmet.json:1-5`。这里的头盔处理不是普通护甲公式的一部分，而是伤害类型触发的独立乘数；它发生在无敌帧比较之前、护甲公式之前。

当前 `DAMAGES_HELMET` 的完整成员和生成入口如下：

| `DamageType` | 伤害来源生成入口 |
| --- | --- |
| `minecraft:falling_anvil` | `AnvilBlock.getDamageSource` 调用 `DamageSources.fallingAnvil`：`net/minecraft/block/AnvilBlock.java:88-90` |
| `minecraft:falling_block` | 通用 `Falling.getDamageSource` 调用 `DamageSources.fallingBlock`：`net/minecraft/block/Falling.java:16-18`；实体落地时使用该来源：`net/minecraft/entity/FallingBlockEntity.java:271-274` |
| `minecraft:falling_stalactite` | `PointedDripstoneBlock.getDamageSource` 调用 `DamageSources.fallingStalactite`：`net/minecraft/block/PointedDripstoneBlock.java:264-266` |

这三项由 `VanillaDamageTypeTagProvider` 写入 `DamageTypeTags.DAMAGES_HELMET`：`net/minecraft/data/tag/vanilla/VanillaDamageTypeTagProvider.java:18`；运行时标签文件是 `data/minecraft/tags/damage_type/damages_helmet.json:1-6`。因此，普通近战、箭、火焰、爆炸等伤害类型不会因为佩戴头盔而进入这条 `0.75` 倍处理，除非数据包或模组把它们额外加入 `minecraft:damages_helmet` 标签。

它的完整触发路径是：

```text
具体伤害来源创建 DamageSource
  -> DamageSource 持有该伤害的 DamageType 注册表项
  -> LivingEntity.damage 调用 source.isIn(DamageTypeTags.DAMAGES_HELMET)
  -> DamageSource.isIn 转发到 DamageType 注册表项的标签查询
  -> 命中 minecraft:damages_helmet 且 HEAD 槽位非空
  -> damageHelmet(...) 损耗头部装备，并执行 amount *= 0.75
```

对应源码：`net/minecraft/entity/damage/DamageSource.java:15-18,132-134` 定义伤害类型并执行标签查询；`net/minecraft/registry/tag/DamageTypeTags.java:7-9,43-45` 定义 `minecraft:damages_helmet` 标签键；`net/minecraft/entity/LivingEntity.java:1186-1198` 执行实际判断和倍率；玩家的 `damageHelmet` 最终只将装备损耗范围限定为 `HEAD` 槽位：`net/minecraft/entity/player/PlayerEntity.java:741-743`。

Absorption 则是另一条路径：它表示额外生命值/黄色生命条，只在护甲、Resistance 和附魔处理之后从剩余伤害中扣除。`PlayerEntity.applyDamage` 先计算 `healthDamage = max(amount - absorption, 0)`，再减少 Absorption，最后才用剩余 `healthDamage` 扣除真实生命值：`net/minecraft/entity/player/PlayerEntity.java:745-765`。因此三者的区别是：

```text
Shield / BLOCKS_ATTACKS：主动格挡，护甲之前
头部装备 / DAMAGES_HELMET：特定伤害的 0.75 倍独立处理，护甲之前
Absorption：额外生命值，护甲和附魔之后、真实生命值之前
```

### 4.4 无敌帧和同一受击窗口的伤害合并

当 `timeUntilRegen > 10` 且伤害类型没有 `BYPASSES_COOLDOWN` 时：

- 如果本次 `amount <= lastDamageTaken`，本次伤害直接返回 `false`；
- 如果本次更大，只把 `amount - lastDamageTaken` 交给 `applyDamage`；
- 否则开启新的受击窗口，将 `timeUntilRegen` 设置为 20，并应用完整 `amount`。

证据：`net/minecraft/entity/LivingEntity.java:1204-1219`。

因此，无敌帧计算发生在**护甲减伤之前**：它比较的是护盾、冻结额外伤害和头盔倍率处理之后、尚未经过护甲公式的 `amount`，而不是最初传入的原始伤害值，也不是护甲减伤后的最终生命伤害。只有通过无敌帧判断后，`amount` 才会进入 `applyDamage`，继续执行护甲、Resistance、附魔和 Absorption 处理。

顺序可概括为：

```text
护盾阻挡
  -> 冻结额外伤害 / 头盔倍率
  -> 无敌帧比较与截取
  -> 护甲减伤
  -> Resistance / 附魔 / Absorption
  -> 扣除生命值
```

## 5. 护甲减伤

玩家覆盖了 `applyDamage`，但仍复用 `LivingEntity.applyArmorToDamage`：

- `net/minecraft/entity/player/PlayerEntity.java:745-748`

### 5.1 护甲值和护甲韧性来源

`getArmor()` 读取 `EntityAttributes.ARMOR` 并向下取整，护甲韧性直接读取 `EntityAttributes.ARMOR_TOUGHNESS`：

- `net/minecraft/entity/LivingEntity.java:1797-1799`
- `net/minecraft/entity/LivingEntity.java:1825-1828`

普通盔甲材料会为装备槽添加两个属性修饰符：该部件的防御值加入 `ARMOR`，材料 toughness 加入 `ARMOR_TOUGHNESS`：

- `net/minecraft/item/equipment/ArmorMaterial.java:15-38`

所以护甲不是根据“穿了几件”简单判断，而是先由当前装备属性汇总成实体属性，再进入伤害公式。

### 5.2 绕过护甲

若伤害类型属于 `BYPASSES_ARMOR`，`applyArmorToDamage` 不调用护甲公式，直接保留当前 `amount`；否则先损耗玩家四个盔甲槽的装备，再计算减伤：

- `net/minecraft/entity/LivingEntity.java:1807-1818`
- `net/minecraft/entity/LivingEntity.java:1825-1831`
- `net/minecraft/entity/player/PlayerEntity.java:737-739`

本仓库的 `bypasses_armor.json` 包含摔落、溺水、凋零、魔法、冰冻、声波尖啸等多种伤害类型，具体集合以数据文件为准：

- `data/minecraft/tags/damage_type/bypasses_armor.json:1-24`

装备耐久损耗与护甲减伤是两个并行结果：只要没有绕过护甲，装备会按伤害损耗，即使最终被护甲完全减为 0，装备也可能已经掉耐久。

### 5.3 护甲公式

`DamageUtil.getDamageLeft` 的源码公式可以写成：

```text
f = 2 + armorToughness / 4
g = clamp(armor - damage / f, armor * 0.2, 20)
baseArmorEffectiveness = g / 25
damageAfterArmor = damage * (1 - armorEffectiveness)
```

源码：`net/minecraft/entity/DamageUtil.java:17-38`。

其中：

- `damage` 是进入护甲阶段的伤害；
- `armor` 是 `getArmor()` 的整数护甲值；
- `armorToughness` 是实体当前的护甲韧性；
- `clamp` 的下限为 `armor * 0.2`，上限为 `20`；
- 伤害越高，`armor - damage / f` 越低，所以高额单次伤害会压低有效护甲；
- 护甲韧性越高，`f` 越大，高额伤害对有效护甲的压低越弱。

### 5.4 武器附魔对护甲公式的影响

如果 `DamageSource.getWeaponStack()` 能取得武器，服务端会把武器上的 `ARMOR_EFFECTIVENESS` 附魔效果应用到 `baseArmorEffectiveness`，然后将结果限制到 `[0, 1]`：

- `net/minecraft/entity/DamageUtil.java:23-34`
- `net/minecraft/enchantment/EnchantmentHelper.java:208-214`

例如 `Breach` 每级向护甲有效率加上 `-0.15`，即降低护甲的减伤比例：

- `data/minecraft/enchantment/breach.json:7-17`

这意味着 `Breach` 不是直接从最终伤害上减一个固定值，而是修改护甲公式算出的“护甲减伤率”。

## 6. 抗性效果与伤害保护附魔

护甲计算完成后，`modifyAppliedDamage` 继续处理状态效果和附魔：

- `net/minecraft/entity/LivingEntity.java:1834-1875`

### 6.1 Resistance

若伤害类型不属于 `BYPASSES_EFFECTS`，且目标拥有 Resistance，并且伤害不属于 `BYPASSES_RESISTANCE`，源码使用：

```text
resistancePercent = (amplifier + 1) * 5
damageAfterResistance = damage * (25 - resistancePercent) / 25
```

因此通常可理解为 Resistance I 减 20%、Resistance II 减 40%，以此类推；源码最后用 `max(..., 0)` 防止变成负数：

- `net/minecraft/entity/LivingEntity.java:1838-1847`
- `data/minecraft/tags/damage_type/bypasses_resistance.json:1-6`

### 6.2 Protection 类附魔

如果伤害已经不大于 0，直接返回 0；如果属于 `BYPASSES_ENCHANTMENTS`，跳过伤害保护附魔。否则 `EnchantmentHelper.getProtectionAmount` 遍历目标所有装备上的附魔，并汇总每个附魔的 `DAMAGE_PROTECTION` 效果：

- `net/minecraft/entity/LivingEntity.java:1854-1870`
- `net/minecraft/enchantment/EnchantmentHelper.java:184-190`
- `net/minecraft/enchantment/EnchantmentHelper.java:147-174`

最终附魔保护公式为：

```text
p = clamp(protection, 0, 20)
damageAfterEnchantments = damage * (1 - p / 25)
```

源码：`net/minecraft/entity/DamageUtil.java:40-43`。

当前数据中的典型附魔贡献包括：

- Protection：每级 `+1` 通用伤害保护，见 `data/minecraft/enchantment/protection.json:7-27`；
- Fire Protection：每级 `+2`，仅对 `is_fire`，见 `data/minecraft/enchantment/fire_protection.json:19-54`；
- Feather Falling：每级 `+3`，仅对摔落，见 `data/minecraft/enchantment/feather_falling.json:7-25`；
- Blast Protection：每级 `+2`，仅对爆炸，见 `data/minecraft/enchantment/blast_protection.json:19-54`；
- Projectile Protection：每级 `+2`，仅对投射物，见 `data/minecraft/enchantment/projectile_protection.json:7-42`。

`bypasses_effects` 是更早的总开关：`LivingEntity.modifyAppliedDamage` 一旦命中它，会直接跳过 Resistance 和伤害保护附魔；当前数据中 `starve` 使用该标签：

- `net/minecraft/entity/LivingEntity.java:1834-1837`
- `data/minecraft/tags/damage_type/bypasses_effects.json:1-5`

而 `bypasses_enchantments` 只跳过附魔保护阶段，当前数据中 `sonic_boom` 使用它：

- `net/minecraft/entity/LivingEntity.java:1854-1858`
- `data/minecraft/tags/damage_type/bypasses_enchantments.json:1-5`

## 7. 吸收量与最终扣血

`PlayerEntity.applyDamage` 在护甲、Resistance 和附魔之后处理 Absorption：

```text
damageBeforeAbsorption = amount
healthDamage = max(amount - absorptionAmount, 0)
absorbed = damageBeforeAbsorption - healthDamage
newAbsorption = oldAbsorption - absorbed
```

证据：`net/minecraft/entity/player/PlayerEntity.java:745-755`。

如果 `healthDamage != 0`，玩家会：

1. 增加 `source.getExhaustion()` 对应的饥饿消耗；
2. 将最终伤害写入 `DamageTracker`；
3. 执行 `health = health - healthDamage`；
4. 增加 `DAMAGE_TAKEN` 统计；
5. 发出 `ENTITY_DAMAGE` 游戏事件。

实现：`net/minecraft/entity/player/PlayerEntity.java:757-765`。

玩家的生命值由 `setHealth` 限制在 `[0, maxHealth]`：`net/minecraft/entity/LivingEntity.java:1161-1162`。

## 8. 伤害事件、生命值同步与受伤渲染

### 8.1 `DamageS2CPacket` 的当前对应物与发送时机

本版本中通常所说的 `DamageS2CPacket` 对应 `EntityDamageS2CPacket`，协议类型是 `damage_event`。它只携带受伤实体 ID、`DamageType` 以及攻击者/直接来源等信息，不携带最终伤害数值或新的生命值：

- 数据包定义：`net/minecraft/network/packet/s2c/play/EntityDamageS2CPacket.java:18-23`；
- 协议注册：`net/minecraft/network/packet/PlayPackets.java:211`；
- 服务端广播：`net/minecraft/server/world/ServerWorld.java:1166-1168`，通过 `sendToSelfAndListeners` 发给实体自己和附近跟踪者：`net/minecraft/server/world/ServerChunkLoadingManager.java:1301-1305`。

`LivingEntity.damage` 只有在本次伤害开启了新的受击窗口（`bl2 == true`）时才执行 `world.sendEntityDamage(this, source)`。因此，普通情况下是完成护盾、冻结、头盔等前置处理后，进入新的受击窗口时发送；即使后续护甲、附魔和吸收量把最终生命伤害减到 0，只要没有被护盾分支拦截，仍会发送该伤害事件包，因为发送位置在 `applyDamage` 之后但不以最终扣血量为条件：`net/minecraft/entity/LivingEntity.java:1204-1229`。

无敌帧（代码名是 damage cooldown）中的行为如下：

1. `timeUntilRegen > 10` 且伤害没有 `BYPASSES_COOLDOWN` 时，如果本次处理后的 `amount <= lastDamageTaken`，直接返回 `false`：不扣血，也不发送 `EntityDamageS2CPacket`。
2. 如果本次 `amount > lastDamageTaken`，只将差值交给 `applyDamage`，但把 `bl2` 设为 `false`：生命值可能减少，但不发送 `EntityDamageS2CPacket`，也不会重新设置 `hurtTime`。
3. 如果伤害带有 `BYPASSES_COOLDOWN`，会走新的受击窗口分支，重新设置 `timeUntilRegen = 20`、`hurtTime = 10`，并发送伤害事件包。

对应逻辑：`net/minecraft/entity/LivingEntity.java:1204-1219`；玩家服务端每 tick 递减 `timeUntilRegen`：`net/minecraft/server/network/ServerPlayerEntity.java:597-603`。

另外，护盾完全挡住伤害时会播放格挡声音而不发送 `EntityDamageS2CPacket`：`net/minecraft/entity/LivingEntity.java:1223-1229`。

### 8.2 生命值什么时候发送到客户端

生命值减少发生在伤害已经经过护甲、Resistance、附魔和 Absorption 之后，并且剩余 `amount != 0` 时：`PlayerEntity.applyDamage` 调用 `setHealth(this.getHealth() - amount)`：`net/minecraft/entity/player/PlayerEntity.java:745-765`。

这一步不会立即在 `applyDamage` 内单独发送生命值包。服务端玩家的 `playerTick` 会比较当前生命值和 `syncedHealth`；只要发现不同，就发送 `HealthUpdateS2CPacket`，同时更新同步基准：`net/minecraft/server/network/ServerPlayerEntity.java:665-702`。因此，伤害造成的生命值变化通常在同一服务端 tick 的玩家同步阶段发送，而不是由 `EntityDamageS2CPacket` 携带。

`HealthUpdateS2CPacket` 同时包含生命值、饥饿值和饱和度，所以它不只由受伤触发；如果只有 Absorption 被消耗、生命值没有变化，则不会因为“扣血”本身发送该包，但其他饥饿/饱和度变化仍可能触发它：`net/minecraft/network/packet/s2c/play/HealthUpdateS2CPacket.java:12-18`、`net/minecraft/server/network/ServerPlayerEntity.java:697-701`。

客户端收到该包后调用 `ClientPlayerEntity.updateHealth`：`net/minecraft/client/network/ClientPlayNetworkHandler.java:1152-1157`。如果新生命值低于旧值，客户端也会设置 `lastDamageTaken`、`timeUntilRegen`、`maxHurtTime = 10` 和 `hurtTime = 10`：`net/minecraft/client/network/ClientPlayerEntity.java:336-350`。

### 8.3 玩家变红由什么控制

玩家模型变红的直接控制量不是生命值，而是客户端实体的 `hurtTime`。其中，`EntityDamageS2CPacket` 是控制受击实体 `hurtTime` 的主要网络入口：客户端收到该包后调用 `entity.onDamaged(...)`，将 `hurtTime` 重置为 10：

1. 客户端收到 `EntityDamageS2CPacket` 后，`ClientPlayNetworkHandler.onEntityDamage` 找到实体并调用 `entity.onDamaged(...)`：`net/minecraft/client/network/ClientPlayNetworkHandler.java:1144-1149`。
2. `LivingEntity.onDamaged` 将 `maxHurtTime` 设为 10，并把 `hurtTime` 设为 10：`net/minecraft/entity/LivingEntity.java:1970-1982`。
3. 每 tick `hurtTime` 递减：`net/minecraft/entity/LivingEntity.java:430-432`。
4. `LivingEntityRenderer` 将 `hurtTime > 0` 转换成渲染状态 `state.hurt`：`net/minecraft/client/render/entity/LivingEntityRenderer.java:284-291`；随后 `getOverlay` 使用 `OverlayTexture.getV(state.hurt)` 选择受伤 overlay：`net/minecraft/client/render/entity/LivingEntityRenderer.java:141-143`，并在模型提交时应用：`net/minecraft/client/render/entity/LivingEntityRenderer.java:96-103`。

所以，新的受击窗口会让玩家变红约 10 tick；无敌帧中“只应用超过 `lastDamageTaken` 的差值”的那次伤害不会重新触发这套受伤动画。玩家自己的 `HealthUpdateS2CPacket` 在检测到生命值下降时也会设置 `hurtTime`，因此本地玩家还存在生命值同步触发的兜底路径，但渲染最终仍由 `hurtTime` 控制。

不要把 `DamageTiltS2CPacket` 和变红混为一谈：它对应 `hurt_animation`，客户端只调用 `entity.animateDamage(...)`，主要用于受击视角/方向动画，而不是红色 overlay：`net/minecraft/client/network/ClientPlayNetworkHandler.java:1039-1045`、`net/minecraft/network/packet/s2c/play/DamageTiltS2CPacket.java:11-16`。

## 9. 一个完整数值例子

假设：

- 玩家收到进入 `PlayerEntity.damage` 的伤害为 `20`；
- 伤害不绕过护甲、Resistance 或附魔；
- 难度为普通；
- 护盾没有阻挡；
- `armor = 20`，`armorToughness = 8`；
- 没有 Breach；
- 没有 Resistance；
- 装备四件 Protection IV，总保护值为 `16`；
- 玩家有 `2` 点 Absorption。

计算：

```text
f = 2 + 8 / 4 = 4
g = clamp(20 - 20 / 4, 20 * 0.2, 20) = 15
护甲有效率 = 15 / 25 = 0.60
护甲后伤害 = 20 * (1 - 0.60) = 8

附魔后伤害 = 8 * (1 - 16 / 25) = 2.88
吸收后生命伤害 = max(2.88 - 2, 0) = 0.88
```

所以本次攻击最终从玩家生命值扣除 `0.88` 点；`2` 点吸收量被消耗完，盔甲耐久则在护甲阶段按进入该阶段前的伤害执行损耗。

## 10. 容易混淆的点

1. **护盾不是护甲。** 护盾在 `LivingEntity.damage` 中先于护甲公式执行，且受方向、伤害类型和投射物穿透影响。
2. **护甲值不是固定常数。** 盔甲通过属性修饰符动态贡献 `ARMOR` 与 `ARMOR_TOUGHNESS`，其他装备或效果也可以改变属性。
3. **护甲减伤不是简单的 `armor / 25`。** 伤害值和护甲韧性会先改变有效护甲，再换算成减伤率。
4. **Protection 与 Breach 作用位置不同。** Protection 作用在护甲之后的最终伤害，Breach 修改护甲阶段的有效率。
5. **无敌帧会改变实际应用量。** 连续受击时，第二次伤害可能只应用超过 `lastDamageTaken` 的部分，甚至完全被拒绝。
6. **绕过标签是分层的。** `bypasses_armor`、`bypasses_resistance`、`bypasses_enchantments` 和 `bypasses_effects` 不是同义词，尤其 `bypasses_effects` 会提前跳过 Resistance 和附魔处理。
7. **本报告没有把伤害来源生成过程混入接收流程。** 例如近战攻击的攻击力、暴击和武器伤害附魔在调用目标的 `damage` 前计算；本报告从目标收到 `DamageSource + amount` 开始。

## 11. 总结

对玩家真正造成生命值损失的核心表达式可以概括为：

```text
进入玩家流程的 amount
  -> 玩家难度缩放
  -> 护盾扣除
  -> 冻结/头盔特殊处理
  -> 无敌帧截取
  -> 护甲公式（除非 bypasses_armor）
  -> Resistance（除非 bypasses_effects 或 bypasses_resistance）
  -> 伤害保护附魔（除非 bypasses_effects 或 bypasses_enchantments）
  -> Absorption
  -> health -= 剩余伤害
```

因此排查“为什么玩家受到的伤害和预期不同”时，应按这个顺序记录每一阶段的 `amount`，同时检查 `DamageSource` 的类型标签、玩家当前难度、护盾朝向、无敌帧、`ARMOR`/`ARMOR_TOUGHNESS` 属性、状态效果、装备附魔和吸收量。
