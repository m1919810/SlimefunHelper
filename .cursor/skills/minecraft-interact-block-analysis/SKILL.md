---
name: minecraft-interact-block-analysis
description: Analyze server-side block interaction flow around ServerPlayerInteractionManager.interactBlock, BlockState.onUseWithItem/onUse, ItemStack.useOnBlock, and BlockItem.useOnBlock in this merged Yarn Minecraft tree. Use when tracing whether right-clicking a block can fall through into item use or block placement, when classifying PASS vs PASS_TO_DEFAULT_BLOCK_ACTION behavior, or when summarizing special interactable block families.
disable-model-invocation: true
---

# Minecraft Interact Block Analysis

## Scope
- Primary path
  - `net/minecraft/server/network/ServerPlayerInteractionManager`
  - `net/minecraft/block/AbstractBlock`
  - `net/minecraft/item/ItemStack`
  - `net/minecraft/item/BlockItem`
  - `net/minecraft/item/ItemPlacementContext`
- Secondary path
  - `net/minecraft/block/**` overrides of `onUseWithItem` and `onUse`
  - `net/minecraft/block/cauldron/CauldronBehavior`

## Core question
判断“玩家对方块右键交互后，是否还会继续走到手里物品使用，进一步触发 `BlockItem.useOnBlock` 并造成放置”。

重点把结果分成三层：
1. 方块交互阶段是否被拦截
2. 是否继续进入物品对方块使用阶段
3. 手里物品是否真的是 `BlockItem`，以及放置上下文是否允许放置

## 主链路
1. 网络入口
   - `ServerPlayNetworkHandler.onPlayerInteractBlock`
   - 调用 `player.interactionManager.interactBlock(...)`
2. 服务器交互协调器
   - `ServerPlayerInteractionManager.interactBlock`
3. 方块优先交互
   - `blockState.onUseWithItem(...)`
   - 仅当返回 `PassToDefaultBlockAction` 且主手时，再调用 `blockState.onUse(...)`
4. 物品对方块交互
   - `stack.useOnBlock(new ItemUsageContext(...))`
5. 物品实现
   - 普通 `Item.useOnBlock` 默认 `PASS`
   - `BlockItem.useOnBlock` 调 `place(new ItemPlacementContext(context))`
6. 真正放置
   - `BlockItem.place(...)`
   - 经过 `context.canPlace()`、`getPlacementState()`、`canPlace()`、`world.setBlockState(...)`

## ActionResult 在这条链路里的含义
- `SUCCESS` / `SUCCESS_SERVER` / `CONSUME`
  - 都属于 accepted
  - 会在当前阶段截断，不再继续往后走
- `PASS_TO_DEFAULT_BLOCK_ACTION`
  - 不算 accepted
  - 在 `interactBlock` 里表示“可以继续尝试默认方块动作 `onUse`”
- `PASS`
  - 不算 accepted
  - 当前阶段不处理，允许继续往后
- `FAIL`
  - 不算 accepted
  - `interactBlock` 没有专门提前返回，后面仍可能继续走到 `stack.useOnBlock`
  - 但本树里方块交互更常用的是 `PASS` / `PASS_TO_DEFAULT_BLOCK_ACTION`

## interactBlock 的真实分流
按服务器顺序判断：

### 0. 前置失败
- 方块功能未启用
  - 直接 `FAIL`
- 旁观模式
  - 能开容器则 `CONSUME`
  - 否则 `PASS`

### 1. 是否跳过方块交互阶段
- `bl = 主手非空 或 副手非空`
- `bl2 = player.shouldCancelInteraction() && bl`
- `PlayerEntity.shouldCancelInteraction()` 默认就是潜行
- 结论
  - 玩家潜行且双手至少有一只手拿着东西时
  - 整个 `onUseWithItem` / `onUse` 阶段都会被跳过
  - 直接去尝试 `stack.useOnBlock`

### 2. 正常方块交互阶段
仅当 `!bl2`

#### 2.1 `blockState.onUseWithItem(...)`
- accepted
  - 直接结束
- `PASS_TO_DEFAULT_BLOCK_ACTION`
  - 若 `hand == MAIN_HAND`
  - 继续进 `blockState.onUse(...)`
- `PASS`
  - 不会进入 `onUse(...)`
  - 直接掉到后面的 `stack.useOnBlock(...)`

#### 2.2 `blockState.onUse(...)`
- 只有上一步返回 `PASS_TO_DEFAULT_BLOCK_ACTION` 且主手才会调用
- accepted
  - 直接结束
- 非 accepted，通常是 `PASS`
  - 继续进 `stack.useOnBlock(...)`

### 3. 物品对方块交互阶段
满足以下条件才会调用：
- `!stack.isEmpty()`
- 该物品没有冷却

否则 `interactBlock` 直接返回 `PASS`

## 什么时候会真的走到放置
必须同时满足：
1. `interactBlock` 前面的方块交互没有被 accepted 截断
2. `stack.useOnBlock(...)` 被调用
3. `stack.getItem()` 实际上是 `BlockItem` 或其放置型子类
4. `ItemPlacementContext.canPlace()` 为真
5. `getPlacementState(...)` 非空
6. `BlockItem.canPlace(...)` 为真
7. `world.setBlockState(...)` 成功

所以结论不是“进入 `stack.useOnBlock` 就一定放置”，而是“进入 `stack.useOnBlock` 后，若手里是可放置方块物品并且目标上下文允许，就可能放置”。

## 默认基线
来自 `AbstractBlock`
- `onUseWithItem` 默认 `PASS_TO_DEFAULT_BLOCK_ACTION`
- `onUse` 默认 `PASS`

因此一个完全没覆写交互逻辑的普通方块，在主手右键时的默认行为是：
- 先允许尝试默认方块动作
- 默认方块动作又什么都不做并返回 `PASS`
- 最终继续掉到 `stack.useOnBlock`
- 若手里是 `BlockItem`，就进入放置判定

## 判断模板
分析某个方块是否会在右键后继续放置，按这个顺序看：

1. 玩家是否潜行且手里拿着东西
   - 是：直接跳过方块交互，优先尝试物品使用/放置
2. 该方块是否覆写 `onUseWithItem`
   - 若返回 accepted，停止
   - 若返回 `PASS`，直接去物品使用
   - 若返回 `PASS_TO_DEFAULT_BLOCK_ACTION`，主手再看 `onUse`
3. `onUse` 是否 accepted
   - accepted，停止
   - `PASS`，继续去物品使用
4. 手里是否是 `BlockItem`
   - 不是：即便继续，也未必是放置
   - 是：再看放置上下文是否成立

## 特殊方块行为汇总
下面只关心“是否会把流程放给后面的 `stack.useOnBlock` / `BlockItem.useOnBlock`”。

### A. 默认可继续放置型
特征
- 没有覆写交互
- 或沿用 `onUseWithItem = PASS_TO_DEFAULT_BLOCK_ACTION`、`onUse = PASS`

结论
- 主手下通常会继续走到物品使用
- 若手里是方块物品，则容易进入放置路径

### B. 直接放行到物品阶段的方块
特征
- `onUseWithItem` 直接返回 `PASS`
- 不会进 `onUse`
- 直接尝试手里物品

典型例子
- `HangingSignBlock`
  - 当命中条件满足，尝试把 `HangingSignItem` 接到现有牌子结构上，先返回 `PASS`
- `WallHangingSignBlock`
  - 同上，命中特定侧面时放行
- `CandleCakeBlock`
  - 手里是 `FLINT_AND_STEEL` / `FIRE_CHARGE` 时返回 `PASS`
  - 让点火物品自己处理
- `RedstoneOreBlock`
  - 已点亮矿石自身先发光
  - 如果手里是 `BlockItem` 且 `new ItemPlacementContext(...).canPlace()` 为真，则返回 `PASS`
  - 允许直接覆盖到放置逻辑
- `CopperGolemStatueBlock`
  - 手里是斧头时返回 `PASS`
- `OxidizableCopperGolemStatueBlock`
  - 手里是蜂蜜脾时返回 `PASS`
  - 某些斧头场景不满足条件时也回落到 `PASS`
- `LecternBlock`
  - 空手主手时某些分支直接 `PASS`
- `ShelfBlock`
  - 打到无效槽位、空手失败等分支返回 `PASS`
- `ChiseledBookshelfBlock`
  - 打到无效槽位时 `PASS`
- `AbstractSignBlock`
  - 没拿到正确实体时 `PASS`
- `PistonExtensionBlock`
  - 客户端或存在活塞方块实体时 `PASS`
- `RespawnAnchorBlock.onUse`
  - 电量为 0 时 `PASS`
- `TrapdoorBlock.onUse` / `DoorBlock.onUse`
  - 不能手开时 `PASS`
- `RepeaterBlock.onUse` / `ComparatorBlock.onUse`
  - 玩家没有修改权限时 `PASS`
- `JukeboxBlock.onUse`
  - 没唱片时 `PASS`
- `StructureBlock` / `CommandBlock` / `JigsawBlock`
  - 权限不足或实体缺失时 `PASS`

### C. 先尝试默认方块动作，再决定是否继续放置
特征
- `onUseWithItem` 返回 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 主手再执行 `onUse`
- 只有 `onUse` 也没 accepted，才会继续到物品使用

典型例子
- `AbstractSignBlock`
  - 不能改字、被封蜡、有人正在编辑、修改文本失败时
  - 返回 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - 然后 `onUse` 再尝试命令点击/打开编辑
  - 若 `onUse` 最终 `PASS`，才继续到物品使用
- `FlowerPotBlock`
  - 手里不是可盆栽内容时，返回 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - `onUse` 会尝试取出当前盆栽；若这是空花盆则 `CONSUME`
- `LecternBlock`
  - 已有书时 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - 再由 `onUse` 负责读书界面
  - 没有书且手里不是 lectern book 时，主手大多走 `PASS_TO_DEFAULT_BLOCK_ACTION`
- `DecoratedPotBlock`
  - 当前物品无法继续往壶里塞时 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - `onUse` 会播放失败交互并 `SUCCESS`
  - 因此通常不会继续到放置
- `JukeboxBlock`
  - 有唱片时 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - `onUse` 负责吐出唱片，通常会截断
- `CakeBlock`
  - 非“给蛋糕插蜡烛”场景时 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - `onUse` 再尝试吃蛋糕
  - 如果玩家当前不能吃，`tryEat` 返回 `PASS`，于是会继续到物品使用
- `ChiseledBookshelfBlock`
  - 手里不是书类、槽位已占用时 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - `onUse` 再尝试从槽里取书
- `VaultBlock`
  - 非激活态、空手或不满足解锁尝试时，`PASS_TO_DEFAULT_BLOCK_ACTION`
- `CauldronBehavior`
  - 默认行为表的缺省返回就是 `PASS_TO_DEFAULT_BLOCK_ACTION`
  - 也就是“该物品不是这个锅当前支持的交互物品时，交给后面的默认方块动作/物品流程”

### D. 会主动截断，不让后面放置的方块
特征
- 在命中主逻辑时返回 accepted
- 即 `SUCCESS` / `SUCCESS_SERVER` / `CONSUME`

典型例子
- `CampfireBlock`
  - 可烹饪物品时 `SUCCESS_SERVER` / `CONSUME`
- `CandleBlock`
  - 空手灭蜡烛时 `SUCCESS`
- `SweetBerryBushBlock`
  - 成熟采摘时 `SUCCESS`
- `BeehiveBlock`
  - 满蜜且剪刀/玻璃瓶成功时 `SUCCESS`
- `PumpkinBlock`
  - 剪刀雕刻时 `SUCCESS`
- `CakeBlock`
  - 插蜡烛成功时 `SUCCESS`
  - 能吃时 `onUse` 也会 `SUCCESS`
- `FlowerPotBlock`
  - 空花盆插植物成功时 `SUCCESS`
  - 非空花盆上手持可盆栽物时 `CONSUME`
- `ComposterBlock`
  - 可堆肥物命中时多为 `SUCCESS`
- `DecoratedPotBlock`
  - 可插入物品时 `SUCCESS`
  - 空手/失败反馈的默认点击也会 `SUCCESS`
- `JukeboxBlock`
  - 成功放唱片、成功吐唱片时 accepted
- `RespawnAnchorBlock`
  - 成功充能、设重生点、爆炸、客户端消费时 accepted
- `DoorBlock` / `TrapdoorBlock`
  - 可手开时 `SUCCESS`
- `RepeaterBlock` / `ComparatorBlock`
  - 有修改权限时 `SUCCESS`
- `NoteBlock`
  - 常规调音 `SUCCESS`
- `RedstoneWireBlock`
  - 有修改权限时切换形态并 `SUCCESS`

## 关于 Cauldron 的单独判断
`AbstractCauldronBlock.onUseWithItem` 不自己写分支，而是委托给 `CauldronBehaviorMap`。

关键点
- 行为表默认值就是 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 所以“这个锅不认识当前物品”时，天然会继续向后流
- 但大量桶、瓶子、染色皮革、旗帜、潜影盒清洗等命中时会直接 `SUCCESS`
- 因此锅类不是稳定可放置，也不是稳定拦截，取决于当前手里物品是否命中行为表

## 需要特别记住的高频结论

### 1. 潜行会显著提高“直接放置”的概率
因为潜行且手里有物品时，`interactBlock` 会直接跳过：
- `onUseWithItem`
- `onUse`

直接进入 `stack.useOnBlock`

### 2. `PASS` 和 `PASS_TO_DEFAULT_BLOCK_ACTION` 不一样
- `PASS`
  - 直接把控制权交给后面的物品使用
  - 不再调 `onUse`
- `PASS_TO_DEFAULT_BLOCK_ACTION`
  - 只是在主手里把控制权先交给 `onUse`
  - 只有 `onUse` 也没处理，才继续到物品使用

### 3. 很多“看起来能交互”的方块并不会阻止放置
只要它们在当前条件下返回了 `PASS`，或者 `PASS_TO_DEFAULT_BLOCK_ACTION` 后 `onUse` 又返回 `PASS`，最终仍会继续尝试放置。

### 4. `BlockItem.useOnBlock` 只是放置入口，不保证成功
还要看：
- 命中的块能否被替换
- 相邻目标位是否可放置
- 方块自己的 `getPlacementState`
- `world.canPlace`
- `state.canPlaceAt`

## 推荐输出结构
分析具体方块时，按下面格式输出：
- 交互入口
- `onUseWithItem` 返回分流
- 是否会进入 `onUse`
- `onUse` 返回分流
- 是否会进入 `stack.useOnBlock`
- 若手持 `BlockItem`，是否可能落到 `BlockItem.useOnBlock`
- 阻止放置的条件
- 放行到放置的条件

## 快速结论模板
- “这个方块在非潜行主手右键时，会先经过方块自己的物品交互；只有前面没 accepted，才会继续尝试放置。”
- “这个方块当前分支返回 `PASS`，因此不会走默认方块动作，直接进入手里物品逻辑。”
- “这个方块当前分支返回 `PASS_TO_DEFAULT_BLOCK_ACTION`，因此主手还会再跑一次 `onUse`；若 `onUse` 也没处理，才会继续尝试放置。”
- “这里确实能触发 `BlockItem.useOnBlock`，但是否真正放置还取决于 `ItemPlacementContext.canPlace()` 和后续放置校验。”

## 汇总统计

### 覆写规模
- 覆写 `onUseWithItem` 的方块/基类：26 个
- 覆写 `onUse` 的方块/基类：53 个
- 与“是否继续掉到 `stack.useOnBlock` / `BlockItem.useOnBlock`”关系最直接的重点对象：34 个

### 主导行为分组
- 直接放行到后续物品逻辑的重点对象：20 个
- 先进入默认方块动作，再决定是否继续放置的重点对象：9 个
- 常见命中时会主动截断交互的重点对象：14 个

说明
- 这些分组不是互斥分类
- 同一个方块可能同时拥有 `PASS`、`PASS_TO_DEFAULT_BLOCK_ACTION`、`SUCCESS` 三类分支
- 更适合按“主导交互模式”和“条件触发”理解

## 重点对象逐项摘要

### `AbstractBlock`
- 默认基线
- `onUseWithItem = PASS_TO_DEFAULT_BLOCK_ACTION`
- `onUse = PASS`
- 没有特殊覆写的普通方块，主手右键最终通常会继续掉到 `stack.useOnBlock`

### `AbstractSignBlock`
- 改字成功时直接拦截
- 改字条件不成立时先返回 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 再由 `onUse` 处理命令点击、编辑、封蜡反馈
- `onUse` 也处理不了时，才会继续往后掉

### `HangingSignBlock`
- 命中允许挂接条件时返回 `PASS`
- 让 `HangingSignItem` 自己接管后续放置/挂接逻辑

### `WallHangingSignBlock`
- 命中允许挂接条件时返回 `PASS`
- 行为和 `HangingSignBlock` 同型

### `LecternBlock`
- 已有书时返回 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 后续 `onUse` 再处理阅读界面
- 手里是可接受书物时会直接放书并拦截
- 某些空手主手分支会直接 `PASS`

### `ShelfBlock`
- 命中有效槽位时会交换物品并拦截
- 打到无效槽位、空手失败等分支返回 `PASS`
- 只有无效命中更容易继续掉到后续物品逻辑

### `ChiseledBookshelfBlock`
- 手里是书且命中空槽时插书并拦截
- 手里不是书、槽位已占用时先 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 打到无效槽位时直接 `PASS`
- 后续 `onUse` 可继续尝试取书

### `FlowerPotBlock`
- 空花盆 + 可盆栽物：插入并拦截
- 手里不是可盆栽物：`PASS_TO_DEFAULT_BLOCK_ACTION`
- `onUse` 再尝试取出当前盆栽
- 空花盆本体点击会 `CONSUME`

### `DecoratedPotBlock`
- 能往壶里塞物时直接拦截
- 塞不进去时先 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 后续 `onUse` 会播放失败反馈并 `SUCCESS`
- 实际上多数情况下不会继续掉到放置

### `JukeboxBlock`
- 空机时尝试放唱片
- 有唱片时先 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 再由 `onUse` 负责吐出唱片
- 没唱片时 `onUse = PASS`

### `CakeBlock`
- 插蜡烛成功时拦截
- 其他物品先 `PASS_TO_DEFAULT_BLOCK_ACTION`
- `onUse` 再尝试吃蛋糕
- 如果玩家当前不能吃，`tryEat = PASS`，于是会继续到后续物品逻辑

### `CampfireBlock`
- 可烹饪输入时 `SUCCESS_SERVER` / `CONSUME`
- 不可烹饪时才回落到默认后续流程

### `CandleBlock`
- 空手灭蜡烛时直接拦截
- 非空手场景不一定拦截，继续沿父类逻辑分流

### `CandleCakeBlock`
- 手里是 `FLINT_AND_STEEL` / `FIRE_CHARGE` 时直接 `PASS`
- 对点火物主动放行
- 空手点中蜡烛顶部且已点燃时会灭火并拦截

### `BeehiveBlock`
- 满蜜且剪刀/玻璃瓶命中时拦截
- 非采蜜场景才可能继续走后续物品逻辑

### `SweetBerryBushBlock`
- 成熟采摘时拦截
- 未成熟且手里是骨粉时 `PASS`
- 骨粉场景会主动放行给物品逻辑

### `PumpkinBlock`
- 剪刀雕刻时拦截
- 非剪刀场景本身不主动占住流程

### `RedstoneOreBlock`
- 先点亮矿石
- 若手里是 `BlockItem` 且当前位置允许放置，则返回 `PASS`
- 否则返回 `SUCCESS`
- 这是最明确的“允许继续尝试放置”的方块之一

### `CopperGolemStatueBlock`
- 手里是斧头时 `PASS`
- 其他物品通常切换姿态并拦截

### `OxidizableCopperGolemStatueBlock`
- 手里是蜂蜜脾时 `PASS`
- 斧头分支还要看氧化阶段与能否恢复实体
- 某些不满足恢复条件的斧头场景也会回落到 `PASS`

### `AbstractCauldronBlock` / `CauldronBehavior`
- 自身不写死结果，委托给行为表
- 行为表默认返回 `PASS_TO_DEFAULT_BLOCK_ACTION`
- 能装/倒/清洗时拦截
- 当前物品不在行为表里时，会天然放给后续流程

### `VaultBlock`
- 只有激活态且手里有物品时才尝试解锁
- 其他情况大多 `PASS_TO_DEFAULT_BLOCK_ACTION`

### `RespawnAnchorBlock`
- 可充能时拦截
- 主手当前物不合适，但副手拿着可充能物时，主手分支会 `PASS`
- `onUse` 中，电量为 0 时也会 `PASS`
- 设重生点、爆炸、客户端消费等场景都会拦截

### `TrapdoorBlock`
- 可手开时拦截
- 不能手开时 `PASS`
- 铁陷阱门这类更容易把控制权让给后续物品逻辑

### `DoorBlock`
- 可手开时拦截
- 不能手开时 `PASS`
- 铁门这类更容易放行到后续物品逻辑

### `RepeaterBlock`
- 有修改权限时拦截
- 没修改权限时 `PASS`

### `ComparatorBlock`
- 有修改权限时拦截
- 没修改权限时 `PASS`

### `NoteBlock`
- 顶面特定乐器输入时 `PASS`
- 常规调音时拦截

### `RedstoneWireBlock`
- 有修改权限时切换形态并拦截
- 没修改权限时 `PASS`

### `PistonExtensionBlock`
- 特定异常状态下会清理自己并 `CONSUME`
- 其他情况通常 `PASS`

### `StructureBlock`
- 能开界面时拦截
- 否则 `PASS`

### `CommandBlock`
- 有足够权限时开界面并拦截
- 否则 `PASS`

### `JigsawBlock`
- 有足够权限时开界面并拦截
- 否则 `PASS`

### `ComposterBlock`
- 可堆肥物命中时大多拦截
- 非正确物品场景才更可能继续走后续物品逻辑

### `TntBlock`
- 打火石/火焰弹点燃成功时拦截
- 特殊异常条件下，例如 TNT 爆炸规则关闭时，存在 `PASS` 分支

## 高频名单

### 最容易继续触发后续放置尝试的对象
- `RedstoneOreBlock`
- `HangingSignBlock`
- `WallHangingSignBlock`
- `CandleCakeBlock`
- `TrapdoorBlock` / `DoorBlock` 的不能手开版本
- `RepeaterBlock` / `ComparatorBlock` / `RedstoneWireBlock` 的无修改权限场景
- `RespawnAnchorBlock` 的零电量或副手充能分支
- `LecternBlock`
- `ChiseledBookshelfBlock`
- `AbstractCauldronBlock` 家族里当前物品不命中行为表的场景

### 最稳定拦截、不容易继续掉到放置的对象
- `CampfireBlock`
- `BeehiveBlock`
- `PumpkinBlock`
- `DecoratedPotBlock`
- `ComposterBlock`
- `NoteBlock`
- `CakeBlock` 的可食用/插蜡烛分支
- `JukeboxBlock` 的已有唱片分支

### 总开关条件
- 潜行且手里拿着东西时，`interactBlock` 会直接跳过 `onUseWithItem` 和 `onUse`
- 这会显著提高继续走到 `stack.useOnBlock`，进而触发 `BlockItem.useOnBlock` 的概率
- 也就是“潜行覆盖方块交互，优先尝试放置”的根因之一