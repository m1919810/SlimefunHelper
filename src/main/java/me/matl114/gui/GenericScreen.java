package me.matl114.gui;

import java.util.Iterator;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.basic.Draggable;
import me.matl114.gui.basic.DrawableWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public class GenericScreen extends Screen implements Selectable, Draggable {
    protected int backgroundWidth;
    protected int backgroundDefaultHeight;
    protected int backgroundHeight;
    protected int x;
    protected int y;
    protected Text titleLabel;
    protected float currentShrink = 1.0F;

    public GenericScreen setTitleLabel(Text text) {
        this.titleLabel = text;
        return this;
    }

    public Text getTitleLabel(DrawableWidget widget) {
        return titleLabel;
    }

    protected GenericScreen(Text title, int backgroundWidth, int backgroundDefaultHeight) {
        super(title);
        setTitleLabel(title);
        this.backgroundDefaultHeight = backgroundDefaultHeight;
        this.backgroundWidth = backgroundWidth;
        this.backgroundHeight = backgroundDefaultHeight;
    }

    protected void init0() {
        this.x = (this.width - this.backgroundWidth) / 2;
        if (this.height > this.backgroundDefaultHeight + 24) {
            this.backgroundHeight = this.backgroundDefaultHeight;
            this.y = (this.height - this.backgroundHeight) / 2;
        } else {
            this.y = 12;
            this.backgroundHeight = this.height - 24;
        }
    }

    @Override
    protected void init() {
        super.init();
        init0();
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // call for all children
        Iterator var5 = this.children().iterator();
        Element element;
        do {
            if (!var5.hasNext()) {
                return false;
            }

            element = (Element) var5.next();
            if (element.isMouseOver(mouseX, mouseY)
                    && element.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        } while (true);
    }

    protected Draggable draggingElement = null;

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingElement != null) {
            // stop dragging here
            releaseDrag(this, mouseX, mouseY);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean val = false;
        for (Element element : this.children()) {
            if (element.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(element);
                if (button == 0) {
                    this.setDragging(true);
                }

                val = true;
                break;
            }
        }
        if (button == 0) {
            startDrag(this, mouseX, mouseY);
        }
        return val;
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.draggingElement != null
                && button == 0
                && this.draggingElement.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public final boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
            // we mixin the input field of these
            // it will return tru at keyPressed
        } else if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return true;
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (client.world == null) {
            super.renderBackground(context, mouseX, mouseY, deltaTicks);
        }
    }

    public void resetScreen() {
        // schedule refresh
        this.clearAndInit();
        // mc.executeSync(()->this.init(mc,mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()));
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {}

    @Override
    public SelectionType getType() {
        return this.isFocused() ? Selectable.SelectionType.FOCUSED : Selectable.SelectionType.NONE;
    }
    // implement our shit interface for dragging
    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (draggingElement != null) {
            // stop dragging here
            draggingElement.releaseDrag(this, mouseX, mouseY);
            draggingElement = null;
        }
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        for (var iter : this.children()) {
            if (iter instanceof Draggable drag && drag.startDrag(this, mouseX, mouseY)) {
                // start drag this element
                draggingElement = drag;
                return true;
            }
        }
        return false;
    }

    public ScreenAccess access() {
        return ScreenAccess.of(this);
    }
}
