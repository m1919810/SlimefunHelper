---
name: minecraft-text-renderer-analysis
description: Analyze vanilla Minecraft client text rendering around TextRenderer, TextHandler, OrderedText, DrawContext, GuiRenderState, GuiRenderer, and TextCommandRenderer in this merged Yarn tree. Use when tracing text shaping, width calculation, bidi/language reordering, glyph preparation, GUI text batching, or when explaining how OrderedText is rendered.
disable-model-invocation: true
---

# Minecraft Text Renderer Analysis

## Scope
- Primary source scope:
  - `net/minecraft/client/font/**`
  - `net/minecraft/text/**`
  - `net/minecraft/client/gui/**`
  - `net/minecraft/client/render/command/**`
- Supporting scope:
  - `com/mojang/**` when glyph or render plumbing crosses package boundaries
  - `javadoc/**` as semantic index

## Core task
Analyze vanilla Minecraft text rendering as a runtime pipeline, not as isolated methods.

Output in human-readable structure:
- entry points
- text data shapes
- reorder / shaping stage
- width / wrapping stage
- glyph preparation stage
- GUI batching stage
- final render submission stage
- where `OrderedText` is consumed
- how to render `OrderedText` in practice

Do not default to line-by-line code explanation.
Prefer data flow, object roles, stage boundaries, and state evolution.
Only explain specific methods when the user explicitly asks.

## Mental model
Treat the pipeline as 6 stages.

1. source text shape
   - `String`
   - `Text`
   - `StringVisitable`
   - `OrderedText`

2. normalization / reorder
   - plain `String` GUI drawing usually passes through language reorder first
   - `Text` usually becomes `asOrderedText()`
   - right-to-left mirroring is handled for raw string drawing in `TextRenderer.prepare(String, ...)`

3. measurement
   - `TextHandler` computes width, trim, wrapping, line breaks
   - width source is glyph metrics from the active font set

4. glyph preparation
   - `TextRenderer.prepare(OrderedText, ...)`
   - visits each code point through `OrderedText.accept(visitor)`
   - resolves `BakedGlyph`, colors, shadow, bold offset, underline, strikethrough, background box
   - produces drawable glyph rectangles rather than drawing immediately

5. batching / state collection
   - GUI path stores text as `TextGuiElementRenderState`
   - later converts prepared glyph drawables into prepared GUI elements

6. final vertex submission
   - direct world/command path: `TextCommandRenderer -> TextRenderer.draw(...)`
   - GUI path: `GuiRenderer.prepareTextElements()` turns prepared glyph drawables into GUI render elements, then the GUI renderer batches them into buffers

## Main objects and roles
- `OrderedText`
  - final ordered stream of styled code points
  - contract is `accept(visitor)`
  - can be concatenated, mapped, or built from forward/backward visited strings

- `TextHandler`
  - pure measurement and line breaking layer
  - no final rendering
  - depends on a width retriever callback

- `TextRenderer`
  - runtime coordinator between ordered text and glyph drawables
  - owns font access, glyph lookup, bidi mirror for raw strings, and `Drawer`

- `TextRenderer.Drawer`
  - state carrier while visiting characters
  - accumulates x/y cursor, glyph quads, decoration rectangles, empty hit boxes, text bounds, background bounds

- `DrawContext`
  - high-level GUI API
  - accepts `String`, `Text`, `OrderedText`
  - converts them into GUI render-state objects instead of drawing immediately

- `TextGuiElementRenderState`
  - lazy prepared GUI text node
  - stores text, pose, color, shadow, clip, and cached preparation result

- `GuiRenderState`
  - frame-local GUI scene graph / layering container
  - stores text elements and later iterates them for preparation

- `GuiRenderer`
  - consumes GUI state
  - converts text preparation output into prepared draw elements and batches them into buffers

- `TextCommandRenderer`
  - non-GUI direct submit path for text render commands
  - directly calls `TextRenderer.draw(...)` or `drawWithOutline(...)`

## OrderedText data shape
`OrderedText` is not "a string". It is a visitor-driven stream.

Think of it as:
- ordered code points
- each code point carries a `Style`
- traversal order is already decided
- the consumer can stop early by returning `false`

Practical consequences:
- layout code can measure without creating quads
- render code can create quads without reparsing source `Text`
- hit-testing can replay the same stream and inspect glyph bounds

## OrderedText rendering pipeline
Use this exact runtime chain when explaining or debugging.

### Path A: GUI text
1. caller uses `DrawContext.drawText(...)` or `drawTextWithShadow(...)`
2. `DrawContext` wraps the request into `TextGuiElementRenderState`
3. `GuiRenderState.addText(...)` stores it in layered GUI state
4. `GuiRenderer.prepareTextElements()` iterates text elements
5. `state.prepare()` calls `TextRenderer.prepare(orderedText, ...)`
6. `orderedText.accept(drawer)` replays the styled code point stream
7. `Drawer` resolves glyph metrics and accumulates drawable glyph rectangles
8. `GuiRenderer` converts each prepared drawable into prepared GUI elements
9. GUI buffer preparation batches those elements for render pass submission

### Path B: direct text command / world-style text path
1. caller produces a text command with `OrderedText`
2. `TextCommandRenderer.render(...)` iterates commands
3. it calls `TextRenderer.draw(orderedText, ...)`
4. `TextRenderer.prepare(orderedText, ...)` creates a `GlyphDrawable`
5. `GlyphDrawable.draw(GlyphDrawer.drawing(...))`
6. `GlyphDrawer.drawing(...)` fetches a `VertexConsumer` from the target layer
7. each glyph/rectangle emits vertices into that consumer

## How OrderedText is actually consumed
The decisive boundary is `OrderedText.accept(visitor)`.

The consumer supplies a visitor with this conceptual shape:
- input: `(charIndex, style, codePoint)`
- output: continue / stop

In vanilla text rendering, the visitor is usually `TextRenderer.Drawer`.

During traversal, for each code point:
- choose glyph from the font selected by `style.getFont()`
- optionally swap obfuscated glyph variant
- compute advance from glyph metrics and bold state
- compute text color, shadow color, bold offset, shadow offset
- create drawable glyph quad if the glyph has visible geometry
- optionally create underline / strikethrough rectangles
- advance the pen x position
- expand text bounds and background bounds

## Width and wrap logic
When the task is measurement rather than rendering, stay in `TextHandler`.

`TextHandler` responsibilities:
- `getWidth(String | StringVisitable | OrderedText)`
- `trimToWidth(...)`
- `trimToWidthBackwards(...)`
- line end search
- wrapped line collection

Measurement is based on the same font metric source used by rendering, so width and render advance stay aligned.

## Language reorder and bidi split
Distinguish these cases clearly.

- raw `String` GUI drawing
  - often gets language reorder before rendering
  - `DrawContext.drawText(String, ...)` uses `Language.getInstance().reorder(...)`

- `Text`
  - usually becomes `text.asOrderedText()`

- raw `String` direct prepare in `TextRenderer`
  - if current language is RTL, `mirror(...)` applies bidi shaping/reordering before visiting

- already-built `OrderedText`
  - treat it as already ordered input
  - `TextRenderer.prepare(OrderedText, ...)` does not reorder again

## How to render OrderedText in practice
Prefer these two patterns.

### Pattern 1: draw in GUI
Use when rendering inside screens, HUD widgets, or other GUI layers.

Pseudo flow:
- obtain `TextRenderer`
- obtain or build `OrderedText`
- call `DrawContext.drawText(textRenderer, orderedText, x, y, color, shadow)`
- let GUI state and renderer batch it later

Human explanation:
- this path does not immediately push vertices
- it records a text element into GUI render state
- actual glyph preparation happens later in the GUI renderer
- this is the safe default for GUI work

### Pattern 2: direct immediate-style draw
Use when you already own matrix + vertex consumer context and want direct text submission.

Pseudo flow:
- obtain `TextRenderer`
- obtain `OrderedText`
- call `textRenderer.draw(orderedText, x, y, color, shadow, matrix4f, vertexConsumers, layerType, backgroundColor, light)`

Human explanation:
- this path prepares glyph drawables immediately
- then sends glyph geometry into the chosen text render layer
- this is the lower-level path used by text command rendering

## Decision table
- need GUI layering, clipping, hover hit-testing, deferred batching
  - use `DrawContext`
- need direct render submission with your own matrix/light/layer
  - use `TextRenderer.draw(OrderedText, ...)`
- need only width or wrap
  - use `TextHandler`
- need click / hover style probing over rendered text
  - reuse prepared text bounds via `DrawnTextConsumer.handleHover(...)`

## Debug checklist
When text appears wrong, verify in this order.

1. input type
   - `String`, `Text`, or `OrderedText`
2. order source
   - was reorder already done
3. font source
   - what `style.getFont()` selects
4. style effects
   - bold, shadow, underline, strikethrough, obfuscated
5. measurement vs render mismatch
   - compare `TextHandler` width against actual glyph advances
6. render path
   - GUI deferred path vs direct draw path
7. clipping / layering
   - `clipBounds`, GUI layering, or render layer choice

## Local source anchors
Use these files first.
- `net/minecraft/text/OrderedText.java`
- `net/minecraft/client/font/TextHandler.java`
- `net/minecraft/client/font/TextRenderer.java`
- `net/minecraft/client/gui/DrawContext.java`
- `net/minecraft/client/gui/render/state/TextGuiElementRenderState.java`
- `net/minecraft/client/gui/render/state/GuiRenderState.java`
- `net/minecraft/client/gui/render/GuiRenderer.java`
- `net/minecraft/client/render/command/TextCommandRenderer.java`

## Output shape
When answering with this skill, prefer these sections.
- target boundary
- pipeline stages
- main objects
- `OrderedText` role
- actual render path
- practical rendering method
- failure points / caveats