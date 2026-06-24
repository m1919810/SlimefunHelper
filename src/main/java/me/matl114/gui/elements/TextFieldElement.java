package me.matl114.gui.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.experimental.Accessors;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.InputHandler;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.PropertyTracker;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@Accessors(chain = true)
public class TextFieldElement extends AbstractElement {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Identifier TEXT_FIELD_TEXTURE = Identifier.ofVanilla("widget/text_field");
    private static final Identifier TEXT_FIELD_HIGHLIGHTED_TEXTURE =
            Identifier.ofVanilla("widget/text_field_highlighted");
    private static final long DOUBLE_CLICK_INTERVAL = 250L;
    private static final int DEFAULT_EDITABLE_COLOR = -2039584;
    private static final int DEFAULT_UNEDITABLE_COLOR = -9408400;
    private static final String HORIZONTAL_CURSOR = "_";
    public static final Style PLACEHOLDER_STYLE = Style.EMPTY.withColor(Formatting.DARK_GRAY);
    public static final Style SEARCH_STYLE = Style.EMPTY.withFormatting(Formatting.GRAY, Formatting.ITALIC);

    private final TextRenderer textRenderer;
    private final TextFieldAccess accessBridge = new TextFieldAccess() {
        @Override
        public void setListener(PropertyTracker<TextFieldAccess, String> tracker) {
            TextFieldElement.this.setListener(tracker);
        }

        @Override
        public void setBorderColorProvider(ColorProvider provider) {
            TextFieldElement.this.setBorderColorProvider(provider);
        }

        @Override
        public boolean canStartDrag(double mouseX, double mouseY) {
            return TextFieldElement.this.canStartDrag(mouseX, mouseY);
        }

        @Override
        public void dragSelect(int deltaX, int deltaY, boolean shiftDownAction) {
            TextFieldElement.this.dragSelect(deltaX, deltaY, shiftDownAction);
        }

        @Override
        public void resetSelect() {
            TextFieldElement.this.resetSelection();
        }
    };

    private Text message;
    private String text = "";
    private int maxLength = 32;
    private boolean drawsBackground = true;
    private boolean focusUnlocked = true;
    private boolean editable = true;
    private boolean centered = false;
    private boolean textShadow = true;
    private boolean invertSelectionBackground = true;
    private boolean visible = true;
    private int firstCharacterIndex;
    private int selectionStart;
    private int selectionEnd;
    private int editableColor = DEFAULT_EDITABLE_COLOR;
    private int uneditableColor = DEFAULT_UNEDITABLE_COLOR;
    private @Nullable String suggestion;
    private @Nullable Consumer<String> changedListener;
    private @Nullable PropertyTracker<TextFieldAccess, String> tracker;
    private Predicate<String> textPredicate = Objects::nonNull;
    private final List<Formatter> formatters = new ArrayList<>();
    private @Nullable Text placeholder;
    private long lastSwitchFocusTime = Util.getMeasuringTimeMs();
    private int textX;
    private int textY;
    private int width;
    private int height;
    private boolean draggingSelection;
    private boolean focused;
    private @Nullable ColorProvider borderColorProvider;
    private long lastClickTime;
    private int lastClickCursor = -1;
    private int lastClickButton = -1;

    public static TextFieldElement instance() {
        return new TextFieldElement();
    }

    public static TextFieldElement instance(Text message) {
        return new TextFieldElement(message);
    }

    public TextFieldElement() {
        this(Text.empty());
    }

    public TextFieldElement(Text message) {
        this.textRenderer = mc.textRenderer;
        this.message = message == null ? Text.empty() : message;
        this.updateTextPosition();
    }

    public TextFieldElement(TextFieldWidget textFieldWidget) {
        this(textFieldWidget == null ? Text.empty() : textFieldWidget.getMessage());
        if (textFieldWidget != null) {
            this.text = textFieldWidget.getText();
            this.drawsBackground = textFieldWidget.drawsBackground();
            this.selectionStart = MathHelper.clamp(textFieldWidget.getCursor(), 0, this.text.length());
            this.selectionEnd = this.selectionStart;
            this.updateFirstCharacterIndex(this.selectionStart);
            this.updateTextPosition();
        }
    }

    public Text getMessage() {
        return this.message;
    }

    public TextFieldElement setMessage(Text message) {
        this.message = message == null ? Text.empty() : message;
        return this;
    }

    public TextFieldElement setText(String text) {
        String next = text == null ? "" : text;
        if (this.textPredicate.test(next)) {
            this.text = next.length() > this.maxLength ? next.substring(0, this.maxLength) : next;
            this.setCursorToEnd(false);
            this.setSelectionEnd(this.selectionStart);
            this.onChanged(this.text);
        }
        return this;
    }

    public String getText() {
        return this.text;
    }

    public String getSelectedText() {
        int i = Math.min(this.selectionStart, this.selectionEnd);
        int j = Math.max(this.selectionStart, this.selectionEnd);
        return this.text.substring(i, j);
    }

    public TextFieldElement setTextPredicate(Predicate<String> textPredicate) {
        this.textPredicate = textPredicate == null ? Objects::nonNull : textPredicate;
        return this;
    }

    public TextFieldElement setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        if (this.text.length() > this.maxLength) {
            this.text = this.text.substring(0, this.maxLength);
            this.selectionStart = Math.min(this.selectionStart, this.text.length());
            this.selectionEnd = Math.min(this.selectionEnd, this.text.length());
            this.updateFirstCharacterIndex(this.selectionStart);
            this.onChanged(this.text);
        }
        return this;
    }

    public TextFieldElement setChangedListener(Consumer<String> listener) {
        this.changedListener = listener;
        return this;
    }

    public TextFieldElement setListener(PropertyTracker<TextFieldAccess, String> tracker) {
        this.tracker = tracker;
        return this;
    }

    public TextFieldElement setBorderColorProvider(ColorProvider provider) {
        this.borderColorProvider = provider;
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

    public TextFieldElement setEditable(boolean editable) {
        this.editable = editable;
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

    public TextFieldElement setSuggestion(@Nullable String suggestion) {
        this.suggestion = suggestion;
        return this;
    }

    public TextFieldElement setEditableColor(int editableColor) {
        this.editableColor = editableColor;
        return this;
    }

    public TextFieldElement setUneditableColor(int uneditableColor) {
        this.uneditableColor = uneditableColor;
        return this;
    }

    public TextFieldElement setCursorToEnd(boolean shiftKeyPressed) {
        this.setCursor(this.text.length(), shiftKeyPressed);
        return this;
    }

    public TextFieldElement setCursorToStart(boolean shiftKeyPressed) {
        this.setCursor(0, shiftKeyPressed);
        return this;
    }

    public TextFieldElement setCentered(boolean centered) {
        this.centered = centered;
        this.updateTextPosition();
        return this;
    }

    public TextFieldElement setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
        return this;
    }

    public TextFieldElement setInvertSelectionBackground(boolean invertSelectionBackground) {
        this.invertSelectionBackground = invertSelectionBackground;
        return this;
    }

    public TextFieldElement setVisible(boolean visible) {
        this.visible = visible;
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
        return this.selectionStart;
    }

    protected void syncWidgetState(DrawableWidget element) {
        this.width = Math.max(0, element.getTextureWidth());
        this.height = Math.max(0, element.getTextureHeight());
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

    protected boolean isEditable() {
        return this.editable;
    }

    protected boolean isActive() {
        return this.focused && this.editable;
    }

    protected boolean drawsBackground() {
        return this.drawsBackground;
    }

    protected int getInnerWidth() {
        return this.drawsBackground ? Math.max(0, this.width - 8) : this.width;
    }

    protected void write(String value) {
        int i = Math.min(this.selectionStart, this.selectionEnd);
        int j = Math.max(this.selectionStart, this.selectionEnd);
        int k = this.maxLength - this.text.length() - (i - j);
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
        String string2 = new StringBuilder(this.text).replace(i, j, string).toString();
        if (this.textPredicate.test(string2)) {
            this.text = string2;
            this.setSelectionStart(i + l);
            this.setSelectionEnd(this.selectionStart);
            this.onChanged(this.text);
        }
    }

    protected void erase(int offset, boolean words) {
        if (words) {
            this.eraseWords(offset);
        } else {
            this.eraseCharacters(offset);
        }
    }

    public void eraseWords(int wordOffset) {
        if (this.text.isEmpty()) {
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
        if (this.text.isEmpty()) {
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
        String string = new StringBuilder(this.text).delete(i, j).toString();
        if (this.textPredicate.test(string)) {
            this.text = string;
            this.setCursor(i, false);
        }
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
                int length = this.text.length();
                index = this.text.indexOf(32, index);
                if (index == -1) {
                    index = length;
                } else {
                    while (skipOverSpaces && index < length && this.text.charAt(index) == ' ') {
                        ++index;
                    }
                }
            } else {
                while (skipOverSpaces && index > 0 && this.text.charAt(index - 1) == ' ') {
                    --index;
                }
                while (index > 0 && this.text.charAt(index - 1) != ' ') {
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
        return Util.moveCursor(this.text, this.selectionStart, offset);
    }

    protected void setCursor(int cursor, boolean select) {
        this.setSelectionStart(cursor);
        if (!select) {
            this.setSelectionEnd(this.selectionStart);
        }
        this.updateTextPosition();
    }

    protected void setSelectionStart(int cursor) {
        this.selectionStart = MathHelper.clamp(cursor, 0, this.text.length());
        this.updateFirstCharacterIndex(this.selectionStart);
    }

    protected void setSelectionEnd(int index) {
        this.selectionEnd = MathHelper.clamp(index, 0, this.text.length());
        this.updateFirstCharacterIndex(this.selectionEnd);
    }

    protected void onChanged(String newText) {
        if (this.changedListener != null) {
            this.changedListener.accept(newText);
        }
        if (this.tracker != null) {
            this.tracker.valueChange(this.accessBridge, newText);
        }
        this.updateTextPosition();
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
                if (this.editable) {
                    this.erase(-1, ctrlOrCmd);
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (this.editable) {
                    this.erase(1, ctrlOrCmd);
                }
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
                this.setCursor(this.text.length(), shift);
                return true;
            }
            default -> {
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_A) {
                    this.setCursor(this.text.length(), false);
                    this.setSelectionEnd(0);
                    return true;
                }
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_C) {
                    mc.keyboard.setClipboard(this.getSelectedText());
                    return true;
                }
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_V) {
                    if (this.editable) {
                        this.write(mc.keyboard.getClipboard());
                    }
                    return true;
                }
                if (ctrlOrCmd && keyCode == GLFW.GLFW_KEY_X) {
                    mc.keyboard.setClipboard(this.getSelectedText());
                    if (this.editable) {
                        this.write("");
                    }
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

    protected int calculateCursorPos(double mouseX) {
        int innerX = Math.min(MathHelper.floor(mouseX) - this.textX, this.getInnerWidth());
        String string = this.text.substring(this.firstCharacterIndex);
        return this.firstCharacterIndex + this.textRenderer.trimToWidth(string, innerX).length();
    }

    protected void selectWord(int cursor) {
        int start = this.getWordSkipPosition(-1, cursor);
        int end = this.getWordSkipPosition(1, cursor);
        this.setCursor(start, false);
        this.setCursor(end, true);
    }

    protected boolean canStartDrag(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseY >= 0 && mouseX < this.width && mouseY < this.height;
    }

    protected void dragSelect(int mouseX, int mouseY, boolean shiftDownAction) {
        int innerX = mouseX;
        if (this.drawsBackground()) {
            innerX -= 4;
        }
        String string = this.textRenderer.trimToWidth(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
        this.setCursor(this.textRenderer.trimToWidth(string, innerX).length() + this.firstCharacterIndex, shiftDownAction);
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
        if (!this.visible) {
            return;
        }
        int translatedMouseX = translateRenderMouseX(element, mouseX);
        int translatedMouseY = translateRenderMouseY(element, mouseY);
        DrawContext drawContext = context.pushMatrix();
        try {
            if (this.drawsBackground()) {
                if (this.borderColorProvider != null) {
                    McWidgetHelpers.drawTextWidgetBox(
                            element, drawContext, 0, 0, this.width, this.height, this.focused, this.borderColorProvider);
                } else {
                    context.drawGuiTexture(
                            this.focused ? TEXT_FIELD_HIGHLIGHTED_TEXTURE : TEXT_FIELD_TEXTURE,
                            0,
                            0,
                            this.width,
                            this.height);
                }
            }

            int color = this.editable ? this.editableColor : this.uneditableColor;
            int cursorOffset = this.selectionStart - this.firstCharacterIndex;
            String visibleText = this.textRenderer.trimToWidth(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
            boolean cursorInVisibleRange = cursorOffset >= 0 && cursorOffset <= visibleText.length();
            boolean showBlink = this.focused
                    && (Util.getMeasuringTimeMs() - this.lastSwitchFocusTime) / 300L % 2L == 0L
                    && cursorInVisibleRange;
            int drawX = this.textX;
            int selectionOffset = MathHelper.clamp(this.selectionEnd - this.firstCharacterIndex, 0, visibleText.length());
            if (!visibleText.isEmpty()) {
                String beforeCursor = cursorInVisibleRange ? visibleText.substring(0, cursorOffset) : visibleText;
                OrderedText orderedText = this.format(beforeCursor, this.firstCharacterIndex);
                drawContext.drawText(this.textRenderer, orderedText, drawX, this.textY, color, this.textShadow);
                drawX += this.textRenderer.getWidth(orderedText) + 1;
            }

            boolean hasMoreChars = this.selectionStart < this.text.length() || this.text.length() >= this.maxLength;
            int cursorX = drawX;
            if (!cursorInVisibleRange) {
                cursorX = cursorOffset > 0 ? this.textX + this.width : this.textX;
            } else if (hasMoreChars) {
                --cursorX;
                --drawX;
            }

            if (!visibleText.isEmpty() && cursorInVisibleRange && cursorOffset < visibleText.length()) {
                drawContext.drawText(
                        this.textRenderer,
                        this.format(visibleText.substring(cursorOffset), this.selectionStart),
                        drawX,
                        this.textY,
                        color,
                        this.textShadow);
            }

            if (this.placeholder != null && visibleText.isEmpty() && !this.focused) {
                drawContext.drawTextWithShadow(this.textRenderer, this.placeholder, drawX, this.textY, color);
            }

            if (!hasMoreChars && this.suggestion != null) {
                drawContext.drawText(this.textRenderer, this.suggestion, cursorX - 1, this.textY, -8355712, this.textShadow);
            }

            if (selectionOffset != cursorOffset) {
                int selectionX = this.textX + this.textRenderer.getWidth(visibleText.substring(0, selectionOffset));
                drawContext.drawSelection(
                        Math.min(cursorX, this.width),
                        this.textY - 1,
                        Math.min(selectionX - 1, this.width),
                        this.textY + 10,
                        this.invertSelectionBackground);
            }

            if (showBlink) {
                if (hasMoreChars) {
                    drawContext.fill(cursorX, this.textY - 1, cursorX + 1, this.textY + 10, color);
                } else {
                    drawContext.drawText(this.textRenderer, HORIZONTAL_CURSOR, cursorX, this.textY, color, this.textShadow);
                }
            }

            if (shouldHighlight) {
                drawContext.setCursor(this.isEditable() ? StandardCursors.IBEAM : StandardCursors.NOT_ALLOWED);
            }
        } finally {
            context.popMatrix();
        }
    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        syncWidgetState(element);
        double translatedMouseX = translateMouseX(element, mouseX);
        double translatedMouseY = translateMouseY(element, mouseY);
        if (!this.visible || button != 0 || !this.canStartDrag(translatedMouseX, translatedMouseY)) {
            return false;
        }
        int cursor = this.calculateCursorPos(translatedMouseX);
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

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, InputHandler.Type type) {
        if (type == InputHandler.Type.MOUSE_CLICK) {
            return super.onAction(element, mouseX, mouseY, button, type);
        }
        if (super.onAction(element, mouseX, mouseY, button, type)) {
            return true;
        }
        syncWidgetState(element);
        if (!this.visible) {
            this.draggingSelection = false;
            return false;
        }
        double translatedMouseX = translateMouseX(element, mouseX);
        double translatedMouseY = translateMouseY(element, mouseY);
        return switch (type) {
            case MOUSE_RELEASE -> {
                boolean wasDragging = this.draggingSelection;
                this.draggingSelection = false;
                yield wasDragging;
            }
            case MOUSE_START_DRAG -> {
                this.draggingSelection = button == 0 && this.canStartDrag(translatedMouseX, translatedMouseY);
                yield this.draggingSelection;
            }
            case MOUSE_DRAG -> {
                if (this.draggingSelection) {
                    this.dragSelect((int) translatedMouseX, (int) translatedMouseY, true);
                    yield true;
                }
                yield false;
            }
            case MOUSE_RELEASE_DRAG -> {
                boolean wasDragging = this.draggingSelection;
                this.draggingSelection = false;
                yield wasDragging;
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

    protected void updateTextPosition() {
        String visibleText = this.textRenderer.trimToWidth(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
        this.textX = this.centered
                ? (this.width - this.textRenderer.getWidth(visibleText)) / 2
                : (this.drawsBackground ? 4 : 0);
        this.textY = this.drawsBackground ? (this.height - 8) / 2 : 0;
    }

    protected void updateFirstCharacterIndex(int cursor) {
        this.firstCharacterIndex = Math.min(this.firstCharacterIndex, this.text.length());
        int innerWidth = this.getInnerWidth();
        String visibleText = this.textRenderer.trimToWidth(this.text.substring(this.firstCharacterIndex), innerWidth);
        int visibleEnd = visibleText.length() + this.firstCharacterIndex;
        if (cursor == this.firstCharacterIndex) {
            this.firstCharacterIndex -= this.textRenderer.trimToWidth(this.text, innerWidth, true).length();
        }
        if (cursor > visibleEnd) {
            this.firstCharacterIndex += cursor - visibleEnd;
        } else if (cursor <= this.firstCharacterIndex) {
            this.firstCharacterIndex -= this.firstCharacterIndex - cursor;
        }
        this.firstCharacterIndex = MathHelper.clamp(this.firstCharacterIndex, 0, this.text.length());
    }

    protected boolean hasCtrlOrCmd(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }

    protected boolean hasShift(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    @FunctionalInterface
    public interface Formatter {
        @Nullable OrderedText format(String string, int firstCharacterIndex);
    }
}