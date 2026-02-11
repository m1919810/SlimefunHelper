package me.matl114.mixins.render;

import me.matl114.gui.basic.Draggable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Environment(EnvType.CLIENT)
@Mixin(ScrollableWidget.class)
public abstract class ScrollableWidgetMixin extends ClickableWidget implements Draggable {
    public ScrollableWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Shadow
    private boolean scrollbarDragged;

    @Shadow
    protected abstract boolean overflows();

    public boolean isDragging() {
        return scrollbarDragged;
    }

    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        this.scrollbarDragged = false;
    }

    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (this.overflows()
                && mouseX >= (double) (this.getX() + this.width)
                && mouseX <= (double) (this.getX() + this.width + 8)
                && mouseY >= (double) this.getY()
                && mouseY < (double) (this.getY() + this.height)) {
            this.scrollbarDragged = true;
            return true;
        }
        return false;
    }
}
