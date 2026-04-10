|||markdown
# SlimefunHelper GUI 组件文档

本模块提供了一套基于 Minecraft 原版 GUI 系统扩展的组件库，支持自定义渲染、事件处理、布局、滚动、拖拽等功能。核心类继承自 `DrawableWidget`，可添加到 `Screen` 或 `SubScreenWidget` 中。

## 核心基类

### `DrawableWidget`
所有组件的基类，继承自 `Element`、`Drawable`、`Selectable`、`Draggable`。

| 方法 | 说明 |
|------|------|
| `DrawableWidget(int x, int y, int dx, int dy)` | 构造，位置和大小 |
| `setTextureScale(float scale)` | 设置纹理缩放比例 |
| `setAlpha(float alpha)` | 设置透明度 (0~1) |
| `setRenderHandler(RenderHandler handler)` | 设置渲染器 |
| `addTo(Screen screen)` | 添加到原版屏幕 |
| `addToSub(SubScreenWidget screen)` | 添加到子屏幕容器 |
| `setPriority(int depth)` | 设置深度（影响渲染和点击顺序） |
| `render0(VDrawContext, int, int, float, boolean)` | 主渲染入口（自动处理变换） |
| `mouseClicked(double, double, int)` / `mouseReleased` | 鼠标事件，需子类实现 |

### `SubScreenWidget`
子屏幕容器，管理内部子组件的位置、渲染顺序和事件分发。

- 通过 `addDrawableChild(DrawableWidget)` 添加子组件。
- 自动处理坐标变换（相对坐标）。
- 维护两个列表：`childrenRenderOrder`（按优先级升序）和 `childrenInteractOrder`（按优先级降序），确保高优先级组件优先获得点击。
- 支持 `setSelected(DrawableWidget)` 和 `getSelected()` 管理焦点。

|||java
SubScreenWidget panel = SubScreenWidget.instance(10, 10, 200, 300);
panel.addDrawableChild(new ExecutableWidget(0, 0, 50, 20));
panel.addTo(screen);
|||

### `ScrollableListWidget`
可滚动列表容器，内部自动生成滚动条和剪裁区域。

- `addScrollingWidget(DrawableWidget widget)` – 添加可滚动子组件
- 自动根据子组件总高度计算是否需要滚动条
- 支持鼠标滚轮滚动，滚动条可拖拽
- 内部使用 `SubScreenWidget` 作为滚动区域边界

|||java
ScrollableListWidget list = new ScrollableListWidget(10, 10, 180, 200);
list.addScrollingWidget(new ExecutableWidget(0, 0, 160, 20));
list.addScrollingWidget(new ExecutableWidget(0, 30, 160, 20));
list.addTo(screen);
|||

### `ContentDelegateWidget<W>`
用于包装任意原版 `Element & Drawable & Selectable` 对象（如原版按钮、文本框），将其作为组件嵌入。

- `setContentDelegate(W delegate)` – 设置被包装的原版组件
- 自动处理坐标缩放和事件转发

|||java
ContentDelegateWidget<ButtonWidget> wrapper = new ContentDelegateWidget<>(10, 10, 100, 20);
wrapper.setContentDelegate(ButtonWidget.builder(Text.literal("Click"), button -> {}).build());
|||

### `DelegateWidget`
包装另一个 `DrawableWidget`，所有方法和属性都委托给内部组件。适合需要动态替换内容的场景。

### `ExecutableWidget`
可交互组件，通过 `ElementHandler` 处理鼠标、键盘、滚动事件。

- `setElementHandler(ElementHandler handler)` – 设置事件处理器
- 支持拖拽（`startDrag`/`releaseDrag`/`isDragging`）
- 内置 `InputHandler` 接口，可组合多个处理器

|||java
ExecutableWidget btn = ExecutableWidget.instance(10, 10, 100, 30);
btn.setElementHandler(new AbstractElement() {
@Override
public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
System.out.println("clicked");
return true;
}
});
|||

### `DisplayWidget`
空组件，不响应任何事件，仅用于占位或渲染。

## 渲染与事件接口

### `RenderHandler`
渲染接口，负责在组件局部坐标中绘制内容。

- `renderAtCentered(...)` – 在组件变换后的坐标系中绘制（原点为组件左上角）
- `renderExtraAbsoluteCoord(...)` – 在屏幕绝对坐标中绘制（常用于工具提示）
- `canBeSelected(DrawableWidget)` – 是否可被高亮选中

内置工厂方法：
- `ofMatchingElement(Identifier)` – 根据组件纹理大小自动适配 UV
- `ofResource(Identifier, float)` – 按固定像素大小绘制
- `ofScrollableText(Text, int)` – 可滚动文本（长文本自动滚动）
- `ofAutoScaleText(Text, int)` – 自动缩放文本以适配组件大小
- `ofSingleItem(Supplier<ItemStack>, int, int, boolean)` – 绘制单个物品

|||java
widget.setRenderHandler(RenderHandler.ofMatchingElement(new Identifier("textures/gui/container.png"), 0, 0, 256, 256));
|||

### `TooltipHandler`
工具提示渲染器，继承自 `RenderHandler`，在 `renderExtraAbsoluteCoord` 中根据鼠标悬停显示提示文本。

|||java
TooltipHandler tip = TooltipHandler.of(List.of(Text.literal("这是一个提示")));
widget.setRenderHandler(tip); // 或通过 AbstractElement 组合
|||

### `ElementHandler`
扩展 `RenderHandler` 和 `InputHandler`，用于处理用户交互。

|||java
ElementHandler handler = new AbstractElement() {
@Override
public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
// 处理点击
return true;
}
};
btn.setElementHandler(handler);
|||

### `AbstractElement`
`ElementHandler` 的简易实现，支持组合多个 `RenderHandler`、`InputHandler` 和工具提示。

- `combineRender(RenderHandler)` – 添加额外渲染器
- `combineAbsoluteRender(RenderHandler)` – 添加绝对坐标渲染器
- `withInputHandler(InputHandler)` – 添加输入处理器
- `withTooltips(TooltipHandler)` – 添加工具提示

## 内置组件

### `SlotElement`
物品槽组件，显示一个物品，支持点击回调。

|||java
SlotElement slot = SlotElement.instance(new ItemStack(Items.DIAMOND))
.setSlotFrame(true)      // 显示槽位边框
.setInSlot(true);        // 显示物品数量文字
slot.addToSub(panel);
|||

### `PlateElement`
带边框的空白面板，可设置是否拦截点击。

|||java
PlateElement plate = PlateElement.catchInteract(); // 拦截点击事件
|||

### `RawTextElement`
显示单行文本，支持缩放和颜色。

|||java
RawTextElement text = RawTextElement.instance(Text.literal("Hello"))
.setColor(Colors.RED)
.setAlignment(-1);  // -1左对齐, 0居中, 1右对齐
|||

### `LabelElement`
带背景框的文本标签。

### `MultiLineTextElement`
自动换行的多行文本。

### `IconElement`
图标按钮，根据激活状态显示不同纹理。

|||java
IconElement icon = IconElement.statedGui(
new Identifier("mod", "active"),
new Identifier("mod", "inactive"),
(btn, x, y, b) -> { System.out.println("click"); return true; }
);
icon.setActive(true);
|||

### `ButtonElement`
Minecraft 风格按钮，包含文本标签。

|||java
ButtonElement btn = new ButtonElement(TextProvider.of(Text.literal("确认")), (e, x, y, b) -> true);
|||

### `ScrollElement`
滚动条组件，通常由 `ScrollableListWidget` 内部使用，也可单独使用。

## 组合使用示例

|||java
SubScreenWidget panel = SubScreenWidget.instance(50, 50, 200, 300);
panel.setTextureScale(2.0f); // 整体缩放

ExecutableWidget btn = ExecutableWidget.instance(10, 10, 80, 20);
btn.setElementHandler(new AbstractElement() {
@Override
public void renderCentered0(DrawableWidget element, VDrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
context.drawCenteredTextWithShadow(mc.textRenderer, "Click", 40, 5, 0xFFFFFF);
}
@Override
public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
// 处理点击
return true;
}
});

SlotElement slot = SlotElement.instance(new ItemStack(Items.EMERALD))
.withTooltips(TooltipHandler.of(List.of(Text.literal("绿宝石"))));

panel.addDrawableChild(btn);
panel.addDrawableChild(slot);
panel.addTo(screen);
|||

## 注意事项

- 所有组件的坐标都是相对于父容器的，`SubScreenWidget` 会自动处理子组件的坐标变换。
- `textureScale` 会影响子组件的渲染大小和鼠标点击坐标换算，谨慎使用。
- 组件优先级 `priority` 越高，渲染越靠前，点击越优先（通过 `childrenInteractOrder` 降序排列）。
- 拖拽操作需要组件实现 `Draggable` 接口（`DrawableWidget` 已实现）。
  |||