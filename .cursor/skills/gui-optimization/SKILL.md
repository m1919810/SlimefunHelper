---
name: gui-optimization
description: 在 SlimefunHelper 中新增或改造模块配置屏幕时，统一复用 `WidgetUtils` 的配置屏幕组装链，而不是在模块里重复手写 `DynamicListWidget + RefKeyValueInputWidget`。用于把 `BaseModule` 的 `editableConfig`、显示条件、模块标题、主题色和自定义 widget 接到同一条打开链路上。
disable-model-invocation: true
---

# gui-optimization

## 目标

把模块配置屏幕的职责收口成一条稳定链路：

- 配置来源：`BaseModule.getEditableConfig()`
- 行显隐：`WrapperConfigRef.showPredicate()`
- 扩展区域：`BaseModule.addCustomWidgets(...)`
- 通用组装：`me.matl114.gui.WidgetUtils`
- 打开入口：`ClickGui.openConfigurateScreen(...)` / `MainTasks.openModuleScreen(...)`

结果要求：

- 模块类只负责提供数据、标题、提示、配色
- 屏幕结构在 `WidgetUtils` 统一组装
- 不在各模块里重复拼 `DynamicListWidget`、`SubScreenWidget`、`RefKeyValueInputWidget`

## 先看什么

优先阅读这些文件：

- `src/main/java/me/matl114/gui/WidgetUtils.java`
- `src/main/java/me/matl114/hacks/modules/task/ClickGui.java`
- `src/main/java/me/matl114/hacks/MainTasks.java`
- `src/main/java/me/matl114/hacks/api/BaseModule.java`
- `src/main/java/me/matl114/gui/complex/config/RefKeyValueInputWidget.java`
- `src/main/java/me/matl114/gui/basic/DynamicListWidget.java`
- `src/main/java/me/matl114/gui/basic/DynamicContentWidget.java`
- `src/main/java/me/matl114/gui/basic/SubScreenWidget.java`
- `src/main/java/me/matl114/gui/elements/ColorLabelTextElement.java`

## 现有链路

### 1. 数据层

模块配置屏幕不是自己扫描配置树，而是直接消费 `BaseModule` 已注册好的可编辑项：

- `registeredConfigEditableRefs`
- `getEditableConfig()`
- `addCustomWidgets(...)`

这意味着：

- 配置是否可编辑，应该在模块 builder 注册阶段就确定
- 配置是否显示，应该通过 `show(...)` / `hideConfig()` 等方式挂在 `WrapperConfigRef.showPredicate()` 上
- 屏幕层只负责展示，不负责定义业务显隐规则

### 2. 组装层

统一由 `WidgetUtils.createConfigScreen(...)` 负责把这些数据拼成界面。

默认结构是：

- 一个标题行
- 多个配置行
- 若干模块自定义 widget

每个配置行的宽度固定遵循：

```text
totalWidth = indexWidth + blankWidth + buttonWidth
```

默认布局：

- `indexWidth = 140`
- `blankWidth = 10`
- `buttonWidth = 180`
- `buttonHeight = 18`
- `buttonBlank = 2`

### 3. 单行结构

每个配置行都维持同一模型：

- 外层 `SubScreenWidget`
- 一个 `DisplayWidget` 占位，保证整行 hover / click 区域稳定
- 一个 `RefKeyValueInputWidget` 负责 value 编辑
- 一个 `DynamicContentWidget` 负责根据 `showPredicate` 决定整行是否出现

不要把显隐逻辑塞进子控件内部；行级显隐统一放在 `DynamicContentWidget`。

### 4. 标题和主题

`WidgetUtils` 里把样式拆成两块：

- `ConfigScreenLayout`
- `ConfigScreenPalette`

其中 `ConfigScreenPalette` 只负责四个颜色来源：

- 标题文字色
- 标题背景色
- key 文字色
- key 背景色

这样 `ClickGui` 只需要提供主题色，不需要自己拼具体 widget。

### 5. 打开链路

统一入口顺序：

- 模块列表右键
- `ClickGui.openConfigurateScreen(BaseModule)`
- `WidgetUtils.openModuleConfigScreen(...)`
- `WidgetUtils.createModuleConfigScreen(...)`
- `WidgetUtils.createConfigScreen(...)`
- `CenterScreen`

如果未来有别的地方需要直接打开模块配置，优先走 `MainTasks.openModuleScreen(...)`，不要重新造入口。

## 适用判断

满足以下条件时，优先复用这条链：

1. 配置项来自 `BaseModule` 的注册配置
2. 行内容仍然是“key + value 编辑”模型
3. 只需要换标题、提示、配色或补少量自定义 widget
4. 配置显隐可由 `showPredicate` 表达

只有在以下场景，才考虑另开专门屏幕：

- 配置内容不是 list 结构，而是网格、树、可视画布之类完全不同的交互模型
- 需要完全脱离 `RefKeyValueInputWidget` 的输入协议
- 需要独立的多阶段状态机，而不是静态配置列表

## 改造规则

### 1. 模块里只保留“提供者”职责

模块类应该只提供：

- 模块标题
- 模块描述提示
- 主题色
- `getEditableConfig()`
- `addCustomWidgets(...)`

不要在模块类里直接 new 一整套配置屏幕组件。

### 2. 新屏幕优先走 `WidgetUtils`

如果只是一个模块配置屏幕：

- 直接调用 `WidgetUtils.openModuleConfigScreen(...)`
- 或先 `createModuleConfigScreen(...)`，再由调用方决定何时打开

如果是通用配置列表但不是模块，也优先走 `createConfigScreen(...)` 这一层，把：

- 标题
- tooltip
- 配置行列表
- 自定义 widget 注入器
- layout
- palette

作为参数传进去。

### 3. 自定义区域走 `addCustomWidgets(...)`

模块自己的额外按钮、说明块、状态块，不要插到 `WidgetUtils` 内部做特判。

统一模式是：

- `WidgetUtils` 先铺标题和配置行
- 最后调用 `customWidgets.accept(listWidget::addDrawableChild)`

这样通用层不需要知道模块差异。

### 4. 显隐规则不要在 GUI 层重复判断

如果一个配置项是否显示依赖别的开关：

- 在 builder 阶段挂 `show(...)`
- 屏幕层只读取 `showPredicate`

不要在屏幕里根据 path、key 名、模块名再写一套 if/else。

## 常见错误

### 错误 1

在某个模块里再次复制一份：

- `DynamicListWidget`
- `RefKeyValueInputWidget`
- `ColorLabelTextElement`
- `CenterScreen`

这会把主题、布局、行显隐和扩展位重新分叉。

### 错误 2

把模块特定逻辑写进 `WidgetUtils`

`WidgetUtils` 只接受数据和样式，不应该知道：

- 某个具体模块名
- 某个具体配置 path
- 某个具体按钮业务

模块差异应通过参数和 `addCustomWidgets(...)` 注入。

### 错误 3

把“是否显示”写到 value widget 里面

这会导致：

- 占位区域高度不稳定
- hover / click 热区不一致
- 列表重排逻辑分散

正确做法是整行由 `DynamicContentWidget` 控制是否返回内容。

### 错误 4

为同一主题差异重新写一个新屏幕类

如果差异只有颜色或尺寸：

- 改 `ConfigScreenPalette`
- 改 `ConfigScreenLayout`

不要新建第二套模块配置屏幕。

## 这次抽象后应保持的边界

- `BaseModule`
  - 负责配置注册、显隐条件、自定义扩展点
- `WidgetUtils`
  - 负责统一拼装配置屏幕
- `ClickGui`
  - 负责从模块列表触发打开，并提供当前主题色
- `MainTasks`
  - 负责提供统一外部调用入口

这个边界不要再混回去。