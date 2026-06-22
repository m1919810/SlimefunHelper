---
name: baritone-mixin-compatibility
description: 在 SlimefunHelper 中兼容多个 Baritone 发行形态，尤其是“全混淆”版本。用于区分无混淆、仅方法混淆、类与方法全混淆三种形态，并决定 MixinPlugin、目标类定位、成员定位、反射桥接和 hooks 访问层该如何拆分。
disable-model-invocation: true
---

# baritone-mixin-compatibility

## 目标

让当前 Baritone 注入链兼容三种运行时形态：

1. 完全无混淆
2. 类名保留、方法名混淆
3. 类名和方法名都混淆

当前项目里，第 1、2 种已经部分兼容，第 3 种还没有打通。

这类兼容问题不能只看单个 mixin，要把链路拆成两层：

- Mixin 目标类 / 目标成员的定位
- 运行时 hooks / API 访问层的定位

## 先看什么

优先阅读：

- `src/main/java/me/matl114/hooks/HooksMixinPlugin.java`
- `src/main/resources/slimefunhelper.mixins.hooks.json`
- `src/main/java/me/matl114/hooks/BaritoneHooks.java`
- `src/main/java/me/matl114/hooks/mixin/baritone/*.java`
- `mapping/Mappings/mappings-standalone.txt`
- 其他 `mapping/Mappings/mappings-*.txt`

## 当前现状

### 1. MixinPlugin 还是空壳

`HooksMixinPlugin` 当前没有任何版本探测、mixin 分发或 mapping 解析逻辑。

它现在只是：

- `shouldApplyMixin(...) -> true`
- `getMixins() -> List.of()`
- `preApply/postApply` 空实现

所以它目前没有承担任何兼容职责。

### 2. 现在的 mixin 只兼容“成员名差异”

当前 Baritone mixin 已经在一些点上做了双写兼容，例如：

- `method = {"a(...)V", "tickUseFireworks(...)V"}`
- `method = {"a()Z", "shouldLandForSafety()Z"}`
- `@Shadow(aliases = {"a", "setPath"})`

这能覆盖：

- 无混淆
- 类名不变、方法名变成 `a/b/c...`

但它仍然默认目标类名是未混淆名，比如：

- `baritone.process.elytra.ElytraBehavior`
- `baritone.process.ElytraProcess`
- `baritone.behavior.InventoryBehavior`
- `baritone.command.defaults.ElytraCommand`
- `baritone.cache.ChunkPacker`

所以一旦类名也被改掉，这一套就失效。

### 3. 全混淆版里，类名确实已经变化

从 `mapping/Mappings/mappings-standalone.txt` 可以直接看到：

- `baritone.process.elytra.ElytraBehavior -> baritone.ki`
- `baritone.process.elytra.ElytraBehavior$PathManager -> baritone.ki$d`
- `baritone.process.ElytraProcess -> baritone.ju`
- `baritone.behavior.InventoryBehavior -> baritone.ex`
- `baritone.command.defaults.ElytraCommand -> baritone.gd`
- `baritone.cache.ChunkPacker -> baritone.fj`
- `baritone.process.elytra.UnpackedSegment -> baritone.kn`
- `baritone.api.utils.IPlayerController -> baritone.ef`
- `baritone.api.process.PathingCommand -> baritone.cx`
- `baritone.api.process.PathingCommandType -> baritone.cy`

这说明问题不只是 `@Mixin` 的类目标变了，很多 handler 方法签名里用到的 Baritone 类型本身也变了。

### 4. 不只是 mixin，hooks 访问层也会失效

`BaritoneHooks.java` 当前直接 import 并调用：

- `baritone.api.BaritoneAPI`
- `baritone.api.IBaritone`
- `baritone.api.Settings`
- `baritone.api.event.events.ChatEvent`

而 mapping 显示这些 API 类在全混淆版里也被改名了。

也就是说：

- 即便 mixin 全部兼容了全混淆版
- `BaritoneHooks.Impl` 也会因为找不到 `baritone.api.*` 而退回 `Default`

所以必须把“mixin 注入兼容”和“Baritone API 访问兼容”一起做。

## 核心判断

## 不能把 MixinPlugin 当成“现成运行时 remapper”

如果你的意思是：

- 继续保留现在这批 mixin 类不动
- 在 `MixinPlugin` 里读 mapping file
- 然后自动把现有 mixin 上的 `@Mixin/@At/@Shadow/@Inject/@WrapOperation` 全部动态改写成全混淆版

那这条路不适合当前项目。

原因是：

1. `IMixinConfigPlugin` 没有一个直接、稳定的入口，让你在应用前把 mixin 类本身的注解和方法描述整包重写
2. 当前 mixin 里大量直接引用了 Baritone 类型，这不只是注解字符串问题，连 handler 方法签名都绑定了未混淆类型
3. 全混淆版连 `baritone.api.*` 都改名了，所以仅靠 target remap 不够，调用层也得改

结论：

- **MixinPlugin 可以做“版本探测 + mixin 分流 + mapping 注册”**
- **但不要指望它把现有 mixin 自动 runtime remap 成全混淆版**

## 建议架构

把兼容拆成三层。

### 第一层：版本探测层

放在 `HooksMixinPlugin`。

职责：

- 识别当前装载的是哪一种 Baritone 形态
- 决定启用哪一组 mixin
- 给后续 hooks / 反射桥提供 mapping 注册表

建议探测顺序：

1. 如果存在 `baritone.process.elytra.ElytraBehavior`
   - 进入“类名未混淆”分支
2. 再检查该类是否同时存在 `tickUseFireworks` / `pathTo` / `shouldLandForSafety`
   - 存在：无混淆分支
   - 不存在但有对应 `a/b/c`：方法混淆分支
3. 如果 `baritone.process.elytra.ElytraBehavior` 不存在，但存在 mapping 中的目标，如 `baritone.ki`
   - 进入“全混淆”分支

### 第二层：mixin 分流层

不要让同一批 mixin 同时承担三种形态。

建议分成：

- `baritone/clear/*`：无混淆 / 类名未混淆的版本
- `baritone/fullobf/*`：全混淆版本

其中：

- clear 分支可以继续沿用现在这批 mixin 的思路
- fullobf 分支单独写，直接面向 mapping 后的类名和成员名

`HooksMixinPlugin` 负责只返回当前版本需要的那一组 mixin。

### 第三层：运行时访问层

`BaritoneHooks` 不应该继续直接绑死在 `baritone.api.*` 上。

建议拆成：

- `BaritoneHooks.ClearImpl`
  - 继续直接调用 `BaritoneAPI`
- `BaritoneHooks.ObfImpl`
  - 基于 mapping + 反射 / MethodHandle 调 Baritone

实例选择顺序与 `HooksMixinPlugin` 的版本探测保持一致。

## full obf mixin 应该怎么写

### 1. 目标类改成字符串目标，不要再依赖类字面量

不要再写：

- `@Mixin(ElytraBehavior.class)`
- `@Mixin(ElytraProcess.class)`

改成面向运行时类名的字符串目标，例如：

- `@Pseudo`
- `@Mixin(targets = "baritone.ki")`
- `@Mixin(targets = "baritone.ki$d")`
- `@Mixin(targets = "baritone.ju")`
- `@Mixin(targets = "baritone.ex")`
- `@Mixin(targets = "baritone.gd")`
- `@Mixin(targets = "baritone.fj")`

这样 Mixin 至少能命中正确的类。

### 2. 尽量不要在 handler 签名里直接使用 Baritone 内部类型

因为全混淆版里这些类型名也变了。

优先级：

1. 能不用就不用
2. 能改成 `Object` / `@Coerce` 就改
3. 实在要创建或调用内部对象时，转为反射 / `MethodHandle`

当前最典型的高风险点：

- `ElytraBehaviourPathManagerMixin` 里直接用 `UnpackedSegment`
- `ElytraBehaviourMixin` 里直接用 `ElytraBehavior` / `InventoryBehavior` / `IPlayerController`
- `ElytraProcessMixin` 里直接 new `PathingCommand` / 用 `PathingCommandType`

这些在全混淆版里都不能继续假设类名稳定。

### 3. 把“对象创建”和“内部调用”抽到 helper 里

建议新建一个全混淆专用 helper 层，例如：

- `BaritoneObfMappings`
- `BaritoneObfReflection`
- `BaritoneObfHandles`

职责：

- 解析 `mapping/Mappings/*.txt`
- 提供 `deobf class -> runtime class` 查询
- 提供 `deobf method(signature) -> runtime name` 查询
- 缓存构造器、字段、方法句柄

这样 full obf mixin 里不直接写硬编码反射逻辑，只调 helper。

## mapping file 应该怎么用

### 1. 不要只存类名映射

至少要构建三张表：

- 类映射
  - `baritone.process.elytra.ElytraBehavior -> baritone.ki`
- 方法映射
  - `owner + deobfName + deobfDesc -> obfName`
- 字段映射
  - `owner + deobfName + deobfDesc -> obfName`

因为全混淆版里大量方法都压缩成了 `a/b/c`，只靠名字无法区分，必须带 descriptor。

### 2. 选择当前实际需要的 mapping 文件

你现在目录里有：

- `mappings-api.txt`
- `mappings-fabric-api.txt`
- `mappings-fabric-standalone.txt`
- `mappings-forge-api.txt`
- `mappings-forge-standalone.txt`
- `mappings-neoforge-api.txt`
- `mappings-neoforge-standalone.txt`
- `mappings-standalone.txt`

不要一开始就把所有文件一起混用。

先基于当前你实际想兼容的发行包类型，锁定一份主 mapping。

如果当前目标是 Fabric 客户端常见整包，优先确认：

- 你运行时加载的到底更接近 `fabric-standalone` 还是 `standalone`

确认后再决定默认读哪一份。

### 3. mapping 主要用来做“分支选择”和“反射桥接”

优先用法：

- 版本探测
- 反射构造器定位
- 反射方法定位
- 反射字段定位

不推荐一开始就尝试：

- 用 mapping 在 plugin 里把现有 mixin 的注解元数据整包 runtime 改写

## 最可行的落地顺序

### 阶段 1

先把 `HooksMixinPlugin` 做成真正的版本探测器。

交付目标：

- 能区分 3 种 Baritone 形态
- 能把结果保存为一个全局 runtime variant

### 阶段 2

把 `BaritoneHooks` 从“直接编译期绑定 API”改成“按 variant 选实现”。

交付目标：

- clear variant 继续走直接调用
- full obf variant 至少能完成：
  - setting 访问
  - `isElytraProcessing`
  - `resetState`
  - `pathTo`
  - `cancelEverything`

### 阶段 3

新增 full obf 专用 mixin 组，不要直接动现有 clear 组逻辑。

交付目标：

- `ElytraBehavior`
- `ElytraBehavior$PathManager`
- `ElytraProcess`
- `InventoryBehavior`
- `ElytraCommand`
- `ChunkPacker`

逐个补齐。

顺序建议：

1. `ChunkPacker`
   - 依赖最少
2. `InventoryBehavior`
   - 只碰一个 field read
3. `ElytraCommand`
   - 逻辑简单
4. `ElytraProcess`
   - 中等复杂度
5. `ElytraBehavior`
   - 最复杂
6. `PathManager`
   - 因为涉及 `UnpackedSegment` 构造与调用，最后做

## 结论

你们**可以**用 `MixinPlugin` 来支持全混淆版，但它更适合承担的是：

- 运行时版本识别
- 选择 mixin 分支
- 初始化 mapping 注册表

而**不适合**承担的是：

- 把当前这批 mixin 自动 runtime remap 成全混淆版

真正可落地的方案是：

- `MixinPlugin` 负责探测和分流
- 新增 full obf 专用 mixin 组
- `BaritoneHooks` 同时新增 full obf 访问实现
- mapping file 用于 runtime 反射桥接，不要试图把它当成 Mixin 的通用动态 remapper