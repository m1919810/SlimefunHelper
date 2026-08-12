package me.matl114.gui.basic;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.versioned.api.VDrawContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DrawableWidget
        implements Element, Drawable, net.minecraft.client.gui.widget.Widget, Selectable, Draggable {
    public DrawableWidget(int x, int y, int dx, int dy) {
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        updateScale();
    }

    // do not read other's value, because of delegate
    private int x;
    private int y;

    protected int dx;
    protected int dy;

    private int textureWidth;
    private int textureHeight;
    // higher priority means more likely to be selected
    protected int priority;

    private final void updateScale() {
        this.textureWidth = (int) (dx / this.textureScale);
        this.textureHeight = (int) (dy / this.textureScale);
    }

    private float textureScale = 1.0f;
    private float alpha = 1.0f;
    private boolean subWidget = false;
    private RenderHandler renderHandler;
    protected boolean selected;
    protected boolean focused;

    public RenderHandler getRenderHandler() {
        return this.renderHandler;
    }

    public float getTextureScale() {
        return textureScale;
    }

    public int getTextureWidth() {
        return this.textureWidth;
    }

    public int getTextureHeight() {
        return this.textureHeight;
    }

    public int getExtraDepth() {
        return this.priority;
    }

    public float getAlpha() {
        return this.alpha;
    }

    public boolean isSubWidget() {
        return subWidget;
    }

    public boolean isSelected() {
        return selected;
    }
    // this should be set before they join any delegates or something, as they often causes problem
    public final <T extends DrawableWidget> T setPriority(int depth) {
        this.priority = depth;
        return (T) this;
    }

    public <T extends DrawableWidget> T setTextureScale(float scale) {
        this.textureScale = scale;
        updateScale();
        return (T) this;
    }

    public <T extends DrawableWidget> T setAlpha(float scale) {
        this.alpha = scale;
        return (T) this;
    }

    public void setSubWidget(boolean s) {
        this.subWidget = s;
    }

    public void setSelected(boolean s) {
        this.selected = s;
    }

    public <T extends DrawableWidget> T setRenderHandler(RenderHandler renderHandler) {
        this.renderHandler = renderHandler;
        return (T) this;
    }

    public <T extends DrawableWidget> T updateRenderHandler(UnaryOperator<RenderHandler> updater) {
        this.renderHandler = updater.apply(this.renderHandler == null ? new AbstractElement() : this.renderHandler);
        return (T) this;
    }

    //    public boolean onElement(int mouseX, int mouseY){
    //
    //    }

    /**
     * override only for delegate!
     * @param context
     * @param mouseX
     * @param mouseY
     * @param delta
     */
    @Override
    public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
        VDrawContext vd = VDrawContext.of(context);
        render0(vd, mouseX, mouseY, delta, false);
        vd.tryDraw();
    }

    protected void checkSelect(boolean disableSelect, int mouseX, int mouseY) {
        this.selected = !disableSelect && isMouseOver(mouseX, mouseY);
    }

    public boolean canSelect() {
        return renderHandler != null && renderHandler.canBeSelected(this);
    }

    public void render0(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        this.selected = !disableSelect && isMouseOver(mouseX, mouseY);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(getX(), getY());
        // compat low version
        if (priority != 0) {
            context.pushLayer(priority);
        }
        float textureScale = getTextureScale();
        if (textureScale != 1.0f) {
            context.getMatrices().scale(textureScale, textureScale);
        }
        renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        if (priority != 0) {
            context.popLayer();
        }
        context.getMatrices().popMatrix();
        renderAbsolute(context, mouseX, mouseY, delta, disableSelect);
    }

    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        if (this.renderHandler != null) {
            this.renderHandler.renderAtCentered(this, context, mouseX, mouseY, delta, this.alpha, this.selected);
        }
    }

    public void renderAbsolute(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        if (this.renderHandler != null) {
            this.renderHandler.renderExtraAbsoluteCoord(
                    this, context, mouseX, mouseY, delta, this.alpha, this.selected);
        }
    }

    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    public abstract boolean mouseReleased(double mouseX, double mouseY, int button);

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getX()
                && mouseY >= this.getY()
                && mouseX < this.getX() + this.textureWidth
                && mouseY < this.getY() + this.textureHeight;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        // Debug.info("This method should not be called!");
    }

    public boolean isFocused() {
        return focused;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return dx;
    }

    public void setWidth(int width) {
        this.dx = width;
    }

    public void setHeight(int height) {
        this.dy = height;
    }

    public int getHeight() {
        return dy;
    }

    public <T extends DrawableWidget> T cast() {
        return (T) this;
    }

    public <T extends DrawableWidget> T addTo(Screen screen) {
        ScreenAccess.of(screen).addDrawableChildTo(this);
        return (T) this;
    }

    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen) {
        addInternal(screen);
        return (T) this;
    }

    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen, int priority) {
        setPriority(priority);
        addInternal(screen);
        return (T) this;
    }

    private void addInternal(SubScreenWidget screen) {
        this.subWidget = true;
        screen.addDrawableChild(this);
    }

    public void mouseMoved(double mouseX, double mouseY) {
        // should not move
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // should not drag
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    // ------------------------------------- public functions left for -------------------------------------

    public final void forEachChild(Consumer<ClickableWidget> consumer) {}

    public Selectable.SelectionType getType() {
        return this.selected ? Selectable.SelectionType.HOVERED : Selectable.SelectionType.NONE;
    }

    public final void appendNarrations(NarrationMessageBuilder builder) {}

    @Nullable
    public final GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }

    @Nullable
    public final GuiNavigationPath getFocusedPath() {
        return null;
    }

    public final ScreenRect getNavigationFocus() {
        return ScreenRect.empty();
    }

    public boolean isDragging() {
        return false;
    }

    public void releaseDrag(Screen screen, double mouseX, double mouseY) {}

    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        return false;
    }
    // -------------------------------- API compat for higher version
    // will only be modified on main thread
    public static int THREAD_SAFE_MODIFIER_CACHE = 0;
    public static boolean THREAD_SAFE_DOUBLE_CLICK = false;

    public final boolean mouseClicked(Click click, boolean doubled) {
        THREAD_SAFE_MODIFIER_CACHE = click.modifiers();
        THREAD_SAFE_DOUBLE_CLICK = doubled;
        return this.mouseClicked(click.x(), click.y(), click.button());
    }

    public final boolean mouseReleased(Click click) {
        THREAD_SAFE_MODIFIER_CACHE = click.modifiers();
        return this.mouseReleased(click.x(), click.y(), click.button());
    }

    public final boolean mouseDragged(Click click, double offsetX, double offsetY) {
        THREAD_SAFE_MODIFIER_CACHE = click.modifiers();
        return this.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY);
    }

    public final boolean keyPressed(KeyInput input) {
        THREAD_SAFE_MODIFIER_CACHE = input.modifiers();
        return this.keyPressed(input.key(), input.scancode(), input.modifiers());
    }

    public final boolean keyReleased(KeyInput input) {
        THREAD_SAFE_MODIFIER_CACHE = input.modifiers();
        return this.keyReleased(input.key(), input.scancode(), input.modifiers());
    }

    public final boolean charTyped(CharInput input) {
        THREAD_SAFE_MODIFIER_CACHE = input.modifiers();
        return this.charTyped((char) input.codepoint(), input.modifiers());
    }
}
