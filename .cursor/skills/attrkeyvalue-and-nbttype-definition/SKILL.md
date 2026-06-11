---
name: attrkeyvalue-and-nbttype-definition
description: 理解并维护 SlimefunHelper 中 `NBTType`、`NBTParsable`、`NBTRef`、`AttrKeyValue` 的自动配置链。用于分析或新增 NBT 配置类型时，追踪它如何在字符串、原始对象、NBT、Ref、可编辑 GUI 之间自动转换，并据此实现新的 `NBTParsable` / 组合型配置结构。
disable-model-invocation: true
---

# attrkeyvalue-and-nbttype-definition

## 目标

这个仓库里的 NBTType 配置不是“写个 Codec 就完了”，而是一整条自动链：

- 运行值 `T`
- `Codec<T>` 负责 `T <-> NbtElement`
- `WrapperFactory<String, T>` 负责 `String <-> T`
- `WidgetFactory` 负责 W
- `NBTType<T>` 把两条链和 GUI 工厂绑成一个类型协议
- `NBTParsable<T>` 提供自动注册入口
- `NBTRef<T>` 负责配置系统里的持久化、懒解析和编辑入口
- `AttrKeyValue<T>` 负责把 `Ref<T>` 接成可编辑字段
- `CustomWidgetFactory<T>` 负责把值变成 GUI 控件
- `TypeConvertAttrKeyValue` / `WrapperAttrKeyValue` 负责把已有类型拼成新类型

新增一个 NBT 配置类型时，必须把这条链一次补完整。不要只写 Codec，不要只写 GUI，也不要只写字符串解析。

在AttrKeyValue体系中 玩家gui输入以string的形态传播,经过factory反序列化出对象并通过validators后才会修改原始值， Codec则用于和配置文件中的nbt string进行序列化/反序列化，

## 先看什么

优先阅读这些真实实现：

- `src/main/java/me/matl114/managers/config/NBTType.java`
- `src/main/java/me/matl114/managers/config/NBTParsable.java`
- `src/main/java/me/matl114/managers/config/NBTRef.java`
- `src/main/java/me/matl114/managers/config/Ref.java`
- `src/main/java/me/matl114/managers/config/LazilyRegisterTypeRef.java`
- `src/main/java/me/matl114/managers/config/Config.java`
- `src/main/java/me/matl114/managers/config/Refs.java`
- `src/main/java/me/matl114/utils/config/AttrKeyValue.java`
- `src/main/java/me/matl114/utils/config/BaseAttrKeyValue.java`
- `src/main/java/me/matl114/utils/config/WrapperFactory.java`
- `src/main/java/me/matl114/utils/config/kv/AttrKeyValues.java`
- `src/main/java/me/matl114/utils/config/kv/TypeConvertAttrKeyValue.java`
- `src/main/java/me/matl114/utils/config/kv/WrapperAttrKeyValue.java`
- `src/main/java/me/matl114/gui/McWidgetHelpers.java`
- `src/main/java/me/matl114/gui/complex/config/KeyValueInputWidget.java`
- `src/main/java/me/matl114/hacks/utils/config/NBTTypes.java`

理解样例时，优先看这些：

- `src/main/java/me/matl114/hacks/utils/config/Primitive.java`
- `src/main/java/me/matl114/hacks/utils/config/Holder.java`
- `src/main/java/me/matl114/hacks/utils/config/PrimitiveList.java`
- `src/main/java/me/matl114/hacks/utils/config/EntryPrimitiveMap.java`
- `src/main/java/me/matl114/hacks/utils/config/Vec2.java`
- `src/main/java/me/matl114/hacks/utils/config/TracingOption.java`
- `src/main/java/me/matl114/hacks/modules/combat/PositionPredict.java`

## 整体数据流

### 1. 四种表示层

同一个配置值通常同时存在四种表示：

1. 业务值 `T`
2. 存盘字符串 `String`
3. NBT 结构 `NbtElement`
4. GUI 中可编辑的控件状态

这套系统的核心就是保证这四层之间能自动来回走。

### 2. `NBTType<T>` 是中心枢纽

`NBTType<T>` 持有四样东西：

- `typeName`：持久化类型名
- `typeCodec`：`T <-> NbtElement`
- `customWidgetFactory`：`T -> GUI`
- `stringifyFactory`：`String <-> T`
- `empty`：默认空值模板

它同时负责：

- `parse(NbtElement)`：NBT 转业务值
- `toNbt(T)`：业务值转 NBT
- `createAttrKeyValue(key, value)`：把值接成可编辑配置项
- `generateValueWidget(...)`：生成值输入控件

关键点：

- `Codec` 只解决 `NBT <-> T`
- `WrapperFactory<String, T>` 只解决 `String <-> T`
- `CustomWidgetFactory<T>` 只解决 `AttrKeyValue 创建 GUI`，GUI的行为应该转换为String状态更新向AttrKeyValue
- `AttrKeyValue`负责将用户GUI交互转换来的String反向解析为T，并进行条件验证。如果未能通过验证或者格式错误，则不会更新真实值
- 玩家通过GUI交互->String->T-> Config这样的链路来操作配置项的值
- `NBTType` 把这三者合并成“配置系统可用的完整类型”

### NBTType与配置文件体系的交互
当创建Ref的时候，需要指定类型

如果一个类实现了NBTParsable接口 并且他类里存在静态字段NBTType TYPE 则在创建Ref的时候会自动加载该TYPE

通过读取这个TYPE, NBTRef可以惰性加载nbt字符串为具体实例

## `AttrKeyValue` 在这里到底干什么

### 1. 它不是“值本身”，而是“可编辑值代理”

`AttrKeyValue<T>` 维护的是：

- key 名
- 当前字符串值 `value`
- 真实值 `originValue`
- `String <-> T` 工厂
- 校验器
- 更新监听器
- GUI 工厂

`BaseAttrKeyValue` 的核心工作流是：

1. 用户在输入框里改字符串
2. `valueChange(...)` 更新字符串缓存
3. `validateAndUpdate()` 用 `stringifyFactory.create(getValue())` 解析出 `T`
4. 过 validator
5. 更新 `originValue`
6. 触发 listener，把新值写回 `Ref`

所以：

- GUI 永远主要编辑字符串态
- `AttrKeyValue` 负责把字符串态收敛回真实值
- 配置是否合法也在这里判定

### 3. `CustomWidgetFactory<T>` 决定“怎么编辑”

基础类型默认是文本框或布尔按钮。

复杂类型则可以：

- 直接手写一个组合控件
- 或复用子字段控件拼起来
- 或打开列表编辑子界面
- 或者其他操作，可以自行在CustomWidgetFactory中实现

## 这套系统为什么能“自动生成 GUI”

因为 GUI 不是从业务类反射出来的，而是从 `NBTType.customWidgetFactory` 明确给出的。

### 三种常见 GUI 生成方式

#### 1. 直接复用已有基础控件

适合本质上只是别名 / 包装的类型。

套路：

- `createXMap(...)`
- `createComapFlatMap(...)`

它们会复用底层类型的：

- codec
- stringifyFactory
- customWidgetFactory

并通过 `TypeConvertAttrKeyValue` 把“外层值”和“内层值”互转。

适用场景：

- `String` 包装成 `Pattern`
- `String` 包装成 `Identifier`
- 任何“本质还是单值”的薄包装类型

#### 2. 手写组合控件

适合固定字段个数的小结构。

套路参考：

- `Vec2`
- `TracingOption`
- `PositionPredict.PredictArgument`

做法是：

1. 对每个子字段准备一个 wrapper
2. 用 `TypeConvertAttrKeyValue` 把整个对象投影成“某一个字段”
3. 每个字段继续复用已有基础 `NBTType`
4. 最后把多个子控件拼到一个 `SubScreenWidget`

也就是：

```text
整体对象 T
 -> 某个字段 A 的代理 AttrKeyValue<A>
 -> A 对应已有的输入控件
```

#### 3. 打开列表/映射编辑子界面

适合 list / map / pair-list / array-map 一类组合结构。

套路参考：

- `NBTTypes.createListLke(...)`
- `NBTTypes.createPairLike(...)`
- `NBTTypes.createArrayMapLike(...)`
- `PrimitiveList`
- `EntryPrimitiveMap`
- `BoundedPrimitiveMap`

这类类型一般不是在主界面直接铺开所有输入框，而是：

- 主界面放一个“打开编辑列表”按钮
- 子界面里逐个编辑元素

## `WrapperFactory` 的真实职责

`WrapperFactory<A, B>` 不是仅给字符串用的，它是整个体系的“类型桥”。

常见职责有三类：

### 1. 字符串桥

```text
String <-> T
```

例如：

- `INT_FACTORY`
- `DOUBLE_FACTORY`
- `NBT_FACTORY`
- `STR_LIST_FACTORY`
- `STR_MAP_FACTORY`

### 2. 结构桥

```text
List<W> <-> T
Map<K, V> <-> T
Pair<K1, K2> <-> T
```

例如：

- `WrapperFactory.list(...)`
- `WrapperFactory.map(...)`
- `WrapperFactory.getListMapWrapper()`

### 3. 代理桥

把已有 `AttrKeyValue<Outer>` 投影成 `AttrKeyValue<Inner>`，继续复用已有 GUI。

这里会大量配合：

- `TypeConvertAttrKeyValue`
- `WrapperAttrKeyValue`

## `TypeConvertAttrKeyValue` 和 `WrapperAttrKeyValue` 的分工

### `TypeConvertAttrKeyValue`

用于“基于一个外层对象，投影出一个可编辑内层值”。

典型用途：

- 编辑 record 的单个字段
- 编辑 pair 的 first / second
- 编辑包装对象里的底层 primitive 值

它会：

- 读取 delegate 的当前真实值
- 用 wrapper 投影成目标值
- 用户修改后，再反向写回 delegate

### `WrapperAttrKeyValue`

用于“整个值结构变了，但仍想复用 delegate 的字符串链或生命周期”。

典型用途：

- 把 `AttrKeyValue<TOuter>` 包成 `AttrKeyValue<TInner>`
- 复用 delegate 原本的更新路径
- 改掉 GUI 呈现方式或整体结构表示

### 实际判断规则

- 想编辑“整体对象中的某个局部字段”时，用 `TypeConvertAttrKeyValue`
- 想把“整个值整体换一种视角表达”时，用 `WrapperAttrKeyValue`

## `NBTTypes` 里的组合器怎么选

### 1. `createXMap` / `createComapFlatMap`

适合单值包装。

输入特征：

- 底层只有一个值
- GUI 也只需要一个已有控件
- 不需要列表子界面

### 2. `createListLke`

适合“列表像”结构。

输入特征：

- 本质是 `List<W>`
- 想保存为自己的业务类型 `T`
- GUI 用列表编辑页

### 3. `createPairLike`

适合固定二元结构。

输入特征：

- 本质是 `(K1, K2)`
- 想在一行里并排编辑两个子字段

### 4. `createArrayMapLike`

适合“以 pair 列表存盘、逻辑上当 map 用”的结构。

输入特征：

- 本质是 `Map<K1, K2>`
- 想复用 pair-list 的子界面编辑能力
- 存盘顺序要保留

注意：

- `CodecUtils.arrayMapCodec(...)` 会把 map 存成 pair 列表
- `LinkedHashMap` 用来保序
- 重复 key 会以后项覆盖前项

## primitive 体系的特殊点

这个仓库里的 primitive 不是 Java primitive，而是“可携带自身类型信息的值盒子”。

### `Primitive<T>`

它持有：

- `valueType`
- `value`
- `valueString`

字符串协议是：

```text
<primitive-type>|<value-string>
```

解析时先按前缀找 `NBTTypes.PRIMITIVE_TYPES`，再用对应 `stringifyFactory` 解析值。

这意味着：

- primitive 的字符串协议依赖 `NBTTypes.init()` 扫描注册的 primitive type
- 某个类型想作为 primitive 使用，必须能进 `NBTTypes.PRIMITIVE_TYPES`

### `PrimitiveList<W>`

它本质上是：

- 记录一个 `elementType`
- 再记录 `List<W>`

但为了序列化和编辑，会在运行时来回转成：

- `List<Primitive<W>>`

这样每个元素都带着类型信息或至少与 elementType 协调。

### `EntryPrimitiveMap<T, W>`

它本质上是：

- key 来自某个 registry
- value 来自某个 primitive type
- 逻辑值是 `Map<T, W>`
- 序列化值是 `Map<Holder<T>, Primitive<W>>`

也就是：

- key 先包成 `Holder<T>`
- value 先包成 `Primitive<W>`
- 然后再交给 array-map 组合器统一处理

## `NBTParsable` 时的硬规则

### 1. 必须暴露 `TYPE`

```java
public static final NBTType<Xxx> TYPE = ...;
```

没有它，`NBTParsable.onLoad(Class)` 就无法自动注册。

### 2. `type()` 必须返回这个 `TYPE`

否则 `NBTRef` 校验类型名时会错位。

### 3. `empty` 必须是真实可编辑空值

它不是摆设。

这些地方会依赖它：

- 新增 list 元素
- createEmpty
- 组合型类型初始化默认项
- 某些 map/list GUI 的“新增一项”行为

### 4. `stringifyFactory` 不能只顾着打印

它必须满足：

- 字符串能稳定 round-trip 回原值
- 失败时要抛异常/返回错误，而不是吞掉
- GUI 文本输入态依赖它做合法性校验

### 5. widgetFactory 必须和真实结构一致

如果真实存的是 pair / list / map：

- 优先复用 `NBTTypes` 的组合器
- 不要手写一套和字符串协议不一致的 GUI

### 6. 若类型依赖运行时上下文，默认值里就要把上下文固定下来

例如：

- registry 依赖某个具体 `Registry`
- primitive map/list 依赖 elementType

这类参数必须成为值本体的一部分，不能只存在于 builder 外部。

否则 reload 后无法从持久化数据恢复完整类型。

## 实现新类型时的建议顺序

1. 先确定真实业务值结构
2. 再确定持久化结构是：
   - 单值
   - pair
   - list
   - pair-list
   - array-map
3. 先写好 `Codec`
4. 再写好 `String <-> T` 协议
5. 再决定 GUI：
   - 复用单值控件
   - 组合控件
   - 列表编辑按钮
6. 再封成 `NBTType<T>`
7. 最后做 `NBTParsable`、`TYPE`、`type()`、默认值接入 builder

不要反过来从 GUI 开始硬拼。


## 针对本仓库的实现倾向

- 除非真的需要专门布局，否则优先复用 `NBTTypes` 现成组合器
- 除非需要不同输入体验，否则优先复用已有基础类型的 widgetFactory
- 组合结构优先通过 wrapper 投影子字段，而不是手写独立状态机
- list/map 结构优先复用列表子界面，不要在主界面直接塞满输入框
- 需要保序的 map 一律按 `LinkedHashMap` 语义处理

## 与后续实现直接相关的两个现有样板

### `PrimitiveList`

它展示了：

- 元素类型如何作为值本体的一部分保存
- `List<W>` 如何转成 `List<Primitive<W>>`
- 如何通过列表编辑页复用元素类型的已有 GUI

### `EntryPrimitiveMap`

它展示了：

- key/value 两侧都先包成更基础的可序列化对象
- map 如何转成 array-map 结构保存
- 如何把 registry 上下文和 valueType 上下文带进值本体

如果要继续做“primitive - primitive map”或“primitive pair list”，优先沿着这两个样板扩展，不要重新发明协议。