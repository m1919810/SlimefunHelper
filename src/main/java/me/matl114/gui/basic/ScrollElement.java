package me.matl114.gui.basic;

import java.util.function.BooleanSupplier;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.config.PropertyTracker;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

@Accessors(chain = true)
public class ScrollElement extends AbstractElement {
    PropertyTracker<ScrollElement, Double> valueTracker;
    BooleanSupplier activate;
    private double percentage0 = 0.0d;

    public double getPercentage() {
        return percentage0;
    }

    @Setter
    private boolean draggingY = true;

    public ScrollElement setPercentage(double per) {
        double oldValue = this.percentage0;
        this.percentage0 = MathHelper.clamp(per, 0.0d, 1.0d);
        if (this.percentage0 != oldValue && this.valueTracker != null) {
            this.valueTracker.valueChange(this, this.percentage0);
        }
        return this;
    }

    public static ScrollElement instance(PropertyTracker<ScrollElement, Double> valueTracker) {
        return new ScrollElement(valueTracker, () -> true);
    }

    public static ScrollElement instance(
            PropertyTracker<ScrollElement, Double> valueTracker, BooleanSupplier activate) {
        return new ScrollElement(valueTracker, activate);
    }

    public ScrollElement(PropertyTracker<ScrollElement, Double> valueTracker, BooleanSupplier activate) {
        this.valueTracker = valueTracker;
        this.activate = activate;
    }

    private static final Identifier SCROLLER_BACKGROUND =
            new Identifier("minecraft", "textures/gui/container/creative_inventory/tab_items.png");
    private static final Identifier SCROLLER_TEXTURE =
            new Identifier("minecraft", "container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED_TEXTURE =
            new Identifier("minecraft", "container/creative_inventory/scroller_disabled");
    private static final Quaternionf ROTATE_X = RotationAxis.NEGATIVE_Z.rotationDegrees(90);

    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        // 绘制滑动条
        // 不支持element进行缩放,谁会对滑动条缩放啊,直接指定大小就行
        if (draggingY) {
            context.drawTexturedQuad(
                    SCROLLER_BACKGROUND,
                    0,
                    element.getWidth(),
                    0,
                    element.getHeight(),
                    100,
                    174 / 256f,
                    (188) / 256f,
                    17 / 256f,
                    (129) / 256f);
        } else {
            // 更符合视觉的方式
            context.getMatrices().pushMatrix();
            context.getMatrices().multiply3D(new Quaternionf(ROTATE_X.x, ROTATE_X.y, ROTATE_X.z, ROTATE_X.w));
            context.drawTexturedQuad(
                    SCROLLER_BACKGROUND,
                    -element.getHeight(),
                    0,
                    0,
                    element.getWidth(),
                    100,
                    (188) / 256f,
                    174 / 256f,
                    17 / 256f,
                    (129) / 256f);
            context.getMatrices().popMatrix();
        }

        boolean activate = this.activate.getAsBoolean();
        double per = activate ? this.getPercentage() : 0.0d;
        Identifier texture = activate ? SCROLLER_TEXTURE : SCROLLER_DISABLED_TEXTURE;
        double rendX;
        double rendY;
        double rendSize;
        if (draggingY) {
            rendX = 0;
            rendSize = element.getWidth() / BUTTON_WIDTH;

            rendY = (element.getHeight() - rendSize * BUTTON_HEIGHT) * per;

        } else {
            rendY = 0;
            rendSize = element.getHeight() / BUTTON_HEIGHT;
            rendX = (element.getWidth() - rendSize * BUTTON_WIDTH) * per;
        }
        context.drawGuiTexture(texture, (int) rendX, (int) rendY, 110, (int) (BUTTON_WIDTH * rendSize), (int)
                (BUTTON_HEIGHT * rendSize));
    }

    public static final float BUTTON_WIDTH = 12f;
    public static final float BUTTON_HEIGHT = 15f;

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
        if (activate.getAsBoolean() && (type == Type.MOUSE_CLICK || type == Type.MOUSE_DRAG)) {
            if (draggingY) {
                double scaleButton = element.getWidth() / BUTTON_WIDTH;
                double halfHeight = BUTTON_HEIGHT * scaleButton / 2.0d;
                double atY = mouseY - halfHeight - element.getY();
                double fullY = element.getHeight() - 2 * halfHeight;
                double percentage = atY / fullY;
                setPercentage(percentage);
            } else {
                double scaleButton = element.getHeight() / BUTTON_HEIGHT;
                double halfHeight = BUTTON_WIDTH * scaleButton / 2.0d;
                double atY = mouseX - halfHeight - element.getX();
                double fullY = element.getWidth() - 2 * halfHeight;
                double percentage = atY / fullY;
                setPercentage(percentage);
            }
            return true;
        }
        return false;
    }
}
