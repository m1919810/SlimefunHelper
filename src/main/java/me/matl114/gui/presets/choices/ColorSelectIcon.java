package me.matl114.gui.presets.choices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.BoxElement;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

public class ColorSelectIcon extends BoxElement {
    public static final TextColor[] FORMAT_COLORS;

    static {
        List<TextColor> colors = new ArrayList<TextColor>();
        for (var formatColor : Formatting.values()) {
            if (formatColor.isColor()) {
                colors.add(TextColor.fromFormatting(formatColor));
            }
        }
        colors.sort(Comparator.comparingInt(TextColor::getRgb));
        FORMAT_COLORS = colors.toArray(TextColor[]::new);
    }

    final ValueAccessor<TextColor> color;

    public ColorSelectIcon(ValueAccessor<TextColor> color) {
        super(ButtonAction.isLeft(s -> {
            if (ScreenUtils.hasShiftDown()) {
                openColorSelectScreen(color);
            } else {
                swapToNearestColorSwitch(color, s);
            }
        }));
        this.color = color;
        combineAbsoluteRender(TooltipHandler.of(() -> {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(Text.literal("当前颜色为: " + color.getValue().getName()));
            tooltip.addAll(TOOLTIPS_SWAP_COLOR);
            return tooltip;
        }));
    }

    public ColorSelectIcon(ValueAccessor<TextColor> color, ButtonAction buttonAction) {
        super(buttonAction);
        this.color = color;
        combineAbsoluteRender(TooltipHandler.of(() -> {
            return List.of(Text.literal("当前颜色为: " + color.getValue().getName()));
        }));
    }

    public static final List<Text> TOOLTIPS_SWAP_COLOR =
            List.of(Text.literal("左右键点击切换颜色"), Text.literal("shift点击打开颜色选取表"));

    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        // float[] shaders = RenderSystem.getShaderColor()
        RenderHandler.drawHighlightFrame(
                context, 0, 0, element.getTextureWidth(), element.getTextureHeight(), Colors.WHITE);
        TextColor color = this.color.getValue();
        context.fill(
                1,
                1,
                element.getTextureWidth() - 1,
                element.getTextureHeight() - 1,
                ColorUtils.withAlphaInt(color.getRgb(), 255));
    }

    public static void swapToNearestColorSwitch(ValueAccessor<TextColor> colorAcc, boolean forward) {
        TextColor color = colorAcc.getValue();
        int idx = Arrays.binarySearch(FORMAT_COLORS, color, Comparator.comparingInt(TextColor::getRgb));
        int targetIdx;
        if (forward) {
            // 找大于 color 的最小颜色
            if (idx >= 0) {
                // 精确匹配，取下一个（大于）
                targetIdx = idx + 1;
            } else {
                // 插入点 = -idx - 1，即第一个大于 color 的索引
                targetIdx = -idx - 1;
            }
            if (targetIdx >= FORMAT_COLORS.length) {
                targetIdx = 0; // 循环到开头
            }
        } else {
            // 找小于 color 的最大颜色
            if (idx >= 0) {
                // 精确匹配，取上一个（小于）
                targetIdx = idx - 1;
            } else {
                // 插入点 = -idx - 1，即第一个大于 color 的索引
                int insertionPoint = -idx - 1;
                // 小于 color 的最大索引为 insertionPoint - 1
                targetIdx = insertionPoint - 1;
            }
            if (targetIdx < 0) {
                targetIdx = FORMAT_COLORS.length - 1; // 循环到末尾
            }
        }
        TextColor newColor = FORMAT_COLORS[targetIdx];
        colorAcc.setValue(newColor);
    }

    public static void openColorSelectScreen(ValueAccessor<TextColor> colorAcc) {
        ScreenAccess.of(new ColorSelectScreen(colorAcc)).openFromCurrent();
    }
}
