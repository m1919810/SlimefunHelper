package me.matl114.gui.elements;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.interfaces.InputElement;
import me.matl114.renderer.TextRenderers;
import me.matl114.renderer.text.TextDrawer;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

@Accessors(chain = true)
public class TextFieldElement extends AbstractElement implements InputElement, TextFieldAccess {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final long DOUBLE_CLICK_INTERVAL = 250L;
    private static final int DEFAULT_EDITABLE_COLOR = -2039584;
    private static final String HORIZONTAL_CURSOR = "_";
    public static final Style PLACEHOLDER_STYLE = Style.EMPTY.withColor(Formatting.DARK_GRAY);
    public static final Style SEARCH_STYLE = Style.EMPTY.withFormatting(Formatting.GRAY, Formatting.ITALIC);

    private Text message;
    private ValueAccessor<String> text = ValueAccessor.holder("");
    private int maxLength = 32;
    private boolean drawsBackground = true;
    private boolean focusUnlocked = true;
    private boolean invertSelectionBackground = true;
    private int firstCharacterIndex;
    private int selectionStart;
    private int selectionEnd;
    private ColorSampler backgroundColor = ColorSampler.of(-16777216);
    private ColorSampler textColor = ColorSampler.of(DEFAULT_EDITABLE_COLOR);
    private final List<Formatter> formatters = new ArrayList<>();
    private Text placeholder;
    private long lastSwitchFocusTime = Util.getMeasuringTimeMs();

    @Getter
    private int textX;

    @Getter
    private int textY;

    @Getter
    private TextAlign align = TextAlign.LEFT;

    private boolean draggingSelection;
    private boolean focused;
    private ColorProvider borderColorProvider;
    private long lastClickTime;
    private int lastClickCursor = -1;
    private int lastClickButton = -1;

    public static TextFieldElement instance() {
        return new TextFieldElement();
    }

    public static TextFieldElement instance(Text message) {
        return new TextFieldElement(message);
    }

    public static <T> TextFieldElement ofValue(Value<T> value) {
        TextFieldElement element =
                new TextFieldElement(ValueAccessor.of(() -> value == null ? "" : value.getInput(), input -> {
                    if (value != null) {
                        value.setInput(input);
                    }
                }));
        element.setMaxLength(32768);
        if (value != null) {
            element.setBorderColorProvider(McWidgetHelpers.getWrongRedTextBoxColorProvider(value::isValidate));
        }
        return element;
    }

    public TextFieldElement() {
        this(Text.empty());
    }

    public TextFieldElement(Text message) {
        this.message = message == null ? Text.empty() : message;
        this.updateTextPosition();
    }

    public TextFieldElement(ValueAccessor<String> text) {
        this.text = text == null ? ValueAccessor.holder("") : text;
        this.message = Text.empty();
        this.updateTextPosition();
    }

    public Text getMessage() {
        return this.message;
    }

    public TextFieldElement setMessage(Text message) {
        this.message = message == null ? Text.empty() : message;
        return this;
    }

    public TextFieldElement setText(String text) {
        this.setTextValue(text, true);
        return this;
    }

    public String getText() {
        String currentText = this.text.getValue();
        currentText = currentText == null ? "" : currentText;
        this.selectionStart = MathHelper.clamp(this.selectionStart, 0, currentText.length());
        this.selectionEnd = MathHelper.clamp(this.selectionEnd, 0, currentText.length());
        this.firstCharacterIndex = MathHelper.clamp(this.firstCharacterIndex, 0, currentText.length());
        return currentText;
    }

    public String getSelectedText() {
        int i = Math.min(this.selectionStart, this.selectionEnd);
        int j = Math.max(this.selectionStart, this.selectionEnd);
        return this.getText().substring(i, j);
    }

    public TextFieldElement setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        String currentText = this.getText();
        if (currentText.length() > this.maxLength) {
            this.text.setValue(currentText.substring(0, this.maxLength));
            this.selectionStart = Math.min(this.selectionStart, this.maxLength);
            this.selectionEnd = Math.min(this.selectionEnd, this.maxLength);
            this.updateFirstCharacterIndex(this.selectionStart);
            this.updateTextPosition();
        }
        return this;
    }

    public void setBorderColorProvider(ColorProvider provider) {
        this.borderColorProvider = provider;
    }

    public TextFieldElement setBackgroundColor(ColorSampler backgroundColor) {
        this.backgroundColor = backgroundColor == null ? ColorSampler.of(-16777216) : backgroundColor;
        return this;
    }

    public TextFieldElement setTextColor(ColorSampler textColor) {
        this.textColor = textColor == null ? ColorSampler.of(DEFAULT_EDITABLE_COLOR) : textColor;
        return this;
    }

    public TextFieldElement setDrawsBackground(boolean drawsBackground) {
        this.drawsBackground = drawsBackground;
        this.updateTextPosition();
        return this;
    }

    public TextFieldElement setFocusUnlocked(boolean focusUnlocked) {
        this.focusUnlocked = focusUnlocked;
        return this;
    }

    public TextFieldElement setPlaceholder(Text placeholder) {
        if (placeholder == null) {
            this.placeholder = null;
        } else {
            boolean emptyStyle = placeholder.getStyle().equals(Style.EMPTY);
            this.placeholder = emptyStyle ? placeholder.copy().fillStyle(PLACEHOLDER_STYLE) : placeholder;
        }
        return this;
    }

    public TextFieldElement setEditableColor(int editableColor) {
        return this.setTextColor(ColorSampler.of(editableColor));
    }

    public TextFieldElement setCursorToEnd(boolean shiftKeyPressed) {
        this.setCursor(this.getText().length(), shiftKeyPressed);
        return this;
    }

    public TextFieldElement setCursorToStart(boolean shiftKeyPressed) {
        this.setCursor(0, shiftKeyPressed);
        return this;
    }

    public TextFieldElement setAlign(TextAlign align) {
        if (align == null) {
            throw new IllegalArgumentException("align must be -1, 0, or 1");
        }
        this.align = align;
        this.updateTextPosition();
        return this;
    }

    public TextFieldElement setInvertSelectionBackground(boolean invertSelectionBackground) {
        this.invertSelectionBackground = invertSelectionBackground;
        return this;
    }

    public TextFieldElement addFormatter(Formatter formatter) {
        if (formatter != null) {
            this.formatters.add(formatter);
        }
        return this;
    }

    public TextFieldElement resetSelection() {
        this.selectionEnd = this.selectionStart;
        this.updateTextPosition();
        return this;
    }

    public int getCursor() {
        this.getText();
        return this.selectionStart;
    }

    protected void syncWidgetState(DrawableWidget element) {
        getText();
        boolean nextFocused = element.isFocused();
        if (this.focusUnlocked || nextFocused) {
            if (this.focused != nextFocused) {
                this.focused = nextFocused;
                if (nextFocused) {
                    this.lastSwitchFocusTime = Util.getMeasuringTimeMs();
                } else {
                    this.resetSelection();
                }
            }
        }
        this.updateTextPosition();
    }

    protected double translateMouseX(DrawableWidget element, double mouseX) {
        return (mouseX - element.getX()) / element.getTextureScale();
    }

    protected double translateMouseY(DrawableWidget element, double mouseY) {
        return (mouseY - element.getY()) / element.getTextureScale();
    }

    protected int translateRenderMouseX(DrawableWidget element, int mouseX) {
        return (int) translateMouseX(element, mouseX);
    }

    protected int translateRenderMouseY(DrawableWidget element, int mouseY) {
        return (int) translateMouseY(element, mouseY);
    }

    protected boolean isActive() {
        return this.focused;
    }

    @Override
    public boolean isPreparingInput() {
        return this.focused;
    }

    protected boolean drawsBackground() {
        return this.drawsBackground;
    }

    protected int getInnerWidth(int textureWidth) {
        return this.drawsBackground ? Math.max(0, textureWidth - 8) : textureWidth;
    }

    protected void write(String value) {
        int i = Math.min(this.selectionStart, this.selectionEnd);
        int j = Math.max(this.selectionStart, this.selectionEnd);
        String currentText = this.getText();
        int k = this.maxLength - currentText.length() - (i - j);
        if (k <= 0) {
            return;
        }
        String string = StringHelper.stripInvalidChars(value);
        int l = string.length();
        if (k < l) {
            if (k > 0 && Character.isHighSurrogate(string.charAt(k - 1))) {
                --k;
            }
            string = string.substring(0, Math.max(0, k));
            l = string.length();
        }
        String string2 = new StringBuilder(currentText).replace(i, j, string).toString();
        this.text.setValue(string2);
        this.setSelectionStart(i + l);
        this.setSelectionEnd(this.selectionStart);
        this.updateTextPosition();
    }

    protected void erase(int offset, boolean words) {
        if (words) {
            this.eraseWords(offset);
        } else {
            this.eraseCharacters(offset);
        }
    }

    public void eraseWords(int wordOffset) {
        if (this.getText().isEmpty()) {
            return;
        }
        if (this.selectionEnd != this.selectionStart) {
            this.write("");
        } else {
            this.eraseCharactersTo(this.getWordSkipPosition(wordOffset));
        }
    }

    public void eraseCharacters(int characterOffset) {
        this.eraseCharactersTo(this.getCursorPosWithOffset(characterOffset));
    }

    public void eraseCharactersTo(int position) {
        String currentText = this.getText();
        if (currentText.isEmpty()) {
            return;
        }
        if (this.selectionEnd != this.selectionStart) {
            this.write("");
            return;
        }
        int i = Math.min(position, this.selectionStart);
        int j = Math.max(position, this.selectionStart);
        if (i == j) {
            return;
        }
        String string = new StringBuilder(currentText).delete(i, j).toString();
        this.text.setValue(string);
        this.setCursor(i, false);
        this.updateTextPosition();
    }

    public int getWordSkipPosition(int wordOffset) {
        return this.getWordSkipPosition(wordOffset, this.getCursor());
    }

    protected int getWordSkipPosition(int wordOffset, int cursorPosition) {
        return this.getWordSkipPosition(wordOffset, cursorPosition, true);
    }

    protected int getWordSkipPosition(int wordOffset, int cursorPosition, boolean skipOverSpaces) {
        int index = cursorPosition;
        boolean backward = wordOffset < 0;
        int count = Math.abs(wordOffset);
        for (int i = 0; i < count; ++i) {
            if (!backward) {
                String currentText = this.getText();
                int length = currentText.length();
                index = currentText.indexOf(32, index);
                if (index == -1) {
                    index = length;
                } else {
                    while (skipOverSpaces && index < length && currentText.charAt(index) == ' ') {
                        ++index;
                    }
                }
            } else {
                while (skipOverSpaces && index > 0 && this.getText().charAt(index - 1) == ' ') {
                    --index;
                }
                while (index > 0 && this.getText().charAt(index - 1) != ' ') {
                    --index;
                }
            }
        }
        return index;
    }

    protected void moveCursor(int offset, boolean shiftKeyPressed) {
        this.setCursor(this.getCursorPosWithOffset(offset), shiftKeyPressed);
    }

    protected int getCursorPosWithOffset(int offset) {
        return Util.moveCursor(this.getText(), this.selectionStart, offset);
    }

    protected void setCursor(int cursor, boolean select) {
        this.setSelectionStart(cursor);
        if (!select) {
            this.setSelectionEnd(this.selectionStart);
        }
        this.updateTextPosition();
    }

    protected void setSelectionStart(int cursor) {
        this.selectionStart = MathHelper.clamp(cursor, 0, this.getText().length());
        this.updateFirstCharacterIndex(this.selectionStart);
    }

    protected void setSelectionEnd(int index) {
        this.selectionEnd = MathHelper.clamp(index, 0, this.getText().length());
        this.updateFirstCharacterIndex(this.selectionEnd);
    }

    public boolean setTextValue(String value, boolean moveCursorToEnd) {
        String next = value == null ? "" : value;
        String boundedText = next.length() > this.maxLength ? next.substring(0, this.maxLength) : next;
        this.text.setValue(boundedText);
        if (moveCursorToEnd) {
            this.setCursorToEnd(false);
            this.setSelectionEnd(this.selectionStart);
        } else {
            this.selectionStart = Math.min(this.selectionStart, boundedText.length());
            this.selectionEnd = Math.min(this.selectionEnd, boundedText.length());
            this.updateFirstCharacterIndex(this.selectionStart);
            this.updateFirstCharacterIndex(this.selectionEnd);
        }
        this.updateTextPosition();
        return true;
    }

    protected boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.focused) {
            return false;
        }
        if (mc.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            return true;
        }
        boolean ctrlOrCmd = hasCtrlOrCmd(modifiers);
        boolean shift = hasShift(modifiers);
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                this.erase(-1, ctrlOrCmd);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                this.erase(1, ctrlOrCmd);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (ctrlOrCmd) {
                    this.setCursor(this.getWordSkipPosition(1), shift);
                } else {
                    this.moveCursor(1, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (ctrlOrCmd) {
                    this.setCursor(this.getWordSkipPosition(-1), shift);
                } else {
                    this.moveCursor(-1, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.setCursor(0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.setCursor(this.getText().length(), shift);
                return true;
            }
            default -> {
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_A) {
                    this.setCursor(this.getText().length(), false);
                    this.setSelectionEnd(0);
                    return true;
                }
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_C) {
                    mc.keyboard.setClipboard(this.getSelectedText());
                    return true;
                }
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_V) {
                    this.write(mc.keyboard.getClipboard());
                    return true;
                }
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_X) {
                    mc.keyboard.setClipboard(this.getSelectedText());
                    this.write("");
                    return true;
                }
                return false;
            }
        }
    }

    protected boolean handleCharTyped(char chr) {
        if (!this.isActive()) {
            return false;
        }
        if (!StringHelper.isValidChar(chr)) {
            return false;
        }
        this.write(Character.toString(chr));
        return true;
    }

    protected int calculateCursorPos(double mouseX, int textureWidth) {
        int innerX = MathHelper.clamp(MathHelper.floor(mouseX) - this.textX, 0, this.getInnerWidth(textureWidth));
        String string = this.getText().substring(this.firstCharacterIndex);
        return this.firstCharacterIndex
                + TextRenderers.get().trimToWidth(string, innerX).length();
    }

    protected void selectWord(int cursor) {
        int start = this.getWordSkipPosition(-1, cursor);
        int end = this.getWordSkipPosition(1, cursor);
        this.setCursor(start, false);
        this.setCursor(end, true);
    }

    public boolean canStartDrag(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseY >= 0;
    }

    protected boolean canStartDrag(DrawableWidget element, double mouseX, double mouseY) {
        return mouseX >= element.getX()
                && mouseY >= element.getY()
                && mouseX < element.getX() + element.getWidth()
                && mouseY < element.getY() + element.getHeight();
    }

    protected boolean handleMouseClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        syncWidgetState(element);
        if (button != 0) {
            return false;
        }
        double translatedMouseX = translateMouseX(element, mouseX);
        double translatedMouseY = translateMouseY(element, mouseY);
        if (!this.canStartDrag(element, mouseX, mouseY)) {
            return false;
        }
        int cursor = this.calculateCursorPos(translatedMouseX, element.getTextureWidth());
        long now = Util.getMeasuringTimeMs();
        boolean doubled = this.lastClickButton == button
                && this.lastClickCursor == cursor
                && now - this.lastClickTime <= DOUBLE_CLICK_INTERVAL;
        if (doubled) {
            this.selectWord(cursor);
        } else {
            this.setCursor(cursor, ScreenUtils.hasShiftDown());
        }
        this.lastClickTime = now;
        this.lastClickCursor = cursor;
        this.lastClickButton = button;
        return true;
    }

    public void dragSelect(int mouseX, int mouseY, boolean shiftDownAction) {
        this.dragSelect(mouseX, mouseY, shiftDownAction, Integer.MAX_VALUE);
    }

    protected void dragSelect(int mouseX, int mouseY, boolean shiftDownAction, int textureWidth) {
        int innerX = MathHelper.clamp(mouseX - this.textX, 0, this.getInnerWidth(textureWidth));
        String string = TextRenderers.get()
                .trimToWidth(this.getText().substring(this.firstCharacterIndex), this.getInnerWidth(textureWidth));
        this.setCursor(
                TextRenderers.get().trimToWidth(string, innerX).length() + this.firstCharacterIndex, shiftDownAction);
    }

    @Override
    public void resetSelect() {
        resetSelection();
    }

    protected void drawSelection(DrawContext context, int x1, int y1, int x2, int y2, boolean invert) {
        if (invert) {
            context.fill(RenderLayer.getGui(), x1, y1, x2, y2, -1);
        }

        context.fill(RenderLayer.getGuiTextHighlight(), x1, y1, x2, y2, -16776961);
    }

    @Override
    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        syncWidgetState(element);
        TextDrawer textDrawer = context.getTextDrawer();
        this.updateFirstCharacterIndex(this.selectionStart, element.getTextureWidth(), textDrawer);
        this.updateTextPosition(element.getTextureWidth(), element.getTextureHeight(), textDrawer);
        int translatedMouseX = translateRenderMouseX(element, mouseX);
        int translatedMouseY = translateRenderMouseY(element, mouseY);
        var drawContext = context.pushMatrix();
        try {
            if (this.drawsBackground()) {
                McWidgetHelpers.drawTextWidgetBox(
                        element,
                        drawContext,
                        0,
                        0,
                        element.getTextureWidth(),
                        element.getTextureHeight(),
                        this.focused,
                        this.borderColorProvider == null
                                ? McWidgetHelpers.getDefaultTextBoxColorProvider()
                                : this.borderColorProvider,
                        this.backgroundColor);
            }

            this.renderInputBackgroundOverlay(element, context, translatedMouseX, translatedMouseY);
            int color = this.textColor.getColorInt();
            int cursorOffset = this.selectionStart - this.firstCharacterIndex;
            String visibleText = textDrawer.trimToWidth(
                    this.getText().substring(this.firstCharacterIndex), this.getInnerWidth(element.getTextureWidth()));
            boolean cursorInVisibleRange = cursorOffset >= 0 && cursorOffset <= visibleText.length();
            boolean showBlink = this.focused
                    && (Util.getMeasuringTimeMs() - this.lastSwitchFocusTime) / 300L % 2L == 0L
                    && cursorInVisibleRange;
            int drawX = this.textX;
            int selectionOffset =
                    MathHelper.clamp(this.selectionEnd - this.firstCharacterIndex, 0, visibleText.length());
            if (!visibleText.isEmpty()) {
                String beforeCursor = cursorInVisibleRange ? visibleText.substring(0, cursorOffset) : visibleText;
                OrderedText orderedText = this.format(beforeCursor, this.firstCharacterIndex);
                context.drawText(textDrawer, orderedText, drawX, this.textY, color, false);
                drawX += textDrawer.getWidth(orderedText) + 1;
            }

            boolean hasMoreChars = this.selectionStart < this.getText().length()
                    || this.getText().length() >= this.maxLength;
            int cursorX = drawX;
            if (!cursorInVisibleRange) {
                cursorX = cursorOffset > 0 ? this.textX + element.getTextureWidth() : this.textX;
            } else if (hasMoreChars) {
                --cursorX;
                --drawX;
            }

            if (!visibleText.isEmpty() && cursorInVisibleRange && cursorOffset < visibleText.length()) {
                context.drawText(
                        textDrawer,
                        this.format(visibleText.substring(cursorOffset), this.selectionStart),
                        drawX,
                        this.textY,
                        color,
                        false);
            }

            if (this.placeholder != null && visibleText.isEmpty() && !this.focused) {
                context.drawText(textDrawer, this.placeholder.asOrderedText(), drawX, this.textY, color, false);
            }

            if (selectionOffset != cursorOffset) {
                int selectionX = this.textX + textDrawer.getWidth(visibleText.substring(0, selectionOffset));
                drawSelection(
                        drawContext,
                        Math.min(cursorX, element.getTextureWidth()),
                        this.textY - 1,
                        Math.min(selectionX - 1, element.getTextureWidth()),
                        this.textY + 10,
                        this.invertSelectionBackground);
            }

            if (showBlink) {
                if (hasMoreChars) {
                    context.fill(cursorX, this.textY - 1, cursorX + 1, this.textY + 10, color);
                } else {
                    context.drawText(textDrawer, HORIZONTAL_CURSOR, cursorX, this.textY, color, false);
                }
            }

        } finally {
            context.popMatrix();
        }
    }

    protected void renderInputBackgroundOverlay(DrawableWidget element, VDrawContext context, int mouseX, int mouseY) {}

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return this.handleMouseClick(element, mouseX, mouseY, button);
    }

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
        if (type == Type.MOUSE_CLICK) {
            return this.handleMouseClick(element, mouseX, mouseY, button);
        }
        if (super.onAction(element, mouseX, mouseY, button, type)) {
            return true;
        }
        syncWidgetState(element);
        this.getText();
        double translatedMouseX = translateMouseX(element, mouseX);
        double translatedMouseY = translateMouseY(element, mouseY);
        return switch (type) {
            case MOUSE_RELEASE -> {
                boolean wasDragging = this.draggingSelection;
                this.draggingSelection = false;
                yield wasDragging;
            }
            case MOUSE_START_DRAG -> {
                this.draggingSelection = button == 0 && this.canStartDrag(element, mouseX, mouseY);
                yield this.draggingSelection;
            }
            case MOUSE_DRAG -> {
                if (this.draggingSelection) {
                    this.dragSelect((int) translatedMouseX, (int) translatedMouseY, true, element.getTextureWidth());
                    yield true;
                }
                yield false;
            }
            case MOUSE_RELEASE_DRAG -> {
                boolean wasDragging = this.draggingSelection;
                this.draggingSelection = false;
                yield wasDragging;
            }
            default -> {
                yield false;
            }
        };
    }

    @Override
    public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
        syncWidgetState(widget);
        if (isPress && this.handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.onKey(widget, keyCode, scanCode, modifiers, isPress);
    }

    @Override
    public boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
        syncWidgetState(widget);
        if (this.handleCharTyped(chr)) {
            return true;
        }
        return super.onTyped(widget, chr, modifiers);
    }

    protected OrderedText format(String value, int firstCharacterIndex) {
        for (Formatter formatter : this.formatters) {
            OrderedText orderedText = formatter.format(value, firstCharacterIndex);
            if (orderedText != null) {
                return orderedText;
            }
        }
        return OrderedText.styledForwardsVisitedString(value, Style.EMPTY);
    }

    protected void updateTextPosition() {}

    protected void updateTextPosition(int textureWidth, int textureHeight) {
        updateTextPosition(textureWidth, textureHeight, TextRenderers.get());
    }

    private void updateTextPosition(int textureWidth, int textureHeight, TextDrawer textDrawer) {
        String currentText = this.getText();
        String visibleText = textDrawer.trimToWidth(
                currentText.substring(this.firstCharacterIndex), this.getInnerWidth(textureWidth));
        int leftPadding = this.drawsBackground ? 4 : 0;
        int availableWidth = Math.max(0, textureWidth - leftPadding * 2);
        this.textX = switch (this.align) {
            case MIDDLE -> leftPadding + (availableWidth - textDrawer.getWidth(visibleText)) / 2;
            case RIGHT -> textureWidth - leftPadding - textDrawer.getWidth(visibleText);
            case LEFT -> leftPadding;};
        this.textY = this.drawsBackground ? (textureHeight - 8) / 2 : 0;
    }

    protected void updateFirstCharacterIndex(int cursor) {
        String currentText = this.getText();
        this.firstCharacterIndex = MathHelper.clamp(cursor, 0, currentText.length());
    }

    protected void updateFirstCharacterIndex(int cursor, int textureWidth) {
        updateFirstCharacterIndex(cursor, textureWidth, TextRenderers.get());
    }

    private void updateFirstCharacterIndex(int cursor, int textureWidth, TextDrawer textDrawer) {
        String currentText = this.getText();
        this.firstCharacterIndex = Math.min(this.firstCharacterIndex, currentText.length());
        int innerWidth = this.getInnerWidth(textureWidth);
        String visibleText = textDrawer.trimToWidth(currentText.substring(this.firstCharacterIndex), innerWidth);
        int visibleEnd = visibleText.length() + this.firstCharacterIndex;
        if (cursor == this.firstCharacterIndex) {
            this.firstCharacterIndex -=
                    textDrawer.trimToWidth(currentText, innerWidth, true).length();
        }
        if (cursor > visibleEnd) {
            this.firstCharacterIndex += cursor - visibleEnd;
        } else if (cursor <= this.firstCharacterIndex) {
            this.firstCharacterIndex -= this.firstCharacterIndex - cursor;
        }
        this.firstCharacterIndex = MathHelper.clamp(this.firstCharacterIndex, 0, currentText.length());
    }

    protected boolean hasCtrlOrCmd(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }

    protected boolean hasShift(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    @FunctionalInterface
    public interface Formatter {
        OrderedText format(String string, int firstCharacterIndex);
    }
}
