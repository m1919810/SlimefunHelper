package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.single.WidgetPosSelectScreen;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.collections.FPoint;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class WidgetPos implements NBTParsable<WidgetPos> {
    // absolute, relative 两种
    @With
    public final int type;

    public final double percentageX;
    public final double percentageY;

    public final int lengthX;
    public final int lengthY;
    public static final WidgetPos EMPTY = new WidgetPos(0, 0, 0, 0, 0);
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static NBTType<WidgetPos> TYPE = new NBTType<>(
            "widgetpos",
            RecordCodecBuilder.<WidgetPos>create(oInstance -> oInstance
                    .group(
                            Codec.INT.fieldOf("type").forGetter(WidgetPos::getType),
                            Codec.DOUBLE.fieldOf("percentageX").forGetter(WidgetPos::getPercentageX),
                            Codec.DOUBLE.fieldOf("percentageY").forGetter(WidgetPos::getPercentageY),
                            Codec.INT.fieldOf("lengthX").forGetter(WidgetPos::getLengthX),
                            Codec.INT.fieldOf("lengthY").forGetter(WidgetPos::getLengthY))
                    .apply(oInstance, WidgetPos::new)),
            (w, x, y, dx, dy) -> {
                SubScreenWidget widget = new SubScreenWidget(x, y, dx, dy);
                widget.addDrawableChild(ExecutableWidget.instance(0, 0, 2 * dy, dy)
                        .setElementHandler(new ButtonElement(
                                (el) -> {
                                    return switch (w.getOriginValue().getType()) {
                                        case 0 -> Text.translatableWithFallback(
                                                "widget.nbt-parsable.widget-pos.percentage", "Per");
                                        case 1 -> Text.translatableWithFallback(
                                                "widget.nbt-parsable.widget-pos.absolute-length", "Abs");
                                        default -> Text.empty();
                                    };
                                },
                                ButtonAction.run(() -> {
                                    int total = 2;
                                    int type = w.getOriginValue().getType();
                                    w.valueChangeInternal(
                                            null, w.getOriginValue().withType((type + 1) % total));
                                }))));

                TypeConvertAttrKeyValue<WidgetPos, Vec2> percentageSel = new TypeConvertAttrKeyValue<>(
                        w,
                        WrapperFactory.of(s -> w.getOriginValue().withPercentage(s), WidgetPos::toPercentage),
                        NBTTypes.VEC2_TYPE);

                SubScreenWidget percentage1 = new SubScreenWidget(0, 0, dx - 2 * dy, dy);
                percentage1.addDrawableChild(percentageSel.generateValueWidget(0, 0, dx - 3 * dy, dy));
                percentage1.addDrawableChild(generateWidgetPosSelectScreenButton(
                        dx - 3 * dy, 0, dy, dy, () -> w.getOriginValue().getFPoint(), (el) -> {
                            int width = mc.getWindow().getScaledWidth();
                            int height = mc.getWindow().getScaledHeight();
                            double mulWidth = (el.x * 100) / width;
                            double mulHeight = (el.y * 100) / height;
                            Vec2 percentage2 = new Vec2(
                                    MathHelper.clamp(Math.round(mulWidth) / 100.0D, 0, 1),
                                    MathHelper.clamp(Math.round(mulHeight) / 100.0D, 0, 1));
                            percentageSel.valueChangeInternal(null, percentage2);
                        }));
                TypeConvertAttrKeyValue<WidgetPos, Vec2> absoluteSel = new TypeConvertAttrKeyValue<>(
                        w,
                        WrapperFactory.of(s -> w.getOriginValue().withLength(s), WidgetPos::toLength),
                        NBTTypes.VEC2_TYPE);
                SubScreenWidget percentage2 = new SubScreenWidget(0, 0, dx - 2 * dy, dy);
                percentage2.addDrawableChild(absoluteSel.generateValueWidget(0, 0, dx - 3 * dy, dy));
                percentage2.addDrawableChild(generateWidgetPosSelectScreenButton(
                        dx - 3 * dy, 0, dy, dy, () -> w.getOriginValue().getFPoint(), (el) -> {
                            absoluteSel.valueChangeInternal(null, new Vec2(el.x, el.y));
                        }));

                DynamicContentWidget<DrawableWidget> showWidget = new DynamicContentWidget<>(
                        () -> {
                            return switch (w.getOriginValue().getType()) {
                                case 0 -> percentage1;
                                case 1 -> percentage2;
                                default -> null;
                            };
                        },
                        2 * dy,
                        0);
                widget.addDrawableChild(showWidget);
                return widget;
            },
            EMPTY);

    public static DrawableWidget generateWidgetPosSelectScreenButton(
            int x, int y, int dx, int dy, Supplier<FPoint> current, Consumer<FPoint> consumer) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(IconElement.fixedGui(Constants.EDITOR_SPRITE, ButtonAction.run(() -> {
                            // open gui
                            ScreenAccess.of(new WidgetPosSelectScreen(320, current.get(), consumer))
                                    .openFromCurrent();
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.nbt-parsable.widget-pos.open-select-screen.tooltips", ""))));
    }

    public WidgetPos withPercentage(Vec2 vec2) {
        return new WidgetPos(type, vec2.x(), vec2.y(), lengthX, lengthY);
    }

    public WidgetPos withLength(Vec2 vec2) {
        return new WidgetPos(type, percentageX, percentageY, (int) vec2.x(), (int) vec2.y());
    }

    public Vec2 toPercentage() {
        return new Vec2(percentageX, percentageY);
    }

    public Vec2 toLength() {
        return new Vec2(lengthX, lengthY);
    }

    public int getWindowX(Window window) {
        if (type == 0) {
            return (int) (window.getScaledWidth() * percentageX);
        } else if (type == 1) {
            return lengthX;
        } else {
            return 0;
        }
    }

    public double getWindowXFloat(Window window) {
        if (type == 0) {
            return (window.getScaledWidth() * percentageX);
        } else if (type == 1) {
            return lengthX;
        } else {
            return 0;
        }
    }

    public FPoint getFPoint() {
        return new FPoint(getWindowXFloat(mc.getWindow()), getWindowYFloat(mc.getWindow()));
    }

    public int getWindowY(Window window) {
        if (type == 0) {
            return (int) (window.getScaledHeight() * percentageY);
        } else if (type == 1) {
            return lengthY;
        } else {
            return 0;
        }
    }

    public double getWindowYFloat(Window window) {
        if (type == 0) {
            return (window.getScaledHeight() * percentageY);
        } else if (type == 1) {
            return lengthY;
        } else {
            return 0;
        }
    }

    @Override
    public <W> Optional<WidgetPos> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef<?> ref2 && ref2.get() instanceof Vec2 legacy) {
            if (type == 0) {
                return Optional.of(this.withPercentage(legacy));
            } else {
                return Optional.of(this.withLength(legacy));
            }
        }
        return Optional.empty();
    }

    @Override
    public NBTType<WidgetPos> type() {
        return TYPE.cast();
    }
}
