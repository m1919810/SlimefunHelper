---
name: minecraft-structure-loot-seed-analysis
description: Analyze Minecraft Java Edition structure generation, loot generation, world-seed derivation, and cubiomes structure calculations by tracing the relevant source-code RNG and generation paths.
---
# Minecraft 结构生成与战利品生成：seed 计算链路及 cubiomes 对照

- 分析日期：2026-09-28
- 目标版本：本仓库 Minecraft Java Edition 1.21.11 Yarn build 4 合并源码；对照相邻目录 `../cubiomes-viewer` 当前检出的源码。
- 问题摘要：说明 structure generation 和 loot generation 的计算链路、它们如何与世界 seed 关联，并指出这些逻辑在 Minecraft 与 cubiomes-viewer 中的源码位置。

## 结论先览

1. **结构位置与世界 seed 有直接、确定性的派生关系。** 对 `random_spread` 结构，世界结构 seed、region 坐标、结构 salt 经 `ChunkRandom.setRegionSeed` 混合，随后在 region 的合法范围中抽取起始 chunk；placement frequency、排除区、biome 合法性再决定是否实际建立结构。结构 piece/variant 还会继续消费结构生成 RNG。
2. **容器 loot table 的选择结果不是简单地“用世界 seed + 坐标”直接计算。** 结构生成阶段把其 RNG 的 `nextLong()` 存为容器的 `lootTableSeed`；玩家首次打开/读取容器时，loot table 从该 seed 创建随机源。于是 seed 链路通常是 `world seed → structure/chunk RNG → nextLong → lootTableSeed → loot rolls`。这条关系是间接的，并且不同容器/feature 有不同设种路径。
3. **`lootTableSeed == 0` 不是固定的零 seed loot。** `LootContext.Builder.random(0)` 不设置显式 RNG，随后按 loot table 的 random sequence ID 取世界 random sequence，缺省时才退回 world random。因此未显式设 loot seed 的 loot，不能仅靠世界 seed 与位置重算。
4. **cubiomes-viewer 的重点是 biome/structure 位置与 seed 搜索，不是容器战利品模拟。** 对照入口是 `cubiomes/finders.c` 的结构配置、region RNG、结构位置和 viability；viewer 把用户输入的 seed/version 交给 cubiomes 的 `Generator`/world 绘图和搜索逻辑。它没有沿 Java 的容器延迟开箱流程计算 loot table 内容。

## 一、结构生成：从 world seed 到实际结构

### 1. seed 如何到达 placement

`ChunkGenerating` 的 chunk 阶段调用 `ChunkGenerator.setStructureStarts(...)`（见 `net/minecraft/world/chunk/ChunkGenerating.java:34-37`）。调用者传入 `StructurePlacementCalculator`，calculator 持有当前维度/世界生成所用的 structure seed；placement 判断和 `StructureStart` 创建都会读取 `placementCalculator.getStructureSeed()`（`net/minecraft/world/gen/chunk/ChunkGenerator.java:481-502`）。新建世界或修改数据包后，结构配置、placement 参数也可能不同，所以**同一 seed 不保证跨版本或跨 worldgen 配置得到相同布局**。

### 2. `random_spread`：由世界 seed 计算候选起始 chunk

`RandomSpreadStructurePlacement.getStartChunk(seed, chunkX, chunkZ)` 的计算如下（`net/minecraft/world/gen/chunk/placement/RandomSpreadStructurePlacement.java:50-63`）：

1. 以 `floorDiv(chunkX, spacing)`、`floorDiv(chunkZ, spacing)` 求 region 坐标 `regionX/regionZ`。
2. 初始化 `ChunkRandom`，调用 `setRegionSeed(worldSeed, regionX, regionZ, salt)`。
3. 令 `range = spacing - separation`，按 `spread_type` 连续抽两次偏移量。
4. 以 `region * spacing + offset` 得到该 region 的候选起始 chunk。

`ChunkRandom.setRegionSeed` 的种子公式为 `regionX * 341873128712 + regionZ * 132897987541 + worldSeed + salt`，然后以该数设 RNG（`net/minecraft/util/math/random/ChunkRandom.java:64-67`）。由此可见，一个大世界 seed 被拆成**每个结构 region 各自可重现的随机流**；region 坐标和结构 salt 区分各 region/结构类型。

典型参数来自数据而非 Java 硬编码。例如 villages 的 structure set JSON 有 `spacing=34, separation=8, salt=10387312`（`data/minecraft/worldgen/structure_set/villages.json:1-8`）；woodland mansion 还指定 `spread_type=triangular`（`data/minecraft/worldgen/structure_set/woodland_mansions.json:1-8`）。实际候选位置还可能受数据定义的频率、exclusion zone 和 `Structure` 的 biome predicate 影响。

### 3. 候选位置如何成为结构

`StructurePlacement.shouldGenerate` 组合三项判断：候选 start chunk、frequency reduction、exclusion zone（`net/minecraft/world/gen/chunk/placement/StructurePlacement.java:61-70`）。frequency 默认实现再用世界 seed、salt 和 chunk 坐标初始化 region RNG 并比较随机浮点数（同文件 `:81-85`）。随后 `ChunkGenerator.setStructureStarts` 对通过 placement 的 structure set 选择结构（若 set 有多个加权候选，则用按结构 seed 与 chunk 坐标设定的 RNG 抽选），调用 `trySetStructureStart`（`net/minecraft/world/gen/chunk/ChunkGenerator.java:499-541`）。

`Structure.createStructureStart` 将 seed、chunk、biome 等封装进 Context，并调用 `getValidStructurePosition`；存在有效位置才生成 pieces 并创建 `StructureStart`（`net/minecraft/world/gen/structure/Structure.java:90-104`）。Context 的 chunk RNG 会以 `setCarverSeed(seed, chunkX, chunkZ)` 初始化（同文件 `:264-289`）。因此结构位置通常可由 placement 公式定位，但结构是否在该位置可生成还需 biome/地形/结构本身的额外规则。

之后 `ChunkGenerator.generateFeatures` 再按 generation step 取得该 chunk 对应的 starts 并调用 `StructureStart.place`（`net/minecraft/world/gen/chunk/ChunkGenerator.java:302-355`）。这是结构 piece 写入区块的后续阶段；“结构起点 chunk 的坐标”与“结构占据的所有 chunk”不是同一概念。

### 4. `ChunkRandom` 不只有一种 seed 公式

应区分不同阶段的随机流：

- `setRegionSeed`：region placement / frequency（`ChunkRandom.java:64-67`）。
- `setCarverSeed`：以世界 seed 和 chunk 坐标生成 carver/结构上下文 RNG（`:56-62`）。
- `setPopulationSeed`：从世界 seed 与 block 坐标生成 feature population seed（`:42-49`）。
- `setDecoratorSeed`：从 population seed、feature index、generation step 生成 decorator RNG（`:51-54`）。

所以“结构受 seed 控制”不表示全世界只用一个 RNG 顺序连续抽取；Minecraft 使用坐标、salt、阶段等派生出多个随机流，以尽量减少区块生成顺序对结果的影响。

## 二、容器 loot：从结构 RNG 到开箱结果

### 1. 结构放置时登记 loot table 和 seed

结构生成器一般不会当场将随机物品写入箱子，而是给箱体设置 loot table key 与 seed。例如 `StructurePiece.addChest` 用当前 piece RNG 的 `random.nextLong()` 调用 `ChestBlockEntity.setLootTable(lootTable, seed)`（`net/minecraft/structure/StructurePiece.java:364-375`）；`IglooGenerator`、`OceanRuinGenerator`、`MineshaftGenerator` 也有相似路径（分别见 `net/minecraft/structure/IglooGenerator.java:81`、`OceanRuinGenerator.java:197-201`、`MineshaftGenerator.java:425-429`）。部分 feature 通过 `LootableInventory.setLootTable(world, random, pos, table)` 设定（例如 `DungeonFeature.java:112-116`）。

抽象容器在 `LootableContainerBlockEntity` 保存 `lootTable` 和 `lootTableSeed`，可读写到容器组件（`net/minecraft/block/entity/LootableContainerBlockEntity.java:19-40,82-96`）。结构 pieces 收到的 RNG 来自结构生成过程，因此常见箱子 loot seed 是由世界 seed 所决定的结构 RNG 序列派生，但中间还包括 piece 的生成顺序和随机数消耗。

### 2. 首次访问时延迟 roll

容器的读槽、移除物品、打开菜单等入口会先触发 `generateLoot`（`LootableContainerBlockEntity.java:43-75`）；这使 loot 通常在首次访问时生成。结构箱在地形生成期间只保存 table key/seed，开箱阶段才按该 table 的 pools、entries、conditions 和 functions 抽取具体物品。

`LootTable.generateLoot(parameters, seed, ...)` 将显式 seed 放进 `LootContext.Builder.random(seed)`，再构造 context 并执行 pools（`net/minecraft/loot/LootTable.java:101-118`）。Builder 只有 seed 非零时才创建显式随机源（`net/minecraft/loot/context/LootContext.java:205-215`）；build 时若没有显式 RNG，则优先使用 loot table 的 random sequence ID 对应的 world RNG，之后 fallback 到 world random（同文件 `:222-230`）。因此，**零值是“未提供明确 seed”的哨兵语义之一，不等价于 `Random.create(0)`**。

具体哪些 container 调用 seed overload、seed 为何值，要追踪各自调用者；不同内容不应一概而论。Loot table JSON 决定抽取规则，不负责将世界 seed 直接换算成箱子 seed。Chest loot seed 是控制该 loot context 的随机流，不是 loot table ID，也不是世界 seed 本身。

### 3. world seed 与 loot 的因果链及边界

对典型结构宝箱，链路可表示为：

`世界 seed (+ 版本、worldgen 配置)` → `结构 placement RNG` → `结构起点与结构构造 RNG` → `piece/feature 的随机消费` → `random.nextLong()` → `容器 lootTableSeed` → `LootContext RNG` → `loot table pools / conditions / functions` → `ItemStack`

因而在结构和生成实现都固定、seed 显式保存的前提下，loot 通常可复现。但不能仅凭世界 seed 与宝箱坐标推算任意箱子的内容：还要知道所用版本/配置、结构 piece 的 RNG 轨迹、是否有显式 loot seed、容器是否已被打开/改动，以及 loot table 和上下文参数。玩家属性、Luck、工具、实体等上下文也可能参与条件与函数计算。

## 三、cubiomes-viewer：在哪里接入、计算到哪一步

### 1. viewer 传递 seed 的入口

viewer 的 UI 将用户输入封装为 `WorldInfo`，`MainWindow::setSeed` 更新版本和 seed，并调用 `MapView::setSeed`（`src/mainwindow.cpp:387-438`）。`MapView::setSeed` 会据新 `WorldInfo` 创建 `QWorld`（`src/mapview.cpp:107-125`）。在 cubiomes 核心 API 中，`getStructurePos(structureType, mc, seed, regX, regZ, pos)` 是从版本、世界 seed、region 坐标计算结构候选位置的清晰入口（`cubiomes/finders.c:212-239`；接口注释 `cubiomes/finders.h:203-217`）。

### 2. cubiomes 的 random_spread 对照

`cubiomes/finders.c` 通过 `getStructureConfig(structureType, mc, ...)` 选择对应版本的 salt、region size、chunk range 等参数（`:58-105`；接口说明 `cubiomes/finders.h:188-201`）。普通 feature 的 region RNG/坐标派生位于 `cubiomes/finders.h:746-792`：核心公式与 Vanilla 类似，即 `seed + regionX*341873128712 + regionZ*132897987541 + salt`，再做 Java 48-bit LCG 和两次 `nextInt`，转为 block coordinates。大型结构使用 triangular spread 分支（同文件 `:795-...`）。结构类型分派、outpost 额外概率、treasure、fortress/bastion 等版本分支在 `cubiomes/finders.c:224-320`。

Cubiomes 对结构的计算并非只返回最终“已生成”结论：其头文件注释明确区分候选位置与 `isViableStructurePos` 的 biome viability 检查（`cubiomes/finders.h:203-217`）。viewer 的结构绘图/搜索还会使用 Generator 的版本、seed 与 biome 数据；biome 合法性涉及 `cubiomes/finders.c` 中的 viability 检查区域（约 `:1434` 起）。需要特别留意：各结构在 1.21.11 的 Vanilla 可能使用新版 random provider/版本规则；cubiomes 支持版本范围和具体结构逻辑必须以当前 `getStructureConfig` 与分支实现为准，不能将某一个简化公式套用所有类型。

### 3. cubiomes 不覆盖 loot roll

viewer README 将项目定位为 Minecraft biome/structure map 和 seed-finding utilities（`../cubiomes-viewer/README.md:1-5,34-55`）。结构核心路径是 `cubiomes/finders.c` / `finders.h`；viewer 在 `src/`，核心 cubiomes 以相邻仓库的 `cubiomes/` 子目录/子模块方式编译（`../cubiomes-viewer/cubiomes-viewer.pro:66-70`，`buildguide.md:75-90`）。在这些主要计算路径中，cubiomes 提供的是 biome/structure 与 seed 搜索/位置判定，并未实现 Minecraft `LootTable`、`LootContext`、结构宝箱延迟开箱及生成物品的完整链路。它可以帮助找结构位置和 seed，不应被当作 vanilla 容器 loot 预测器。

## 四、源码入口索引与访问方式

### 当前 Minecraft Yarn 源码树

从本项目根目录查看：

- `net/minecraft/util/math/random/ChunkRandom.java`：世界 seed 派生的 region/chunk/population/decorator RNG。
- `net/minecraft/world/gen/chunk/placement/RandomSpreadStructurePlacement.java`：region 到起始 chunk 的 spread 计算。
- `net/minecraft/world/gen/chunk/placement/StructurePlacement.java`：frequency reduction、exclusion zone 判定。
- `net/minecraft/world/gen/chunk/placement/StructurePlacementCalculator.java`：结构 placement 配置/索引与结构 seed。
- `net/minecraft/world/gen/chunk/ChunkGenerator.java`：`setStructureStarts`、结构放置，以及 feature 阶段实际调用 `StructureStart.place`。
- `net/minecraft/world/gen/structure/Structure.java`、`net/minecraft/structure/StructureStart.java`：结构有效性、结构上下文和 pieces。
- `net/minecraft/structure/StructurePiece.java`、具体 `*Generator.java`：容器 loot table key/seed 的设置点。
- `net/minecraft/block/entity/LootableContainerBlockEntity.java`、`net/minecraft/loot/LootTable.java`、`net/minecraft/loot/context/LootContext.java`：延迟生成与 loot RNG。
- `data/minecraft/worldgen/structure_set/*.json` 与 `data/minecraft/loot_table/**/*.json`：placement 参数、table 内容。

此源码目录当前不是 Git 仓库（执行 Git 操作会提示 `not a git repository`），但 Java 文件和 `data/` 是本地可直接搜索/阅读的源码及数据快照；当前快照标识为 Minecraft 1.21.11 Yarn build 4。行号以此工作区版本为准。

### Cubiomes Viewer 源码

本机项目相邻目录可从 Minecraft 项目根目录按 `../cubiomes-viewer/` 访问：

- `../cubiomes-viewer/cubiomes/finders.c`：版本化结构配置和结构类型分派。
- `../cubiomes-viewer/cubiomes/finders.h`：`getStructurePos` API，以及 feature/large-structure spread 内联算法。
- `../cubiomes-viewer/cubiomes/rng.h`：Java LCG `setSeed`/`next` 等 RNG 基元。
- `../cubiomes-viewer/src/mainwindow.cpp`、`src/mapview.cpp`、`src/world.cpp`：viewer seed 输入、地图刷新和 world/Generator 计算接线。
- `../cubiomes-viewer/README.md`、`buildguide.md`：项目能力定位与构建方式；`.pro` 文件显示 cubiomes 子目录如何被编译链接。

要在其他机器取得源代码，viewer 的 `buildguide.md:77-80` 给出 recursive clone：`git clone --recursive https://github.com/Cubitect/cubiomes-viewer.git`。递归克隆会取回其 cubiomes 子模块；若只下载 viewer 主仓库而未初始化子模块，核心 C 源码可能缺失。

## 范围与注意事项

- 本报告追踪的是 worldgen 结构与 lootable container 的常见流程，不覆盖所有独立随机系统（例如 fishing、mob drops、vault、trial spawner eject、玩家触发 loot 等）。
- cubiomes 与目标 Minecraft 源码版本并非逐行同实现：cubiomes 有自己的支持版本表和针对不同版本的分支；精确一致性需按结构类型、游戏版本、seed 位宽、dimension 和 biome viability 分别核对。
- 世界 seed 到 loot seed 的连接依赖结构/feature RNG 的具体消费轨迹；没有显式 loot seed 的路径还可能落入随机 sequence/world random，不能仅用位置公式代替。

## Takeaway

结构是通过 world seed 派生的、按 region/chunk 分流的 RNG 决定候选位置，再经频率、biome、地形与结构规则筛选；结构箱 loot 则多由结构 RNG 再抽一个独立 `lootTableSeed`，在实际访问时由 `LootContext` 按 loot table 规则 roll。Minecraft 源码可在本仓库 `net/minecraft` 与 `data/minecraft` 直接检查；cubiomes-viewer 的可比计算集中在相邻 `../cubiomes-viewer/cubiomes/finders.c/.h`，其结构/biome 搜索能力不等于 loot 模拟。
