---
name: minecraft-interact-desync
description: Analyze Minecraft interaction desync cases in this merged Yarn tree. Use when the user asks which block or item interactions are decided server-side, require explicit server-to-client synchronization, or can leave the client unaware of the true post-interaction state until a packet or block update arrives.
disable-model-invocation: true
---

# Minecraft Interact Desync

## 目标
统计“交互结果只在服务端计算，客户端必须等同步才能知道真实状态”的场景。

重点判断：
- 客户端是否只能看到交互前/预测态
- 真正状态是否由 `!world.isClient()` 分支决定
- 是否依赖 `world.setBlockState(...)`、block entity 同步、custom packet、`syncWorldEvent` 或实体数据包回传

## 先看什么
优先从这些信号入手：
- `if (!world.isClient())`
- `if (!world.isClient() && ...)`
- `world.setBlockState(..., 3/11/...)`
- `world.updateListeners(...)`
- `world.syncWorldEvent(...)`
- `world.emitGameEvent(...)`
- `ServerPlayerEntity` / `Criteria` / stats / inventory exchange
- block entity `markDirty()` + `toUpdatePacket()` / `createNbt()`
- 客户端方法里只做音效、动画、预览、假设性返回

## 判定原则
满足下面任一条，就要当作 desync 候选：
1. 客户端没有足够信息独立算出最终结果
2. 交互后状态只在服务端修改
3. 客户端先看到操作反馈，但真实 state 要等同步包
4. 交互结果依赖随机、权限、邻居状态、容器内容、block entity 内容、服务端持久数据
5. 逻辑本身通过 `!world.isClient()` 把关键分支锁在服务端

## 不要误判成 desync 的情况
以下情况要单独标记为“弱 desync”或“非重点”：
1. 客户端和服务端都会执行同样的确定性 `setBlockState(...)`
   - 只是正常双端执行，不代表客户端缺真相
2. 只有音效、粒子、统计、advancement 在服务端/客户端分边执行
   - 如果 block state、block entity、inventory 没有分叉，不应夸大成核心 desync
3. 客户端只缺一帧视觉确认，但不缺最终语义
   - 这更像同步时序，不是分析重点
4. 单纯 `world.playSound(...)` 或 `emitGameEvent(...)`
   - 没有实际状态变更时，只算反馈层，不算状态 desync
5. 纯界面打开但不涉及隐藏状态判断
   - 这类通常只算 server-authoritative UI，不一定是你要的“交互后状态不同步”

## 输出时分三层
### 1. 客户端可见层
客户端立即能看到什么：
- 动画
- 音效
- 手上物品的本地交换
- 预测性结果

### 2. 服务端裁决层
真正的状态改变在哪里发生：
- block state 改变
- block entity 内容改变
- 物品栈交换
- 掉落物 / 生成实体
- 统计与 advancement

### 3. 同步回传层
客户端何时拿到真实结果：
- block update packet
- chunk re-send
- block entity update packet
- inventory sync
- entity status / metadata
- world event 仅是视觉，不一定补全语义

## 典型分类
### A. 直接 block state desync
特征：交互只在服务端改方块状态，客户端本地没有完整判断依据。

已确认的典型家族：
- 流体写入类
  - `Waterloggable.tryFillWithFluid`
  - 代表“水桶对已有方块做含水化”
  - 例如各种可 `WATERLOGGED` 的方块，包括你关心的“箱子被灌成含水态”这一类
- 锅状态切换类
  - `CauldronBehavior`
  - 空锅、水锅、岩浆锅、细雪锅之间切换
  - level 变化也在这里
- 讲台类
  - `LecternBlock.setHasBook` / `setPowered`
  - 切 `HAS_BOOK`、`POWERED`
- 堆肥桶类
  - `ComposterBlock.addToComposter` / `emptyComposter`
  - 切 `LEVEL`
- 日光传感器类
  - `DaylightDetectorBlock.onUse`
  - 切 `INVERTED`
- 音符盒类
  - `NoteBlock.onUse`
  - 切 `NOTE`
- 门控开关类
  - `DoorBlock.onUse`
  - `TrapdoorBlock.onUse`
  - `FenceGateBlock.onUse`
  - `LeverBlock.onUse`
  - `ButtonBlock.onUse`
  - 这类都通过 `world.setBlockState(...)` 切 `OPEN`、`POWERED`、`FACING` 等状态
- 工具改块态类
  - `AxeItem.useOnBlock`
    - 去皮、刮铜锈、去蜡
  - `HoneycombItem.useOnBlock`
    - 给铜块、铜门、铜活板门、铜箱子、铜雕像、铜灯、铜栏杆等上蜡
  - `HoeItem.useOnBlock`
    - 泥土转耕地、粗泥转泥土、树根泥转普通泥土
  - `FlintAndSteelItem.useOnBlock`
    - 点燃营火、蜡烛、蛋糕蜡烛，或直接放火
- 其他右键切状态类
  - `RepeaterBlock`
  - `ComparatorBlock`
  - `CandleBlock`
  - `CandleCakeBlock`
  - `JukeboxBlock`
  - `DecoratedPotBlock`
  - `FlowerPotBlock`
  - `RespawnAnchorBlock`
  - `LightBlock`
  - `RedstoneOreBlock`

### B. block entity desync
特征：真正信息在 block entity 里，客户端只能等更新包。

已确认的典型家族：
- 讲台类
  - `LecternBlockEntity`
  - 书本内容、页码、比较器输出来源都不只是裸 block state
- 容器类
  - 箱子、木桶、潜影盒、漏斗、发射器、投掷器、熔炉系
  - 打开容器、交换容器内容、锁定状态检查都不是客户端单机真相
- 蜂巢与钟类
  - `BeehiveBlockEntity`
  - `BellBlockEntity`
- 信标类
  - `BeaconBlockEntity`
- 命令/结构/拼图类
  - `CommandBlock`
  - `StructureBlock`
  - `JigsawBlock`
- 告示牌文本类
  - `AbstractSignBlock` / `SignBlockEntity`
  - 文本可编辑、是否封蜡、是否允许修改，都是实体态+权限态
- 刷怪笼/试炼/保险库类
  - `VaultBlock`
  - 各类生成器和试炼方块的交互条件通常不止 block state

### C. item-stack exchange desync
特征：右键后手上物品被服务器交换，客户端只是同步结果。

已确认的典型家族：
- 桶类
  - `BucketItem`
  - 空桶取液体、装液体桶倒出、与 `FluidDrainable` / `FluidFillable` 交互
- 锅和瓶类
  - `CauldronBehavior`
  - 水瓶、玻璃瓶、桶、岩浆桶、细雪桶、清洗染色物品、清洗旗帜、清洗潜影盒
  - `PotionItem`
  - `GlassBottleItem`
- 牵引类
  - `LeadItem`
  - 栓绳连接围栏的最终结果由服务端决定
- 书和容器操作类
  - `LecternBlock.putBookIfAbsent`
  - 放书成功后手持书减少是在服务端定稿
- 点火与工具耐久类
  - `FlintAndSteelItem`
  - `AxeItem`
  - `HoeItem`
  - 工具耐久扣减与真实成功结果一起由服务端定稿

### D. 随机/条件分支 desync
特征：客户端即使知道交互对象，也无法独立推出结果。

已确认的典型家族：
- 随机型
  - `ComposterBlock`
  - 是否升层带随机性
- 权限型
  - `CommandBlock`
  - `StructureBlock`
  - `JigsawBlock`
  - `LecternBlock` 打开界面或注入数据等场景
- 邻居/环境依赖型
  - 是否能含水
  - 是否能灌锅
  - 是否能真正放火
  - 是否能转成某种放置态
  - 是否允许门/栅栏门/活板门按当前环境翻转
- 实体态依赖型
  - 蜂巢是否满蜜
  - 讲台是否已有书
  - 告示牌是否封蜡、是否有人正在编辑
  - 双箱、铜箱这类是否联动另一半状态
- 方向/命中面依赖型
  - `FenceGateBlock` 可能改 `FACING`
  - `WallHangingSignBlock` / `HangingSignBlock`
  - `FlowerPotBlock`
  - `DecoratedPotBlock`

### E. 客户端先给反馈，服务端再定真相
特征：客户端可能先看到成功手感，但这不等于它已经知道真实结果。

已确认的典型模式：
- `LeverBlock.onUse`
  - 客户端分支先做粒子反馈，真正切 `POWERED` 在另一侧
- 大量交互先 `return ActionResult.SUCCESS`
  - 但真实状态、物品、实体变更仍要等同步
- 容器打开、书本放入、红石状态切换
  - 客户端即时只拿到“动作已接受”，不是完整结果

### F. 纯视觉回声，不等于完整同步
特征：客户端可能先收到音效/粒子/事件，但这不代表它已经拿到完整语义状态。

已确认的典型信号：
- `world.syncWorldEvent(...)`
- `world.playSound(...)`
- `world.emitGameEvent(...)`

解释规则：
- 这些通常只说明“服务端承认发生了一件事”
- 不等于客户端已经知道最终 block state、block entity 内容或真实物品栈
- 做统计时不能把 `syncWorldEvent` 当成完整状态同步本身

## 典型案例写法
每个案例都按这个格式输出：

- 交互对象：方块 / 物品 / 实体
- 先决条件：客户端是否能自洽判断
- 服务端修改：具体改了什么
- 客户端缺口：客户端缺少哪份状态
- 同步方式：block update / block entity packet / item sync / entity metadata / world event
- 结论：是否会出现 desync，属于哪一层

## 示例
### 水桶灌入可进水方块
- 交互对象：`BucketItem` + `FluidFillable` / `Waterloggable`
- 服务端修改：`!world.isClient()` 后写入 `WATERLOGGED = true`
- 客户端缺口：客户端无法独立确认最终是否可灌入、是否被别的状态拦截
- 同步方式：方块状态同步
- 结论：典型 desync

### 讲台放书
- 交互对象：`LecternBlock`
- 服务端修改：设置 block entity 书内容并切 `HAS_BOOK`
- 客户端缺口：书是否真的放入、讲台是否已有书
- 同步方式：block state + block entity 同步
- 结论：典型 desync

### 堆肥桶投放
- 交互对象：`ComposterBlock`
- 服务端修改：随机决定是否升 level
- 客户端缺口：客户端不知道这次是否成功，也不知道最终 level
- 同步方式：block state + world event
- 结论：典型 desync

### 水桶/药水/玻璃瓶/火药雪桶
- 交互对象：`BucketItem` / `CauldronBehavior`
- 服务端修改：改 cauldron state 和手持物品
- 客户端缺口：当前 cauldron level、返回物品、是否成功
- 同步方式：方块状态 + 物品栈同步
- 结论：典型 desync

## 需要重点扫的代码区域
- `net/minecraft/block/**`
- `net/minecraft/block/entity/**`
- `net/minecraft/item/**`
- `net/minecraft/server/network/**`
- `net/minecraft/world/**` 中的更新与同步入口

## 实际统计流程
### 第一步：锁定交互入口
优先从这几类入口开始：
- `BlockState.onUseWithItem`
- `BlockState.onUse`
- `Item.useOnBlock`
- `Item.use`
- `BucketItem.placeFluid` / `tryDrainFluid`
- `CauldronBehavior.interact`

### 第二步：找服务端裁决点
看是否存在：
- `!world.isClient()` 包裹真正修改
- `world.setBlockState(...)`
- block entity 写入
- `player.setStackInHand(...)`
- `stack.decrement...`
- `world.spawnEntity(...)`

### 第三步：判断客户端是否缺信息
重点看客户端是否缺这些：
- 当前 block state 的隐藏分支
- block entity 内部内容
- 物品返回结果
- 成功/失败是否受随机或权限控制
- 是否受环境、邻居、液体、朝向、容器内容影响

### 第四步：标记同步通道
把同步方式归类为：
- block state update
- block entity update
- inventory / hand stack sync
- entity spawn / metadata / status
- world event only

### 第五步：输出 desync 结论
必须明确说清：
- 客户端在交互瞬间不知道什么
- 最终哪一层状态是服务端定的
- 靠什么同步回来
- 有没有“只同步视觉，不同步完整语义”的风险

## 已确认的源码锚点
- `Waterloggable.tryFillWithFluid`
  - `!world.isClient()` 内写 `WATERLOGGED = true`
- `BucketItem.use`
  - 通过 `FluidFillable` / `FluidDrainable` 进入服务端流体修改
- `CauldronBehavior.fillCauldron` / `emptyCauldron`
  - 服务端改锅状态并交换手持物
- `LecternBlock.putBookIfAbsent`
  - 服务端写讲台书并切 `HAS_BOOK`
- `ComposterBlock.addToComposter`
  - 服务端随机决定是否升层
- `DaylightDetectorBlock.onUse`
  - 服务端切 `INVERTED`

## 对象级索引
下面这个索引不是“全量穷举”，而是后续分析时优先覆盖的高价值对象表。

### 1. 强 block state desync 对象
- `Waterloggable` 家族
  - 代表对象：`ChestBlock`、`TrapdoorBlock`、`DecoratedPotBlock` 等可含水方块
  - 交互入口：`BucketItem.use` -> `FluidFillable` / `Waterloggable.tryFillWithFluid`
  - 服务端裁决：`WATERLOGGED` 真正写入在服务端分支
  - 同步类型：block state update
  - 强度：强

- `AbstractCauldronBlock` / `CauldronBehavior`
  - 交互入口：方块交互 + 桶/瓶/药水类物品
  - 服务端裁决：锅种类和 `LEVEL`、手持物交换
  - 同步类型：block state + inventory sync
  - 强度：强

- `ComposterBlock`
  - 交互入口：`onUseWithItem` / `onUse`
  - 服务端裁决：`LEVEL` 增减、是否成功受随机影响
  - 同步类型：block state + world event
  - 强度：强

- `DaylightDetectorBlock`
  - 交互入口：`onUse`
  - 服务端裁决：切 `INVERTED`
  - 同步类型：block state update
  - 强度：中

- `NoteBlock`
  - 交互入口：`onUse`
  - 服务端裁决：切 `NOTE`，并通过同步事件播放结果
  - 同步类型：block state + synced block event
  - 强度：中

- `DoorBlock` / `TrapdoorBlock` / `FenceGateBlock`
  - 交互入口：`onUse`
  - 服务端裁决：切 `OPEN`、`POWERED`、某些情况下改 `FACING`
  - 同步类型：block state update
  - 强度：中

- `LeverBlock` / `ButtonBlock`
  - 交互入口：`onUse`
  - 服务端裁决：切 `POWERED`，安排 tick，更新邻居
  - 同步类型：block state update
  - 强度：中
  - 备注：`LeverBlock` 明显存在“客户端先粒子反馈，服务端再定真相”的模式

- `FlintAndSteelItem` 关联对象
  - 代表对象：`CampfireBlock`、`CandleBlock`、`CandleCakeBlock`、火焰放置位
  - 交互入口：`FlintAndSteelItem.useOnBlock`
  - 服务端裁决：`LIT` 写入或火焰放置、耐久消耗
  - 同步类型：block state + inventory sync
  - 强度：中

- `HoeItem` / `ShovelItem` / `AxeItem` / `HoneycombItem` 关联对象
  - 代表对象：土类、营火、原木、铜块、铜门、铜活板门、铜箱子、铜雕像等
  - 交互入口：各工具 `useOnBlock`
  - 服务端裁决：目标方块替换/去蜡/去锈/压路径，外加耐久或物品减少
  - 同步类型：block state + inventory sync + world event
  - 强度：中

### 2. 强 block entity desync 对象
- `LecternBlock` / `LecternBlockEntity`
  - 交互入口：`onUseWithItem`、`onUse`
  - 服务端裁决：书内容、页码、`HAS_BOOK`、`POWERED`
  - 同步类型：block state + block entity + inventory sync
  - 强度：强

- `ChestBlock` / `ChestBlockEntity`
  - 交互入口：容器打开、含水化、双箱联动
  - 服务端裁决：容器内容、双箱合并态、锁定检查
  - 同步类型：block state + block entity + screen/inventory sync
  - 强度：强

- `BarrelBlock` / `BarrelBlockEntity`
  - 交互入口：`onUse`
  - 服务端裁决：打开容器、内容读写、比较器输出
  - 同步类型：block entity + screen/inventory sync
  - 强度：强

- `JukeboxBlock` / `JukeboxBlockEntity`
  - 交互入口：`onUseWithItem`、`onUse`
  - 服务端裁决：唱片插入/弹出、播放状态、比较器输出
  - 同步类型：block state + block entity
  - 强度：强

- `DecoratedPotBlock` / `DecoratedPotBlockEntity`
  - 交互入口：`onUseWithItem`、`onUse`
  - 服务端裁决：内部堆叠物、摇晃类型、物品减少
  - 同步类型：block entity + inventory-like item sync + particles/events
  - 强度：强
  - 备注：客户端分支先直接 `SUCCESS`，但真实内容更新在服务端

- `BeehiveBlock` / `BeehiveBlockEntity`
  - 交互入口：取蜜、剪蜜脾、破坏后放蜂、愤怒状态
  - 服务端裁决：蜂数量、蜜量、是否放蜂、是否激怒
  - 同步类型：block state + block entity + entity spawn/state
  - 强度：强

- `BeaconBlock` / `BeaconBlockEntity`
  - 交互入口：打开、效果配置
  - 服务端裁决：层级、主副效果
  - 同步类型：block entity + screen sync
  - 强度：强

- `CommandBlock` / `StructureBlock` / `JigsawBlock`
  - 交互入口：`onUse`
  - 服务端裁决：权限检查 + block entity 配置态
  - 同步类型：block entity + screen sync
  - 强度：强

### 3. 强 item exchange desync 对象
- `BucketItem`
  - 交互入口：`use`
  - 服务端裁决：取液/放液是否成功、目标是否变含水、返回桶类型
  - 同步类型：block state + inventory sync
  - 强度：强

- `GlassBottleItem`
  - 交互入口：`use`
  - 服务端裁决：取水瓶/龙息瓶后的新物品栈
  - 同步类型：inventory sync
  - 强度：中

- `PotionItem`
  - 交互入口：`useOnBlock`
  - 服务端裁决：与锅等对象交互后的返回物和状态变化
  - 同步类型：inventory sync + block state
  - 强度：中

- `BoneMealItem`
  - 交互入口：`useOnBlock`
  - 服务端裁决：是否成功生长、实际长成什么、数量扣减
  - 同步类型：block state / block entity / world event / inventory sync
  - 强度：强
  - 备注：水下骨粉尤其明显，因为生成结果与随机、可放置条件相关

- `LeadItem`
  - 交互入口：`useOnBlock`
  - 服务端裁决：是否真的把被牵引实体绑定到栅栏
  - 同步类型：entity state + inventory sync
  - 强度：中

- `AxeItem` / `HoeItem` / `ShovelItem` / `FlintAndSteelItem` / `HoneycombItem`
  - 交互入口：`useOnBlock`
  - 服务端裁决：是否成功、结果块态、耐久/物品扣减
  - 同步类型：block state + inventory sync
  - 强度：中

### 4. 优先怀疑为 desync 的对象特征
满足越多，越应该放进结果：
- 有 `onUseWithItem` 且返回 `SUCCESS`，但真正写入在服务端
- 客户端分支直接 `SUCCESS` / 粒子 / 音效，服务端分支才改真实内容
- 依赖 block entity 当前内容
- 依赖随机、权限、邻居、液体、双块联动、容器合并态
- 交互结果不仅改 block，还改手持物或生成/释放实体
