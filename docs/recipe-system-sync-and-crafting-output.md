---
name: recipe-system-server-sync
description: Minecraft 1.21.11 Yarn build 4 高版本配方系统、服务端配方同步、客户端 RecipeDisplay、配方书以及 CraftingScreenHandler 输出槽同步机制。
---
# 高版本配方系统、服务端同步与合成台输出更新

## 原问题

分析当前源码树（Minecraft 1.21.11 Yarn build 4 的合并源码）中的配方系统，重点说明：

1. 服务端如何向客户端同步配方信息；
2. 客户端如何接收和使用这些信息；
3. 合成台输出槽如何由服务端通知客户端。

## 范围与结论

本报告只讨论当前源码中的常规配方书/配方展示、工作台合成和容器槽位同步，不展开数据包加载器的通用实现。

核心结论是：**当前版本把“配方展示信息”和“服务端实际配方逻辑”分开处理。**

- 服务端通过 `ServerRecipeManager` 加载并持有完整的 `Recipe`，负责最终的 `matches`、`craft`、配方解锁校验和剩余物处理。
- 客户端收到的主要是 `RecipeDisplayEntry`，用于配方书 UI、配方搜索、可合成判断、幽灵配方和“放入配方”。客户端不需要、也不依赖完整的服务端 `Recipe` 对象来显示配方。
- 登录/数据包重载时，服务端发送一份通用配方辅助数据 `SynchronizeRecipesS2CPacket`，再按玩家的配方书状态发送 `RecipeBookSettingsS2CPacket` 和 `RecipeBookAddS2CPacket`。
- 工作台输出槽不是通过配方同步包更新的，而是容器同步的一部分。服务端重新计算结果后，使用 `ScreenHandlerSlotUpdateS2CPacket` 更新 `syncId` 对应容器的槽位 `0`。

## 重要对象及职责

| 对象 | 职责 |
|---|---|
| `ServerRecipeManager` | 读取数据包中的配方，构建服务端配方索引、属性集合、切石机展示和网络配方 ID 映射。 |
| `ServerRecipeBook` | 保存某个玩家已解锁/高亮的配方，并把对应的展示条目发送给该玩家。 |
| `SynchronizeRecipesS2CPacket` | 同步通用的 `RecipePropertySet` 和切石机展示分组，不承载完整的所有配方对象。 |
| `RecipeBookAddS2CPacket` | 添加或初始化客户端配方书中的 `RecipeDisplayEntry`。 |
| `ClientRecipeManager` | 客户端保存服务端同步来的属性集合和切石机展示。 |
| `ClientRecipeBook` | 保存玩家当前已解锁的配方展示条目，并按配方书分类、分组。 |
| `CraftingScreenHandler` | 服务端工作台容器；根据输入格重新匹配并生成输出槽内容。 |
| `ScreenHandlerSyncHandler` | 把容器整体、单槽、光标和属性变化转换为网络包。 |

## 一、服务端如何加载和组织配方

### 1. 从数据包读取完整 `Recipe`

`ServerRecipeManager.prepare` 使用 `Recipe.CODEC` 读取配方资源，将每个资源包装成 `RecipeEntry`，并按标识符排序后保存（`net/minecraft/recipe/ServerRecipeManager.java:63-73`）。

随后 `initialize` 会根据启用的特性集过滤配方，构建：

- 各类 `RecipePropertySet`，例如用于判断锻造、熔炉等输入物是否合法的集合；
- 切石机的 `CuttingRecipeDisplay.Grouping`；
- 服务端配方列表和按原始配方键分组的展示列表（`net/minecraft/recipe/ServerRecipeManager.java:80-113`）。

### 2. 为每个展示生成 `NetworkRecipeId`

`collectServerRecipes` 遍历每个原始配方的 `getDisplays()`。每一个启用的 `RecipeDisplay` 都会被包装成一个 `RecipeDisplayEntry`，并分配一个从 `0` 开始的 `NetworkRecipeId`（`net/minecraft/recipe/ServerRecipeManager.java:209-247`）。

这里要注意：一个原始配方可以有多个展示，因此网络 ID 对应的是“某个展示”，不一定是一对一对应一个配方 JSON。服务端同时保留 `ServerRecipe(display, parent)`，所以收到客户端的网络 ID 后，仍能找到对应的真实 `RecipeEntry`。

`RecipeDisplayEntry` 包含：

- 网络 ID；
- `RecipeDisplay`，例如 shaped/shapeless crafting display；
- 配方书分组和分类；
- 可选的 `craftingRequirements`，供客户端根据玩家库存判断是否可合成。

其网络编码定义在 `net/minecraft/recipe/RecipeDisplayEntry.java:15-55`。展示本身使用注册表驱动的 `RecipeDisplay.PACKET_CODEC`（`net/minecraft/recipe/display/RecipeDisplay.java:12-24`）。例如有序合成展示会传输宽高、输入 `SlotDisplay` 列表、结果和工作台展示（`net/minecraft/recipe/display/ShapedCraftingRecipeDisplay.java:12-63`）。

## 二、服务端如何同步给客户端

当前版本登录时不是只发送一个“全部配方列表”包，而是至少包含以下几类信息。

### 1. 登录时发送通用配方辅助数据

玩家登录初始化阶段，`PlayerManager` 从服务端 `ServerRecipeManager` 取出属性集合和切石机展示，构造并发送 `SynchronizeRecipesS2CPacket`（`net/minecraft/server/PlayerManager.java:161-169`）。

该包的字段只有：

- `Map<RegistryKey<RecipePropertySet>, RecipePropertySet> itemSets`；
- `CuttingRecipeDisplay.Grouping<StonecuttingRecipe> stonecutterRecipes`。

字段和编码见 `net/minecraft/network/packet/s2c/play/SynchronizeRecipesS2CPacket.java:17-42`。因此它不是当前版本中工作台配方书的完整传输载体；工作台配方展示主要由下面的配方书包携带。

### 2. 发送配方书设置和玩家已解锁展示

`ServerRecipeBook.sendInitRecipesPacket` 先发送配方书选项，然后遍历玩家已解锁的原始配方，通过 `DisplayCollector` 找到该配方对应的所有 `RecipeDisplayEntry`，最后以 `replace=true` 发送 `RecipeBookAddS2CPacket`（`net/minecraft/server/network/ServerRecipeBook.java:124-137`）。

玩家获得新配方时：

1. 服务端检查该原始配方尚未解锁且不是 `isIgnoredInRecipeBook()`；
2. 将原始配方键加入服务端配方书并标记高亮；
3. 把该原始配方的所有展示转换成 `RecipeBookAddS2CPacket.Entry`；
4. 发送 `replace=false` 的增量包（`net/minecraft/server/network/ServerRecipeBook.java:64-85`）。

锁定配方时，服务端将对应展示的 `NetworkRecipeId` 放入 `RecipeBookRemoveS2CPacket`（`net/minecraft/server/network/ServerRecipeBook.java:88-107`）。

数据包重载时，服务端重新发送通用配方辅助数据，并对每个在线玩家重新发送配方书初始化数据（`net/minecraft/server/PlayerManager.java:857-874`）。

### 3. 同步的本质

可以把登录同步概括为：

```text
服务端数据包/注册表
        |
        v
完整 Recipe --------------------> ServerRecipeManager（服务端权威匹配和产出）
        |
        +--> RecipeDisplayEntry ---> RecipeBookAddS2CPacket ---> 客户端配方书
        |
        +--> RecipePropertySet/切石机展示 ---> SynchronizeRecipesS2CPacket
```

客户端拿到的是足以显示和放置配方的结构化展示数据，而不是把服务端所有匹配逻辑迁移到客户端。

## 三、客户端如何获取和使用配方信息

### 1. 接收通用配方数据

收到 `SynchronizeRecipesS2CPacket` 后，客户端网络处理器切换到主线程，并用包中的两个字段创建新的 `ClientRecipeManager`（`net/minecraft/client/network/ClientPlayNetworkHandler.java:1560-1563`）。

`ClientRecipeManager` 保存属性集合和切石机展示，并对外实现 `RecipeManager` 接口（`net/minecraft/client/recipebook/ClientRecipeManager.java:13-29`）。客户端世界的 `getRecipeManager()` 实际转发到网络处理器中的这个客户端管理器（`net/minecraft/client/world/ClientWorld.java:623-624`）。

### 2. 接收玩家配方书条目

客户端收到 `RecipeBookAddS2CPacket` 后：

- `replace=true` 时先清空客户端配方书；
- 把每个 `RecipeDisplayEntry` 放入 `ClientRecipeBook.recipes`；
- 根据标志位记录高亮和通知；
- 调用 `refreshRecipeBook` 重新分组、刷新搜索和当前界面（`net/minecraft/client/network/ClientPlayNetworkHandler.java:1600-1622`）。

客户端配方书以 `NetworkRecipeId -> RecipeDisplayEntry` 保存条目（`net/minecraft/client/recipebook/ClientRecipeBook.java:27-45`），并按类别、分组构建 `RecipeResultCollection`（`net/minecraft/client/recipebook/ClientRecipeBook.java:59-80`）。

### 3. 客户端本地判断“是否可合成”

`RecipeDisplayEntry.isCraftable` 使用条目中的 `craftingRequirements` 和客户端库存构造的 `RecipeFinder` 判断是否有足够材料（`net/minecraft/recipe/RecipeDisplayEntry.java:26-31`）。`RecipeResultCollection.populateRecipes` 据此维护可显示和可合成的 ID 集合（`net/minecraft/client/gui/screen/recipebook/RecipeResultCollection.java:28-50`）。

这个判断只服务于 UI 过滤和按钮状态，不能替代服务端验证。

### 4. 配方书选中配方后的网络交互

客户端点击配方时，只发送当前容器的 `syncId`、`NetworkRecipeId` 和是否批量合成的标志，即 `CraftRequestC2SPacket`（`net/minecraft/client/network/ClientPlayerInteractionManager.java:487-489`）。

服务端收到后会：

1. 验证当前容器 ID 和使用权限；
2. 通过 `ServerRecipeManager.get(NetworkRecipeId)` 找回服务端的 `ServerRecipe`；
3. 验证该原始配方确实已被玩家解锁；
4. 检查配方是否具有可放置的输入布局；
5. 调用 `AbstractRecipeScreenHandler.fillInputSlots`，由服务端根据玩家库存向输入槽放置材料（`net/minecraft/server/network/ServerPlayNetworkHandler.java:1905-1928`）。

工作台配方书的幽灵配方也由客户端的 `RecipeDisplay` 绘制。`CraftingRecipeBookWidget` 根据 shaped/shapeless display 把输入和结果绘制到幽灵槽位（`net/minecraft/client/gui/screen/recipebook/CraftingRecipeBookWidget.java:62-88`）。这只是 UI 层的预览；实际输入槽和输出槽仍由服务端容器状态决定。

## 四、合成台输出槽如何通知客户端

工作台的槽位布局中，输出槽固定为 `0`，输入槽是 `1-9`（`net/minecraft/screen/CraftingScreenHandler.java:23-49`）。

### 1. 输入变化触发服务端重新计算

工作台输入库存 `CraftingInventory` 持有对应的 `ScreenHandler`。输入槽写入新物品时调用 `handler.onContentChanged(this)`（`net/minecraft/inventory/CraftingInventory.java:63-66`）。对于普通工作台，`CraftingScreenHandler.onContentChanged` 在服务端世界中调用 `updateResult`（`net/minecraft/screen/CraftingScreenHandler.java:72-80`）。

### 2. 服务端权威匹配和生成结果

`CraftingScreenHandler.updateResult` 的流程是：

1. 将输入库存转换成 `CraftingRecipeInput`；
2. 使用服务端 `ServerRecipeManager.getFirstMatch(RecipeType.CRAFTING, ...)` 查找匹配配方；
3. 检查结果是否允许该玩家合成；
4. 调用真实 `CraftingRecipe.craft(...)` 生成 `ItemStack`；
5. 检查物品是否受当前特性集启用；
6. 写入结果库存的第 `0` 格。

对应源码为 `net/minecraft/screen/CraftingScreenHandler.java:51-67`。服务端的匹配实现最终调用配方的 `matches`，没有匹配时结果保持为空（`net/minecraft/recipe/ServerRecipeManager.java:128-139`）。

### 3. 结果槽的专用单槽包

写入输出槽后，`updateResult` 立即执行：

```text
resultInventory.setStack(0, result)
handler.setReceivedStack(0, result)
send ScreenHandlerSlotUpdateS2CPacket(syncId, nextRevision(), 0, result)
```

源码位于 `net/minecraft/screen/CraftingScreenHandler.java:67-70`。

这里的 `ScreenHandlerSlotUpdateS2CPacket` 携带：

- `syncId`：对应哪个打开的容器；
- `revision`：容器状态版本；
- `slot`：这里是 `0`；
- `stack`：输出槽的新 `ItemStack`。

数据包字段和编码见 `net/minecraft/network/packet/s2c/play/ScreenHandlerSlotUpdateS2CPacket.java:11-44`。

客户端收到包后，`ClientPlayNetworkHandler.onScreenHandlerSlotUpdate` 根据 `syncId` 选择当前容器，并调用 `currentScreenHandler.setStackInSlot(slot, revision, stack)`（`net/minecraft/client/network/ClientPlayNetworkHandler.java:1293-1318`）。因此客户端工作台界面看到的输出物，本质上是容器槽位同步的结果，不是客户端根据配方自行生成后再等待确认。

### 4. 通用容器同步与去重

除了 `updateResult` 的显式结果包，`ScreenHandler` 还会在 `sendContentUpdates` 中逐槽检查变化（`net/minecraft/screen/ScreenHandler.java:218-240`）。若槽位与客户端已知状态不一致，`checkSlotUpdates` 会调用 `ScreenHandlerSyncHandler.updateSlot`（`net/minecraft/screen/ScreenHandler.java:285-296`）。

服务端玩家的同步处理器把这个调用编码成同样的 `ScreenHandlerSlotUpdateS2CPacket`（`net/minecraft/server/network/ServerPlayerEntity.java:318-341`）。

`updateResult` 先调用 `handler.setReceivedStack(0, itemStack)`，再发送专用的输出槽包（`net/minecraft/screen/CraftingScreenHandler.java:67-69`）。这会更新服务端对槽位“已收到状态”的跟踪，避免随后 `sendContentUpdates` 再把同一个输出槽变化重复发送；输入槽、玩家背包和光标等其他变化仍可由通用同步流程发送。

### 5. 打开工作台时的初始状态

打开容器时，服务端先发送 `OpenScreenS2CPacket`，然后给新的 `ScreenHandler` 安装同步处理器（`net/minecraft/server/network/ServerPlayerEntity.java:1332-1352`）。`updateSyncHandler` 会触发 `syncState`，把所有槽、光标和属性作为整体状态交给服务端同步处理器（`net/minecraft/screen/ScreenHandler.java:168-198`），最终发送 `InventoryS2CPacket`。客户端通过 `onInventory` 将完整槽位列表写入当前容器（`net/minecraft/client/network/ClientPlayNetworkHandler.java:1342-1349`）。

所以工作台输出槽有两种到达客户端的场景：

- **首次打开或整体纠正**：包含在 `InventoryS2CPacket` 的完整容器内容中；
- **输入变化后的增量更新**：通常是 `ScreenHandlerSlotUpdateS2CPacket`，槽位号为 `0`。

## 五、实际合成和输出消耗仍由服务端控制

玩家点击输出槽时，客户端发送普通的 `ClickSlotC2SPacket`，其中包含容器 ID、修订号、槽位、点击类型、修改槽位哈希和光标哈希（`net/minecraft/network/packet/c2s/play/ClickSlotC2SPacket.java:16-70`）。

服务端在 `onClickSlot` 中验证 `syncId`、玩家是否能使用容器和槽位是否合法，然后调用服务端 `ScreenHandler.onSlotClick`；处理完后依据修订号是否一致，执行 `updateToClient` 或 `sendContentUpdates`（`net/minecraft/server/network/ServerPlayNetworkHandler.java:1869-1903`）。

输出槽由 `CraftingResultSlot` 禁止插入，只允许取出。取出时它会：

- 记录玩家合成行为并解锁最近配方；
- 从输入槽扣除材料；
- 通过服务端配方获取容器剩余物；
- 将剩余物放回输入槽、玩家背包或丢弃（`net/minecraft/screen/slot/CraftingResultSlot.java:26-35`、`82-113`）。

扣除材料又会触发 `CraftingInventory` 的内容变化，因而重新执行 `updateResult`，把新的输出槽内容再次发送给客户端。

## 简化时序

```text
登录
  ServerRecipeManager
      ├─ SynchronizeRecipesS2CPacket
      │     └─ ClientRecipeManager
      └─ RecipeBookAddS2CPacket
            └─ ClientRecipeBook（RecipeDisplayEntry）

打开工作台
  服务端创建 CraftingScreenHandler
      └─ InventoryS2CPacket（初始所有槽，输出槽=0）

玩家放入/取出输入材料
  Client -> ClickSlotC2SPacket
  Server -> ScreenHandler.onSlotClick
          -> CraftingInventory.onContentChanged
          -> CraftingScreenHandler.updateResult
          -> ServerRecipeManager.getFirstMatch
          -> CraftingRecipe.craft
          -> ScreenHandlerSlotUpdateS2CPacket(slot=0)
  Client -> currentScreenHandler.setStackInSlot(0, ...)
```

## 结论

高版本配方系统的关键变化是：网络同步面向 `RecipeDisplay`，服务端执行面向完整 `Recipe`。客户端拥有足够的展示、搜索、材料判断和放置配方所需的信息，但工作台真正的匹配、产出、消耗、剩余物和配方解锁仍由服务端决定。输出槽的客户端更新走的是 `ScreenHandler` 容器同步协议，最直接的路径是 `CraftingScreenHandler.updateResult` 发送 `ScreenHandlerSlotUpdateS2CPacket`，而不是重新发送配方信息。
