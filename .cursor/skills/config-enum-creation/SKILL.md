---
name: config-enum-creation
description: 为 SlimefunHelper 新增或改造 `ConfigEnum` / `EnumRef` 配置链。用于创建枚举型配置、补全 `EnumRef<T>` 字段、对齐自动注册、YAML 序列化、配置 UI 枚举映射，以及补全 `configenum.*` / `module-meta.*` 语言键。基于本仓库现有实现，不按通用 Java 枚举套路乱写。
disable-model-invocation: true
---

# config-enum-creation

## 目标

这个仓库里的枚举配置不是“写一个 enum 就完事”，而是一整条链：

- `enum implements ConfigEnum`
- `EnumRef<T>` 字段声明
- `builder(..., EnumClass.class).defaultValue(...).build()`
- `AutoRegisterType` / `onLoad(Class)` 自动注册
- YAML 序列化为 `enum:<type>:<ENUM_NAME>`
- reload 后通过 lazy resolve 恢复为真实枚举值
- 配置 UI 通过 `enumMap(...)` 展示所有候选值
- 默认显示文案走 `configenum.*`
- 模块列表附加元信息走 `moduleMeta(() -> this.ref)` + `module-meta.*`

新增、改造、补语言时，必须把这条链一次补完整。

## 先看什么

优先阅读这些真实实现，不要脑补不存在的文件：

- `src/main/java/me/matl114/managers/config/ConfigEnum.java`
- `src/main/java/me/matl114/managers/config/EnumRef.java`
- `src/main/java/me/matl114/managers/config/Refs.java`
- `src/main/java/me/matl114/managers/config/LazilyRegisterTypeRef.java`
- `src/main/java/me/matl114/managers/config/AutoRegisterType.java`
- `src/main/java/me/matl114/managers/config/Config.java`
- `src/main/java/me/matl114/hacks/api/BaseModule.java`
- `src/main/java/me/matl114/managers/Configs.java`
- `src/main/resources/assets/slimefunhelper/lang/en_us.json`
- 目标模块本身

明确一点：

- 本仓库没有单独的 `WrapperSettingBuilder.java`
- builder / wrapper 逻辑在 `Config.java` 的 `SettingBuilder<T>` 和 `BaseModule.java` 的 `WrapperSettingBuilder<T>` / `WrapperConfigRef<T>` 里

## 仓库里的硬规则

### 1. `ConfigEnum` 就是配置类型协议

`ConfigEnum` 同时继承：

- `StringIdentifiable`
- `Displayable`
- `AutoRegisterType`

所以它同时定义了：

- 存盘标识
- UI 显示
- 自动注册入口

不要绕开它自己再造一层“枚举配置协议”。

### 2. 类型名是持久化协议的一部分

YAML 存的是：

```text
enum:<config-enum-type>:<ENUM_NAME>
```

其中：

- `<config-enum-type>` 来自 `getConfigEnumType()`
- `<ENUM_NAME>` 来自枚举常量名本身

所以这两个名字都不能随便改：

- 改 `getConfigEnumType()` 会影响存盘、读取、语言 key、module meta key
- 改枚举常量名会影响旧配置恢复和语言 key 命中

除非你明确在做迁移，否则不要重命名现有 type 和现有常量名。

### 3. UI 选项顺序 = 枚举声明顺序

`ConfigEnum.register(...)` 用的是 `LinkedHashMap`，并按 `getEnumConstants()` 顺序写入。

结论：

- 配置界面里候选值顺序就是 enum 常量声明顺序
- 需要调整 UI 顺序时，改 enum 常量顺序，不要去找别的排序器

### 4. 枚举类型名和配置路径不是一回事

例如：

- 配置路径可以是 `move-safety.no-fall.bypass-mode`
- 枚举类型名却是 `no_fall_bypass_mode`
- 语言 key 最终是 `configenum.no-fall-bypass-mode.*`

不要把“配置路径最后一段”当成“枚举类型名”。这两个在仓库里经常不相等。

## 真实运行链路

### A. 注册链

真实链路是：

1. `builder(path, EnumClass.class)` 创建 `SettingBuilder<T>`
2. `SettingBuilder` 构造时调用 `Config.registerClassSupport(clazz)`
3. 只要这个类实现了 `AutoRegisterType`，就会通过反射调用静态 `onLoad(Class)`
4. `ConfigEnum.onLoad(Class)` 会执行 `ConfigEnum.ensureRegistered(...)`
5. `ensureRegistered(...)` 把该 enum 的所有常量注册进：
   - `ConfigEnum.registeredConfigs`
   - `ConfigEnum.registeredEnumsClasses`

这是第一层自动注册。

### B. 默认值链

写：

```text
builder(..., SomeEnum.class).defaultValue(SomeEnum.X).build()
```

时，第二层保障会发生：

1. `defaultValue(...)` 内部走 `Refs.wrapInstance(val)`
2. `ConfigEnum` 实例会被包成 `EnumRef`
3. `EnumRef(ConfigEnum enumR)` 会调用 `tryRegisterType(value)`
4. `tryRegisterType(...)` 再次执行 `ConfigEnum.ensureRegistered(...)`

也就是说：

- builder 声明 enum class 时会注册一次
- defaultValue 给 enum 实例时又会兜底注册一次

不要自己手搓注册逻辑，直接按 builder 正常写。

### C. 存盘链

真实存盘格式来自 `LazilyRegisterTypeRef.getAsPrimitive()`：

```text
enum:<enumType>:<enumValue>
```

`EnumRef` 的前缀固定是 `enum`。

例如：

- `Mode.NO_BYPASS` + type `no_fall_bypass_mode`
- 存成 `enum:no_fall_bypass_mode:NO_BYPASS`

### D. 读盘链

真实读盘链是：

1. YAML 被读成普通 `Map<String, Object>`
2. `Refs.transferConfig(...)` 递归包裹所有值
3. 字符串值先走 `String.class` 的 builder 链
4. 先尝试 `EnumRef.fromString(...)`
5. 命中 `enum:` 前缀后，得到一个 lazy 的 `EnumRef`
6. 如果此时 enum class 尚未注册，`EnumRef` 会先只保留：
   - `enumType`
   - `enumValue`
7. 后续通过 `setEnumType(...)` / `get()` / UI 渲染再完成 resolve

所以本仓库支持“先加载字符串，再等类型注册完成后解析成真实枚举”。

### E. 配置 UI 链

配置界面最终不是直接吃 enum，而是吃 `AttrKeyValue.enumMap(...)`：

- `EnumRef._createKeyValue0(...)` 会在已 resolve 的前提下创建 `enumMap`
- 候选值来源是 `this.getValue().getMap()`
- `getMap()` 会再次确保该 enum 已注册

如果你绕开 builder / 默认值 / 自动注册链，UI 很容易在这里炸成“还未注册”。

## `Refs` 体系里，`EnumRef` 处于什么位置

`Refs.referenceBuilders` 的真实顺序里，和枚举有关的部分是：

- `ConfigEnum.class -> EnumRef::new`
- `String.class` 的解析链里优先尝试 `EnumRef::fromString`

同一套 `Refs` 还负责这些类型：

- `Boolean -> FlagRef`
- `Integer -> IntRef`
- `Long -> LongRef`
- `Float/Double -> DoubleRef`
- `MultiKeyBind -> KeyBindRef`
- `List -> ListRef`
- `NBTParsable -> NBTRef`
- `String -> EnumRef / KeyBindRef / NBTRef / FlagRef / IntRef / LongRef / StringRef`

结论：

- `EnumRef` 不是单独的旁路系统
- 它是整个 `Refs.wrapInstance(...)` 生态中的一个标准分支

## 新增时怎么落地

### 1. 先决定 enum 放哪

按仓库当前模式，只分两类：

#### 共享语义，多个模块复用

放进 `me.matl114.managers.Configs`。

真实例子：

- `LegalTargetingMode`
- `LegalInteractMode`
- `BypassMode`
- `MineTargetingMode`
- `AutoInvMode`
- `SetBackTriggerType`

#### 只服务某个模块或某个局部能力

放进模块类内部。

真实例子：

- `NoFall.Mode`
- `Velocity.Mode`
- `ElytraBot.Mode`
- `DisablerManager.SupportAC`
- `ConnectionProxy.HttpProxyType`

#### 局部 enum 也能被别处直接复用

真实例子：

- `ElytraExtra.MotionMode` 定义在 `ElytraExtra`
- 但 `ElytraFlight.motionMode` 直接声明成 `EnumRef<ElytraExtra.MotionMode>`

所以不要因为“以后可能复用”就强行全塞进 `Configs`。先看实际语义边界。

### 2. 决定 type 名

#### 默认行为

如果不重写 `getConfigEnumType()`：

- type = `SimpleClassName.toLowerCase()`
- 不会自动插入 `_`
- 也不会自动插入 `-`

真实例子：

- `MotionMode -> motionmode`
- `ArmorFlyMode -> armorflymode`
- `EncryptAlgorithm -> encryptalgorithm`
- `HttpProxyType -> httpproxytype`

#### 自定义行为

如果你要稳定、可读、跨模块的 type 名，就重写 `getConfigEnumType()`，使用 snake_case。

真实例子：

- `legal_targeting_mode`
- `no_fall_bypass_mode`
- `support_disabler_ac`
- `packet_sneak_bypass_mode`

仓库现状是混合风格，已经落地的老 type 不要统一重命名。

### 3. 声明 `EnumRef<T>`

模块侧标准形态：

```java
public final EnumRef<Type> mode = builder(module.add("mode"), Type.class)
        .defaultValue(Type.DEFAULT)
        .build();
```

共享 enum 复用形态：

```java
public final EnumRef<Configs.LegalInteractMode> mode = builder(module.add("mode"), Configs.LegalInteractMode.class)
        .defaultValue(Configs.LegalInteractMode.NONE)
        .build();
```

跨类局部 enum 复用形态：

```java
public final EnumRef<ElytraExtra.MotionMode> motionMode = builder(module.add("motion-mode"), ElytraExtra.MotionMode.class)
        .defaultValue(ElytraExtra.MotionMode.VOID)
        .build();
```

### 4. 需要配置界面显隐联动时，直接依赖 enum ref

真实模式：

- `mode.get().isIn(...)`
- `mode.get().isNotIn(...)`

现成例子：

- `Velocity` 里多个 `.show(() -> mode.get().isIn(...))`
- `ElytraBot` 里多组配置跟着 `mode` 切换显示
- `TravellingControl` 里 `negativeArgument` 跟 `controlType` 联动

## 显示与语言 key 规则

### A. 默认显示规则

如果 enum 没有重写 `getDisplay()`，默认显示走：

```text
configenum.<type 把 _ 替换为 ->.<enum 常量名小写>
```

真实例子：

- `legal_targeting_mode` -> `configenum.legal-targeting-mode.delay_movement`
- `motionmode` -> `configenum.motionmode.fire_works`
- `elytramode` -> `configenum.elytramode.control`
- `encryptalgorithm` -> `configenum.encryptalgorithm.aes_gcm`

### B. 自定义显示规则

当前仓库里，真实的 `ConfigEnum` 显示覆写只有两种：

#### `DisablerManager.SupportAC`

- type: `support_disabler_ac`
- `getDisplay()` 返回 `Text.literal(this.name())`
- 所以配置 UI 不依赖 `configenum.support-disabler-ac.*`
- 但如果模块入口要显示当前值，仍然要补 `module-meta.support-disabler-ac.*`

#### `ConnectionProxy.HttpProxyType`

- type: 默认 `httpproxytype`
- `getDisplay()` 返回 `Text.literal(name().toLowerCase(Locale.ROOT))`
- 所以配置 UI 不依赖 `configenum.httpproxytype.*`
- 当前也没有 `moduleMeta(...)` 入口，因此不需要 `module-meta.httpproxytype.*`

### C. `moduleMeta(...)` 规则

`BaseModule.moduleMeta(() -> this.ref)` 不走 `getDisplay()`。

它固定走：

```text
module-meta.<type 把 _ 替换为 ->.<enum 常量名小写>
```

所以：

- 即使 enum 自己的 `getDisplay()` 是 `Text.literal(...)`
- 只要你用了 `moduleMeta(...)`
- 就必须补 `module-meta.*`

真实例子：

- `NoFall.noFallMode`
- `Velocity.mode`
- `ElytraBot.mode`
- `Criticals.mode`
- `DisablerManager.currentAC`
- `Flight.flightMode`
- `MineBot.mineBotMode`

### D. 最终补语言时的判断矩阵

#### 默认 `getDisplay()`，没有 `moduleMeta(...)`

补：

- `configenum.*`

#### 默认 `getDisplay()`，并且用了 `moduleMeta(...)`

补：

- `configenum.*`
- `module-meta.*`

#### `getDisplay()` 改成 `Text.literal(...)`，没有 `moduleMeta(...)`

补：

- 通常不用补 `configenum.*`
- 也不用补 `module-meta.*`

#### `getDisplay()` 改成 `Text.literal(...)`，并且用了 `moduleMeta(...)`

补：

- 不需要 `configenum.*`
- 但仍然需要 `module-meta.*`

## 当前仓库里所有真实 `ConfigEnum` 实现

### 共享 enum，在 `Configs.java`

- `Configs.LegalTargetingMode` -> `legal_targeting_mode`
- `Configs.LegalInteractMode` -> `legal_interact_mode`
- `Configs.BypassMode` -> `bypass_mode`
- `Configs.MineTargetingMode` -> `mine_targeting_mode`
- `Configs.AutoInvMode` -> `auto_inv_mode`
- `Configs.SetBackTriggerType` -> `setback_trigger_type`

### 模块内 enum，重写了 `getConfigEnumType()`

- `NoFall.Mode` -> `no_fall_bypass_mode`
- `MineExtra.Mode` -> `fast_break_bypass_mode`
- `Velocity.Mode` -> `velocity_bypass_mode`
- `ElytraBot.Mode` -> `elytra_bot_mode`
- `Criticals.Mode` -> `critical_mode`
- `PositionPredict.Mode` -> `predict_mode`
- `DisablerManager.SupportAC` -> `support_disabler_ac`
- `NoSlowDown.PacketSneakMode` -> `packet_sneak_bypass_mode`
- `NoSlowDown.UseBypassMode` -> `use_item_noslow_bypass`
- `Flight.Mode` -> `flight_mode`
- `MineBot.MineBotMode` -> `mine_bot_mode`
- `Airplace.Mode` -> `air_place_mode`
- `Blink.Action` -> `blink_event_action`
- `TravellingControl.Type` -> `travel_control_type`
- `ElytraFlight.Mode` -> `elytramode`

### 模块内 enum，使用默认 type 名

- `ElytraExtra.MotionMode` -> `motionmode`
- `ElytraExtra.ArmorFlyMode` -> `armorflymode`
- `PlayerChat.EncryptAlgorithm` -> `encryptalgorithm`
- `ConnectionProxy.HttpProxyType` -> `httpproxytype`

### 自定义显示的真实特例

- `DisablerManager.SupportAC` -> `Text.literal(this.name())`
- `ConnectionProxy.HttpProxyType` -> `Text.literal(name().toLowerCase(Locale.ROOT))`

### 不要漏掉的特殊点

- `PositionPredict.Mode` 虽然实现了 `ConfigEnum`，但当前不是模块顶层 `EnumRef` 字段，而是被 `PredictArgument` 的 codec 使用
- `ElytraExtra.MotionMode` 没有在 `ElytraExtra` 自己声明 `EnumRef<MotionMode>`，而是被 `ElytraFlight.motionMode` 复用

## 当前仓库里所有真实 `EnumRef` 字段

### `Configs.*` 共享 enum 的使用点

- `Attack.legalTargetingMode -> Configs.LegalTargetingMode`
- `AutoSurround.mode -> Configs.LegalInteractMode`
- `BowEnhance.mode -> Configs.LegalInteractMode`
- `PrinterRewrite.mode -> Configs.LegalInteractMode`
- `ProjectileEnhance.mode -> Configs.LegalInteractMode`
- `Scaffold.legalMode -> Configs.LegalInteractMode`
- `MultiBlockHelper.legalMode -> Configs.LegalInteractMode`
- `MineBot.legalMode -> Configs.MineTargetingMode`
- `AutoTotem.mode -> Configs.AutoInvMode`
- `Criticals.setBackType -> Configs.SetBackTriggerType`
- `ElytraGrimAccelerate.mode -> Configs.SetBackTriggerType`
- `BlockRotate.bypassMode -> Configs.BypassMode`
- `BlockRotate.bypassMode2 -> Configs.BypassMode`
- `Sprint.fakeSprintMode -> Configs.BypassMode`
- `Sprint.directionalSprintMode -> Configs.BypassMode`
- `ElytraExtra.noKineticMode -> Configs.BypassMode`
- `ElytraExtra.maceFixMode -> Configs.BypassMode`
- `NoSlowDown.blockInBypass -> Configs.BypassMode`
- `NoSlowDown.fakeSneakBypass -> Configs.BypassMode`

### 模块内或局部 enum 的使用点

- `DisablerManager.currentAC -> SupportAC`
- `PlayerChat.algorithm -> EncryptAlgorithm`
- `Blink.attackBehaviour -> Action`
- `Blink.onHurtBehaviour -> Action`
- `Blink.onVelocityBehaviour -> Action`
- `Blink.onInventoryBehaviour -> Action`
- `Blink.onTotemBehaviour -> Action`
- `Blink.onEnermyNearBehaviour -> Action`
- `Blink.nearTargetAction -> Action`
- `Airplace.enableAirWall -> Mode`
- `MineExtra.fastBreakBypassMode -> Mode`
- `Velocity.mode -> Mode`
- `ElytraBot.mode -> Mode`
- `Criticals.mode -> Mode`
- `Flight.flightMode -> Mode`
- `NoFall.noFallMode -> Mode`
- `ElytraFlight.controlMode -> Mode`
- `TravellingControl.controlType -> Type`
- `MineBot.mineBotMode -> MineBotMode`
- `NoSlowDown.useItemBypass -> UseBypassMode`
- `NoSlowDown.fakeStatusBypass -> PacketSneakMode`
- `ElytraExtra.armorMode -> ArmorFlyMode`
- `ElytraFlight.motionMode -> ElytraExtra.MotionMode`
- `ConnectionProxy.proxyType -> HttpProxyType`

### 当前不是有效模式的注释残留

- `ElytraFlightLegit` 里有注释掉的 `EnumRef<ElytraFlight.ElytraMode>`，它不是现行实现，不要拿它当模板
- `ElytraGrimAccelerate` 里有注释掉的旧 `Mode implements ConfigEnum`，也不是现行实现

## 当前仓库里所有真实 `moduleMeta(() -> this.enumRef)` 使用点

- `NoFall.noFallMode`
- `Velocity.mode`
- `ElytraBot.mode`
- `Criticals.mode`
- `DisablerManager.currentAC`
- `Flight.flightMode`
- `MineBot.mineBotMode`

这几个地方新增值时，除了配置 UI 显示，还要同步补 `module-meta.*`。

## 创建新枚举配置时的最小清单

### 如果是默认显示 enum

1. 新建或复用 `ConfigEnum`
2. 确认 `getConfigEnumType()` 是否需要自定义
3. 声明 `EnumRef<T>` 字段
4. 设置 `defaultValue(...)`
5. 如果界面联动依赖该枚举，补 `.show(() -> ref.get().isIn(...))`
6. 补 `configenum.<type>.*`
7. 如果该值要显示在模块入口额外元数据里，再补 `module-meta.<type>.*`

### 如果是自定义 `getDisplay()` 的 enum

1. 新建或复用 `ConfigEnum`
2. 明确 `getDisplay()` 是 `Text.literal(...)` 还是别的逻辑
3. 声明 `EnumRef<T>` 字段
4. 设置 `defaultValue(...)`
5. 只在用了 `moduleMeta(...)` 时补 `module-meta.*`
6. 不要机械地补一套无用的 `configenum.*`

## 写之前先检查的两个问题

### 这个语义是不是已有 shared enum

优先复用这些现成共享类型：

- 合法交互 -> `Configs.LegalInteractMode`
- 合法瞄准 -> `Configs.LegalTargetingMode`
- 通用绕过模式 -> `Configs.BypassMode`
- 自动背包模式 -> `Configs.AutoInvMode`
- 击退回正触发模式 -> `Configs.SetBackTriggerType`
- 挖掘合法模式 -> `Configs.MineTargetingMode`

### 这个 enum type 名是不是已经对外稳定

如果是已有存量 type：

- 直接复用旧 type 名
- 不要“顺手规范化”
- 也不要把 `motionmode` 改成 `motion_mode`
- 也不要把 `elytramode` 改成 `elytra_mode`

这个仓库已经有历史包袱，保持兼容优先。

## 交付前检查

至少确认这些点：

- enum 真正实现了 `ConfigEnum`
- `EnumRef<T>` 的泛型和 builder 传入 class 一致
- `defaultValue(...)` 是该 enum 的真实常量
- 没有手写绕过 `builder(...).build()` 的注册逻辑
- 如果沿用默认 `getDisplay()`，对应 `configenum.*` 已补全
- 如果用了 `moduleMeta(...)`，对应 `module-meta.*` 已补全
- 没有把配置路径错当成 enum type 名
- 没有重命名现有 type 或现有枚举常量导致兼容性破坏

## 这次仓库里最关键的三个特例

### 特例 1：`SupportAC`

- 自定义 `getConfigEnumType()` 为 `support_disabler_ac`
- 自定义 `getDisplay()` 为 `Text.literal(this.name())`
- 配置 UI 不走 `configenum.*`
- 但模块入口显示当前值，必须有 `module-meta.support-disabler-ac.*`

### 特例 2：`HttpProxyType`

- 默认 type 为 `httpproxytype`
- 自定义 `getDisplay()` 为小写 literal
- 当前没有 `moduleMeta(...)`
- 所以现状下既没有 `configenum.httpproxytype.*`，也没有 `module-meta.httpproxytype.*`

### 特例 3：`MotionMode`

- 定义在 `ElytraExtra`
- 不重写 `getConfigEnumType()`，type 是 `motionmode`
- 实际 `EnumRef` 字段出现在 `ElytraFlight.motionMode`
- 语言 key 是 `configenum.motionmode.*`，不是 `configenum.motion-mode.*`

按这三个特例去校正你的判断，基本就不会把这套系统写偏。