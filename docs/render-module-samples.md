---
name: render-module-samples
description: 说明如何查找 Minecraft 1.21.11 与 Meteor 的渲染参考，并给出本项目渲染模块的最小写法。
disable-model-invocation: true
---

# 渲染模块参考文档与示例

本文面向需要在本项目中新增或修改渲染模块的开发者。重点不是罗列所有渲染 API，而是说明**参考文档在哪里、应该先看什么、如何从参考实现回到本项目**。

本文针对 Minecraft 1.21.11。Meteor 的实现可以作为设计和调用顺序的参考，但不能直接复制导入；当前项目已经通过 `VDrawContext`、`VRender` 和自己的事件系统隔离了部分版本差异。

## 先看哪里

从仓库根目录开始，父目录中有两套最重要的离线参考。

| 目的 | 首选位置 | 用法 |
| --- | --- | --- |
| 查 Minecraft 类、方法和字段的语义 | `..\minecraft-sourcecode-yarn-mapping-1.21.11\javadoc\index.html` | 先用 Javadoc 搜索类，再回到同名 `.java` 确认真实执行流程 |
| 查 Minecraft 1.21.11 的实际实现 | `..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\client\` | 从入口方法向下追调用链，不要只依赖方法摘要 |
| 查 Meteor 的模块生命周期 | `..\meteor-client\src\main\java\meteordevelopment\meteorclient\systems\modules\render\` | 优先阅读 `ESP.java`、`Tracers.java`、`Nametags.java` |
| 查 Meteor 的渲染事件数据 | `..\meteor-client\src\main\java\meteordevelopment\meteorclient\events\render\` | 阅读 `Render3DEvent.java` 和 `Render2DEvent.java` |
| 查 Meteor 的绘制原语 | `..\meteor-client\src\main\java\meteordevelopment\meteorclient\renderer\` | 阅读 `Renderer3D.java`、`Renderer2D.java`、`ShapeMode.java` |
| 查本项目的最终落地方式 | `src\main\java\me\matl114\hacks\modules\render\` | 对照现有模块和 `RenderCollectors`，使用项目自己的事件及版本抽象 |

### Yarn Javadoc 的查找方法

Yarn 工作区附带完整的离线 Javadoc，不需要联网。

1. 打开 `..\minecraft-sourcecode-yarn-mapping-1.21.11\javadoc\index.html`。
2. 搜索类名，例如 `DrawContext`、`MatrixStack`、`VertexConsumer`、`RenderLayer`、`WorldRenderer`。
3. 先看类页中的继承关系、构造方法和公开方法。
4. 对依赖不清楚的类型打开 `class-use` 页面，查看它在哪里被调用。
5. 回到 `..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\...` 下的源文件，确认参数的实际含义、矩阵状态和调用顺序。

Javadoc 适合快速建立 API 轮廓，源代码才是运行时行为的依据。Yarn 工作区的 `AGENT.md` 也明确建议使用 `javadoc/` 加速导航，但必须回到 `.java` 文件确认行为。

如果不想打开浏览器，可以在仓库根目录使用定向搜索：

```powershell
rg -n "class DrawContext|void drawText|void drawTexture" ..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\client\gui\DrawContext.java
rg -n "class WorldRenderer|void render\(|void renderWorld\(" ..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\client\render\WorldRenderer.java
rg -n "class VertexConsumer|vertex\(|color\(|normal\(" ..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\client\render\VertexConsumer.java
```

### Meteor 源码的查找方法

Meteor 更适合回答“一个渲染模块通常如何组织”。推荐按下面的顺序阅读：

1. `events/render/Render3DEvent.java`：确认 3D 事件提供的矩阵、绘制器、`tickDelta` 和坐标偏移。
2. `events/render/Render2DEvent.java`：确认 2D 事件提供的 GUI 绘制上下文、屏幕尺寸和 `tickDelta`。
3. `renderer/Renderer3D.java`：查看 `line`、`box`、`quad` 等原语，以及 `begin`/`render` 的批处理生命周期。
4. `renderer/Renderer2D.java`：查看二维线段、矩形、纹理四边形的写入方式。
5. `systems/modules/render/ESP.java`：最适合参考实体包围盒和 2D 投影。
6. `systems/modules/render/Tracers.java`：最适合参考世界坐标线段、屏幕边缘投影和 2D/3D 双路径。
7. `systems/modules/render/Nametags.java`：最适合参考实体状态收集、距离排序、文本布局和投影后的 GUI 绘制。

Meteor 的源码应该被当作“调用模式样例”，而不是当前项目的基类文档。当前项目没有使用 Meteor 的 `EventHandler`、`Renderer3D` 或 `GuiGraphicsExtractor`。

## 按渲染目标选择参考类

### HUD 和屏幕上的 2D 内容

优先查看：

- Yarn：`net\minecraft\client\gui\DrawContext.java`
- Yarn：`net\minecraft\client\gui\GuiRenderState.java`
- Yarn：`net\minecraft\client\gui\render\GuiRenderer.java`
- Yarn：`net\minecraft\client\render\RenderTickCounter.java`
- 项目：`src\main\java\me\matl114\versioned\api\VDrawContext.java`
- 项目：`src\main\java\me\matl114\hacks\modules\render\IRender2DModule.java`

在 1.21.11 中，`DrawContext` 的 GUI 矩阵是 `Matrix3x2fStack`，并且 GUI 绘制会进入 `GuiRenderState`，不应把旧版本或其他项目中的 `MatrixStack` 用法直接套到 GUI 绘制上。当前项目用 `VDrawContext` 封装这部分差异；新模块优先调用 `VDrawContext` 的方法。

### 世界中的 3D 线、方框和面

优先查看：

- Yarn：`net\minecraft\client\util\math\MatrixStack.java`
- Yarn：`net\minecraft\client\render\VertexConsumer.java`
- Yarn：`net\minecraft\client\render\VertexConsumerProvider.java`
- Yarn：`net\minecraft\client\render\RenderLayer.java`
- Yarn：`net\minecraft\client\render\WorldRenderer.java`
- 项目：`src\main\java\me\matl114\versioned\api\VRender.java`
- 项目：`src\main\java\me\matl114\versioned\impl\Render_v1_21_11.java`
- 项目：`src\main\java\me\matl114\hacks\utils\render\RenderCollectors.java`

需要自己提交顶点时，重点理解 `VertexConsumer` 的 `vertex`、`color`、`normal` 和 `lineWidth` 链式调用，以及 `RenderLayer` 决定的顶点格式和 GPU 状态。若只是画线、方框或实心面，优先使用项目的 `VRender`，避免在模块中重复搭建底层 layer。

### 实体、方块实体和渲染状态

先区分需求属于哪一层：

- 只是在世界上叠加 ESP：从 `WorldRenderer` 的世界渲染入口进入 3D 事件即可。
- 修改实体本身的模型或可见性：查看 Yarn 的 `net\minecraft\client\render\entity\`，再看项目的实体渲染 mixin 和相关事件。
- 修改方块实体模型：查看 Yarn 的 `net\minecraft\client\render\block\entity\`，不要把普通世界叠加绘制误当成方块实体渲染器覆写。
- 修改物品模型或 GUI 物品：分别查看 Yarn 的 `net\minecraft\client\render\item\` 和项目的 `VDrawContext` 物品绘制方法。

`WorldRenderer`、`GameRenderer`、实体渲染器和 GUI 渲染器的职责不同。先确定入口，再选择参考类，通常比从一个“大而全”的渲染器开始阅读更快。

## 本项目的渲染生命周期

### 3D 世界渲染

入口位于 `src\main\java\me\matl114\mixins\events\WorldRendererEvents.java`：

1. Mixin 注入 `WorldRenderer.render` 的返回点。
2. 保存 `positionMatrix`、基础投影矩阵和世界投影矩阵到 `RenderListener`。
3. 用 `positionMatrix` 初始化 `MatrixStack`。
4. 通过 `RenderListener.renderWorldTasks(stack, tickDelta)` 广播 `Render3D`。
5. 模块从 `Render3D` 取出 `event.context().stack()`，执行自己的世界绘制。

事件结构是 `src\main\java\me\matl114\events\impl\Render3D.java`：

```java
public record Render3D(MatrixStack stack, float partialTicks) {}
```

注册监听器的最小形态：

```java
@Override
public void registerAll() {
    super.registerAll();
    registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
}

public void onRender3D(Event<Render3D> event) {
    if (checkNull() || !enable.get()) return;

    MatrixStack matrices = event.context().stack();
    RenderUtils.startDrawVirtual(matrices);
    try {
        renderWorld(matrices, event.context().partialTicks());
    } finally {
        RenderUtils.stopDrawVirtual(matrices);
    }
}
```

上面的生命周期保护来自现有模块，例如 `ChestESP` 和 `ExplosiveESP`。实际绘制可以直接调用 `VRender`：

```java
Vec3d cameraOffset = RenderUtils.getCameraPos().negate();
Vec3d from = firstPosition.add(cameraOffset);
Vec3d to = secondPosition.add(cameraOffset);

VRender.getInstance().createLinesLayer((operation, vertexConsumer) ->
        operation.drawLine(matrices, vertexConsumer, from, to, color));
```

`RenderUtils.getCameraPos().negate()` 的意义是把世界坐标转换到当前世界渲染矩阵使用的相对坐标。不要在不知道矩阵已经包含哪些变换时重复平移相机。

### 2D HUD 渲染

入口位于 `src\main\java\me\matl114\mixins\events\InGameHudEvents.java`：

1. Mixin 注入 `InGameHud.render` 的返回点。
2. 用 `VDrawContext.of(context)` 包装原始 `DrawContext`。
3. 创建新的 GUI 根 layer。
4. 广播 `Render2D(vdraw, tickDelta, hudHidden)`。

事件结构是 `src\main\java\me\matl114\events\impl\Render2D.java`：

```java
public record Render2D(VDrawContext drawContext, float partialTicks, boolean hudHidden) {}
```

固定位置和缩放的 HUD，优先继承 `IRender2DModule`；需要统一颜色、粗体和逐行文本时继承 `IRender2DColoredModule`。基类已经负责：

- 监听 `Render2D` 和 tick 事件；
- 检查模块启用状态、客户端状态和 HUD 隐藏状态；
- `pushMatrix`/`popMatrix`；
- 应用 `WidgetPos` 和缩放；
- 调用子类的 `render2D(VDrawContext, float)`。

一个最小的子类只需要提供更新和绘制逻辑：

```java
public final class SampleHud extends IRender2DColoredModule {
    public SampleHud() {
        super("SampleHud");
    }

    @Override
    public void onUpdate(Event<Void> event) {}

    @Override
    public void render2D(VDrawContext drawContext, float partialTicks) {
        drawText(drawContext, "sample");
    }
}
```

需要自定义坐标时，在自己的局部变换前后使用 `pushMatrix`/`popMatrix`；不要修改全局 GUI 矩阵后继续绘制其他模块。

## 从 Meteor 示例映射到本项目

| Meteor | 当前项目对应物 | 迁移时注意 |
| --- | --- | --- |
| `Render3DEvent.matrices` | `Render3D.context().stack()` | 都表示世界绘制时的矩阵上下文，但事件类型和注入点不同 |
| `Render3DEvent.tickDelta` | `Render3D.context().partialTicks()` | 用于实体位置、动画和插值，不要把它当作固定 tick 时间 |
| `event.renderer.line(...)` | `VRender.getInstance().createLinesLayer(...)` + `operation.drawLine(...)` | 当前项目由版本适配层创建 layer 和提交顶点 |
| `event.renderer.box(...)` | `operation.drawOutlinedBox(...)` 或 `operation.drawSolidBoxQuad(...)` | 根据需要选择线框或实心 layer |
| `Render2DEvent.graphics` | `Render2D.context().drawContext()` | 当前项目是 `VDrawContext`，不是 Meteor 的 GUI 类型 |
| `Renderer2D.COLOR.line(...)` | `VDrawContext` 的 GUI 绘制方法或项目 GUI 工具 | 先确认当前 wrapper 是否已有对应方法 |
| `NametagUtils.to2D(...)` | `RenderUtils.createProjector(...)` | 项目通过保存的世界矩阵和投影矩阵完成屏幕投影 |
| Meteor 模块的事件订阅 | `registerAll()` 中的 `registerListener(...)` | 不要复制 `@EventHandler` 注解 |

Meteor `ESP.java` 展示了“3D 先画包围盒、2D 再投影包围盒”的双路径；`Tracers.java` 展示了世界线和屏幕边缘线；`Nametags.java` 展示了先在 tick 阶段整理实体，再在渲染阶段排序和绘制。当前项目的 `EntityESP`、`MineESP`、`ChestESP` 和 `RenderCollectors` 可以作为本项目版本的对应样例。

## 推荐的编写步骤

1. **先定渲染层**：HUD 选 2D，世界叠加选 3D，实体/方块模型修改则进入对应 renderer 或 mixin。
2. **先查 Yarn Javadoc**：确认类的职责、继承关系和公开方法。
3. **再读 Yarn 源码**：确认矩阵、相机、tick delta、buffer 和 render layer 的真实流向。
4. **再读 Meteor 样例**：寻找相同视觉效果的模块，观察数据收集、投影和批处理方式。
5. **回到项目抽象**：优先使用 `RenderListener`、`VDrawContext`、`VRender` 和 `RenderCollectors`。
6. **把重计算放到 tick**：实体遍历、筛选、状态缓存尽量放在 tick 监听器；渲染回调只做插值和提交。
7. **保护绘制状态**：矩阵、虚拟绘制状态和 collector 都要成对恢复或清空。

## 常见错误

- 把 Meteor 的 `Renderer3D`、`Render3DEvent` 或 `@EventHandler` 直接复制到本项目。
- 把 GUI 的 `DrawContext`/`Matrix3x2fStack` 和世界的 `MatrixStack` 混用。
- 在同一个模块中无条件重复减相机坐标，导致方框随视角漂移。
- 忘记 `pushMatrix`/`popMatrix`，污染后续模块的矩阵状态。
- 使用 `VertexConsumer` 时不匹配 `RenderLayer` 和顶点格式。
- 每一帧扫描所有区块或实体，导致渲染线程承担本应在 tick 阶段完成的工作。
- 只看 Javadoc 方法名，不回到 Yarn 源码确认方法是否只是收集命令、是否延迟到后续阶段提交。
- 复制旧版本教程中的类名；以 `minecraft-sourcecode-yarn-mapping-1.21.11` 当前源码和本项目版本适配层为准。

## 快速索引

- 项目 2D 事件：`src\main\java\me\matl114\events\impl\Render2D.java`
- 项目 3D 事件：`src\main\java\me\matl114\events\impl\Render3D.java`
- 项目事件分发：`src\main\java\me\matl114\events\RenderListener.java`
- 项目 2D 基类：`src\main\java\me\matl114\hacks\modules\render\IRender2DModule.java`
- 项目版本化 GUI API：`src\main\java\me\matl114\versioned\api\VDrawContext.java`
- 项目版本化 3D API：`src\main\java\me\matl114\versioned\api\VRender.java`
- 项目 3D 收集器样例：`src\main\java\me\matl114\hacks\utils\render\RenderCollectors.java`
- 项目实体 ESP 样例：`src\main\java\me\matl114\hacks\modules\render\EntityESP.java`
- Meteor 3D 事件：`..\meteor-client\src\main\java\meteordevelopment\meteorclient\events\render\Render3DEvent.java`
- Meteor 3D 模块：`..\meteor-client\src\main\java\meteordevelopment\meteorclient\systems\modules\render\ESP.java`
- Yarn 1.21.11 Javadoc 首页：`..\minecraft-sourcecode-yarn-mapping-1.21.11\javadoc\index.html`
- Yarn 世界渲染入口：`..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\client\render\WorldRenderer.java`
- Yarn GUI 绘制入口：`..\minecraft-sourcecode-yarn-mapping-1.21.11\net\minecraft\client\gui\DrawContext.java`
