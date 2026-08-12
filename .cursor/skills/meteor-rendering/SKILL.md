---
name: meteor-rendering
description: Explain and modify Meteor Client text rendering, GUI 2D rendering, font loading, custom text size, textured UI pieces, and pseudo-rounded controls. Use when working on meteordevelopment.meteorclient.renderer, renderer.text, gui.renderer, gui.themes.meteor, HUD text, quads, sliders, circles, triangles, or custom font behavior.
disable-model-invocation: true
---

# Meteor Rendering

## 目标
快速理解 Meteor 的两条 2D/UI 渲染链：
- 文字链
- GUI 几何/贴图链

优先输出：模块关系、数据流、尺寸如何传导、有哪些真实能力、哪些效果其实不是通用能力。

## 先看哪里
- 文字抽象入口
  - `src/main/java/meteordevelopment/meteorclient/renderer/text/TextRenderer.java`
  - `src/main/java/meteordevelopment/meteorclient/renderer/text/CustomTextRenderer.java`
  - `src/main/java/meteordevelopment/meteorclient/renderer/text/VanillaTextRenderer.java`
  - `src/main/java/meteordevelopment/meteorclient/renderer/text/Font.java`
  - `src/main/java/meteordevelopment/meteorclient/renderer/Fonts.java`
  - `src/main/java/meteordevelopment/meteorclient/systems/config/Config.java`
- HUD 文字专用链
  - `src/main/java/meteordevelopment/meteorclient/systems/hud/HudRenderer.java`
  - `src/main/java/meteordevelopment/meteorclient/systems/hud/Hud.java`
- GUI 2D 链
  - `src/main/java/meteordevelopment/meteorclient/renderer/Renderer2D.java`
  - `src/main/java/meteordevelopment/meteorclient/gui/renderer/GuiRenderer.java`
  - `src/main/java/meteordevelopment/meteorclient/gui/GuiTheme.java`
  - `src/main/java/meteordevelopment/meteorclient/gui/themes/meteor/MeteorGuiTheme.java`
  - `src/main/java/meteordevelopment/meteorclient/gui/themes/meteor/MeteorWidget.java`
- 贴图打包与 shader
  - `src/main/java/meteordevelopment/meteorclient/gui/renderer/packer/TexturePacker.java`
  - `src/main/java/meteordevelopment/meteorclient/renderer/MeteorRenderPipelines.java`
  - `src/main/resources/assets/meteor-client/shaders/text.vert`
  - `src/main/resources/assets/meteor-client/shaders/text.frag`

## 文字渲染的真实结构

### 1. 统一抽象，不是继承 Minecraft 的字体类
Meteor 自己定义了 `TextRenderer` 接口。
运行时二选一：
- `Config.customFont = true` -> `Fonts.RENDERER` -> `CustomTextRenderer`
- `Config.customFont = false` -> `VanillaTextRenderer.INSTANCE`

这是一层适配，不是替换 Minecraft 全局字体类。

### 2. 自定义字体如何得到“文字图像”
`CustomTextRenderer` 不直接拿现成字图。
它的流程是：
1. `Fonts.load(...)` 选择一个 `FontFace`
2. `FontFace` 读取 `.ttf` 数据到 `ByteBuffer`
3. `Font` 用 `STBTruetype` 把一组字符打包进 2048x2048 单通道纹理
4. 每个字符被记录为：
   - 在纹理中的 UV
   - 在屏幕上的 offset
   - 宽高
   - xAdvance
5. 渲染字符串时，把每个字符展开成一个 quad 写进 mesh
6. `UI_TEXT` pipeline + `text.vert/text.frag` 采样字体纹理并乘颜色

`text.frag` 的关键点：只取纹理的 `r` 通道做 alpha。也就是“白色字形蒙版 * 顶点颜色”。

### 3. 自定义大小的文字如何实现
Meteor 不是任意缩放一张最终位图，而是分两层：
- 先准备多个基础字号的 `Font`
- 再根据请求 scale 做二次比例修正

`CustomTextRenderer.begin(scale, ...)` 会：
1. 把 scale 落到若干档位，挑一个更接近的基础 `Font`
2. 用 `fontScale` 和目标 `scale` 计算最终缩放比
3. `render(...)` 时按这个比率写 quad

意义：
- 减少极端模糊
- 比“拿一个小字图强行放大”更稳定
- 仍然保留连续 scale 的使用方式

### 4. 阴影怎么做
不是特殊 shader。
是同一串字绘制两次：
- 第一次偏移一点，颜色较暗
- 第二次正常位置，颜色正常

### 5. HUD 为什么又有一条单独路径
普通 Meteor 文字接口适合通用调用。
HUD 为了大量同类文本，做了专门优化：
- 按 scale 计算目标字高
- 缓存 `FontHolder`
- 每帧复用对应 mesh
- 帧尾统一提交到 `UI_TEXT`

所以 HUD 的“自定义大小文字”本质上是：
- scale -> 目标 font height
- height -> 缓存/创建 `Font`
- `Font.render(...)` 直接写 mesh

这条链更偏批处理。

### 6. 原版字体路径做了什么
`VanillaTextRenderer` 没有自定义字形生成。
它只是把 Meteor 的调用方式包一层，最终仍然走 `mc.font.drawInBatch(...)`。
自定义大小通过 `scale` 和 matrix / model-view 缩放实现。

## GUI / 2D 渲染的真实结构

### 1. 基础能力只有三类
`Renderer2D` 的核心 primitive：
- line
- triangle
- quad

再分两种通道：
- `COLOR`：纯色几何
- `TEXTURE`：带 UV 的贴图几何

本质都是：
- 往 `MeshBuilder` 写顶点
- 帧内积累
- `MeshRenderer` + 指定 pipeline 提交

### 2. 自定义大小的 2D 色块怎么实现
色块没有“字号”概念，只有几何尺寸。
你传：
- x
- y
- width
- height
- color

`Renderer2D.quad(...)` 直接生成四个顶点。
所以“自定义大小色块”本质就是自定义矩形尺寸，不需要额外缩放系统。

主题层的 `scale(...)` 只是一个 UI 设计尺寸换算器：
- 逻辑尺寸 2 / 3 / 6 等
- 经过 `theme.scale(...)` 变成当前 GUI 像素尺寸

### 3. GUI 文本与几何如何配合
`GuiRenderer` 的顺序是：
1. 开始 colored / textured mesh
2. 控件把 quad、贴图、triangle 记入批次
3. 文本不立刻画，而是先记成 `TextOperation`
4. mesh 提交
5. 再统一用 `theme.textRenderer()` 画普通文本和标题文本

所以 GUI 中文字和几何是两套批处理，再按顺序合成。

### 4. 圆角是怎么实现的
默认 Meteor GUI **没有通用“圆角矩形 primitive”**。
常见做法其实是两类：

#### A. 贴图式圆/圆头
例如：
- slider handle 用 `GuiRenderer.CIRCLE`
- triangle 箭头用 `GuiRenderer.TRIANGLE`

这些贴图在启动时进 `TexturePacker`，再被放进统一 atlas。
渲染时是 textured quad，不是数学意义的实时圆角。

#### B. 方框拼接
很多背景、输入框、按钮边框直接是：
- 中间一块背景 quad
- 上下左右各一条 outline quad

也就是“中间填充 + 四边描边”。
这是一种假圆角之外的纯方框风格。

### 5. 如何判断某个控件是否真圆角
优先按下面顺序判断：
1. 有没有 `quad(..., GuiRenderer.CIRCLE, ...)` 或其他圆形贴图
2. 有没有多个矩形拼接
3. 有没有专门 shader / SDF / arc tessellation

在 Meteor 默认 GUI 里，前两种常见，第三种默认基本不是主路径。

## 调试或修改时的判断模板

### 文字问题
如果用户问“为什么某段字变大/变小/发虚/有阴影”，按这个链路查：
- 这段字走的是 `TextRenderer.get()`、`HudRenderer.text(...)` 还是原版 `mc.font`
- 是否开启 `Config.customFont`
- scale 从哪里来的
- scale 是传给 `begin(...)` 还是 theme 的 `scale(...)`
- 最终是：
  - `Font.render(mesh, ...)` 生成字形 quad
  - 还是 `mc.font.drawInBatch(...)`

### GUI 形状问题
如果用户问“这个 2D 控件怎么变大、怎么改成圆角、怎么换图标”，按这个链路查：
- 控件在主题层哪个 widget
- 它调用的是：
  - `renderer.quad(...)`
  - `renderer.triangle(...)`
  - `renderer.rotatedQuad(...)`
  - `renderer.quad(..., texture, ...)`
- 如果是圆形/箭头/图标，继续查 `GuiRenderer` 静态贴图和 `TexturePacker`
- 如果只是边框/底色，继续查 `MeteorWidget.renderBackground(...)`

## 回答这类问题时的表达方式
优先给出：
- 模块关系图
- 输入到渲染结果的链路
- 哪一步决定尺寸
- 哪一步决定贴图/颜色/阴影
- 是否属于真实通用能力，还是主题层技巧

避免只说“它调用了某个函数”。

## 一个可直接复用的总结模板

```text
目标效果
-> 入口模块
-> 尺寸来源
-> 数据形态（字符串 / quad / 贴图 region）
-> 批处理位置
-> 最终 pipeline / shader
-> 是否是通用 primitive 还是主题技巧
```

## 典型结论
- Meteor 的自定义文字渲染来自 TTF -> STB 打包字形 -> 字体纹理 -> 每字符 quad -> `UI_TEXT` pipeline。
- 自定义大小文字不是单纯缩放最终图片，而是“挑更接近的基础字号 + 再缩放”。
- HUD 文字有单独缓存链，比通用文本更偏批量渲染。
- GUI 的任意大小 2D 色块就是任意尺寸 quad。
- 默认 GUI 的“圆角感”更多来自圆形贴图、三角贴图、边框拼接，不是通用圆角矩形算法。

## 2D 渲染层级

### 1. 最外层入口
Meteor 的 2D 渲染不是直接从某个控件开始，而是先挂在 Minecraft GUI 渲染尾部。
主分支是：
- 当前不是 `WidgetScreen` -> 发 `Render2DEvent`
- 当前是 `WidgetScreen` -> 走 Meteor 自己的 GUI 渲染

可以把它理解成：

```text
Minecraft GUI render tail
-> Meteor 追加 2D
   -> 游戏内 HUD / 模块覆盖层
   -> 或 Meteor 自己的 ClickGUI
```

### 2. 游戏内 2D 层
游戏内覆盖层走 `Render2DEvent`。
这层提供：
- `graphics`
- `screenWidth`
- `screenHeight`
- `frameTime`
- `tickDelta`

HUD 就挂在这层上。

### 3. HUD 层
HUD 在 `Hud.onRender(Render2DEvent)` 中：
- `HudRenderer.begin(event.graphics)`
- 遍历 `HudElement`
- `HudRenderer.end()`

HUD 自己又分成几类内容：
- 纯色几何
- 纹理
- 文字
- postTasks

并且会显式调用 `graphics.nextStratum()`，所以 HUD 不是一层糊到底，而是主动切 stratum。

### 4. WidgetScreen / ClickGUI 层
Meteor 自己的 GUI 走 `WidgetScreen.renderCustom(...)`，时序是：
- `unscaledProjection()`
- `theme.beforeRender()`
- `GuiRenderer.begin(graphics)`
- `root.render(...)`
- `GuiRenderer.end()`
- `renderTooltip(...)`
- debug overlay（可选）
- `scaledProjection()`

所以 ClickGUI 是单独的一条 2D 分支，不和 HUD 共用同一个控件树。

### 5. GuiRenderer 内部层级
`GuiRenderer` 里至少分这些层：
- 纯色几何批次 `r`
- 贴图批次 `rTex`
- 文本操作队列 `texts`
- tooltip
- debug
- scissor 栈

提交顺序是：
1. 几何
2. 贴图
3. 普通文本
4. 标题文本
5. tooltip
6. debug

所以在 ClickGUI 里，文字天然盖在背景和图标之上。

### 6. scissor 是批次边界
scissor 不是附加属性，而是切批次的边界。
进入新 scissor 时，会：
- 必要时先结束当前批次
- 开启 scissor
- 再重新 beginRender()

所以带滚动区、折叠动画、裁剪视图的控件，都会天然形成新的局部层级。

## ClickGUI 的构造方案

### 1. 基本组成
Meteor 的 ClickGUI 不是一个大类硬编码，而是这套组合：

```text
Screen
-> Theme
-> Root container
-> Window / List / Section / View
-> Widget
-> Renderer
```

也就是：
- `WidgetScreen` / `TabScreen` / `WindowScreen`
- `GuiTheme`
- `WWidget` 树
- `GuiRenderer`

### 2. 根节点结构
`WidgetScreen` 内部有一个 `WFullScreenRoot`。
它的作用不是普通列表，而是全屏根画布：
- 宽高 = 整个窗口
- 每个顶层 child 先拿满全屏 cell
- 再由 `alignWidget()` 决定具体对齐方式

这意味着顶层不是“自然流布局”，而是“全屏挂点 + 对齐”。

### 3. 控件树关系
Meteor GUI 的基础关系是：

```text
WWidget
└─ WContainer
   ├─ WWindow
   ├─ WView
   ├─ WVerticalList
   ├─ WHorizontalList
   ├─ WSection
   └─ 各种具体控件
```

布局不是 widget 自己直接决定，而是：

```text
container -> cell -> widget
```

也就是 `Cell` 承担：
- padding
- 对齐
- expand
- child 最终摆放

### 4. Window 是 ClickGUI 的主壳层
`WWindow` 的结构固定是：

```text
WWindow
├─ Header
└─ View
   └─ 真正内容区
```

其中：
- Header 负责标题、图标、折叠箭头、拖拽
- View 负责滚动内容区

并且 `WWindow` 自带：
- 展开 / 折叠
- 右键切换展开
- 左键拖拽标题栏
- 位置持久化
- 展开状态持久化
- 用 scissor 实现折叠动画

### 5. Tab 与 Screen 的关系
Meteor 的主界面不是单窗口路由，而是 Tab 系统。
`Tabs` 在预初始化阶段注册：
- Modules
- Config
- Gui
- Hud
- Friends
- Macros
- Profiles
- 以及可选的 PathManager

`ModulesScreen` 这类界面走 `TabScreen`，`ModuleScreen` 这类详情页走 `WindowScreen`。

### 6. Modules ClickGUI 的构造方式
`ModulesScreen` 不是一个单列表，而是：
- 一个 `WCategoryController`
- 多个分类 `WWindow`
- 一个 Search `WWindow`
- 可选 Favorites `WWindow`
- 左下角帮助文字

也就是：

```text
全屏 controller
-> 多个独立窗口并排/换列
   -> 每个窗口对应一个分类 / Search / Favorites
```

### 7. 分类窗口怎么生成
每个 category window 的生成逻辑是：
- `theme.window(category.name)`
- 可选分类图标
- `view.scrollOnlyWhenMouseOver = true`
- `view.hasScrollBar = false`
- 把当前分类下每个 `module` 转成 `theme.module(module)` 塞进去

所以分类窗口本质是“可拖拽的模块列表容器”。

### 8. Search 窗口怎么生成
Search 也是普通 `WWindow`，内部结构是：

```text
Search Window
├─ TextBox
└─ VerticalList
   ├─ Modules section
   └─ Settings section
```

文本变化时不是局部 patch，而是：
- 清空结果 list
- 按搜索结果重建控件树

### 9. Favorites 窗口怎么生成
Favorites 也是独立 `WWindow`。
只有存在收藏模块时才创建；如果收藏为空，会把整个 favorites window 移除。

### 10. 模块行控件
模块列表的每一行不是普通 label，而是独立控件 `WMeteorModule`。
它自带：
- 左键 toggle
- 右键打开 `moduleScreen(module)`
- 背景动画
- 左侧 accent 条动画
- 文本对齐

所以模块行本质上是一个小型状态控件。

### 11. 模块详情页构造
`ModuleScreen` 是单窗口页面，结构大概是：
- 描述
- addon 来源信息（可选）
- settings 容器
- 模块自定义 widget（可选）
- Bind section
- 底部 active / copy / paste 区

这说明 Meteor 的模块详情页不是写死每个模块 UI，而是：
- 通用 settings 自动展开
- 可选 custom widget 插槽
- 通用底部控制区

### 12. 默认视觉风格的真实来源
默认 ClickGUI 的风格主要来自：
- 纯矩形 quad
- 图标 atlas
- `CIRCLE` / `TRIANGLE` 这类贴图
- 边框拼接
- 文本后置渲染
- 简单状态动画

不是统一的“圆角矩形 DSL”或通用 shader 圆角系统。

## 自由探索时的默认分析顺序
如果用户让你继续深挖 Meteor GUI / 2D，按下面顺序：
1. 先确认是游戏内 2D，还是 `WidgetScreen` 分支
2. 再确认数据走的是 HUD、普通模块 overlay，还是 ClickGUI
3. 再看是几何批次、贴图批次、文本批次中的哪一层
4. 如果是 ClickGUI，再看控件树：root -> window -> view -> widget
5. 如果是视觉效果，再判断它是 primitive、贴图技巧，还是主题层拼装
