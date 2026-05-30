package me.matl114.utils.config.kv;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.experimental.Accessors;
import me.matl114.api.Displayable;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.ColorBoxElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.mutable.MutableBoolean;

@Accessors(chain = true)
public class EnumAttrKeyValue<T> extends BaseAttrKeyValue<T> {
    public static <T> WrapperFactory<String, T> createFiniteMapLookup(Map<String, T> map) {
        Map<T, String> inverseMap = new HashMap<>();
        for (var re : map.entrySet()) {
            inverseMap.put(re.getValue(), re.getKey());
        }
        return WrapperFactory.of(
                s -> {
                    var re = map.get(s);
                    if (re != null) {
                        return re;
                    } else throw WrapperFactory.PARSE_FAILURE;
                },
                inverseMap::get);
    }

    protected final Map<String, T> finiteValueMap;

    public Map<String, T> getValueMap() {
        return finiteValueMap;
    }

    Class<T> identifier;

    public EnumAttrKeyValue(String key, T value, Class<T> clazz, Map<String, T> finiteValueMap) {
        super(key, value, (CustomWidgetFactory<T>) ENUM_WIDGET_FACTORY, createFiniteMapLookup(finiteValueMap));
        this.finiteValueMap = finiteValueMap;
        this.identifier = clazz;
    }

    public static final CustomWidgetFactory<?> ENUM_WIDGET_FACTORY = (s, x, y, dx, dy) -> {
        if (s instanceof EnumAttrKeyValue attrKeyValue) {
            return attrKeyValue.generateSwitchingButton(x, y, dx, dy, Consumers.nop());
        } else {
            return BaseAttrKeyValue.generateTextInputValueWidget(s, x, y, dx, dy);
        }
    };

    public DrawableWidget generateSwitchingButton(
            int x, int y, int dx, int dy, Consumer<EnumAttrKeyValue<T>> changelistener) {
        List<Pair<String, Supplier<Text>>> flattenMap;
        if (Displayable.class.isAssignableFrom(identifier)) {
            Map<String, Displayable> valueMap = (Map<String, Displayable>) (this).getValueMap();
            flattenMap = valueMap.entrySet().stream()
                    .map((entry) -> new Pair<>(entry.getKey(), (Supplier<Text>) entry.getValue()::getDisplay))
                    .toList();
        } else {
            flattenMap = ((EnumAttrKeyValue<T>) this)
                    .getValueMap().keySet().stream()
                            .map(v -> new Pair<>(v, (Supplier<Text>) () -> Text.literal(v)))
                            .toList();
        }
        int choices = flattenMap.size();
        if (choices > 0) {
            AtomicInteger integer = new AtomicInteger();
            Runnable kvUpdater = () -> {
                String val = this.getValue();
                int index = -1;
                for (int i = 0; i < choices; ++i) {
                    if (Objects.equals(val, flattenMap.get(i).getFirst())) {
                        index = i;
                        break;
                    }
                }
                if (index == -1) {
                    this.valueChange(this, flattenMap.get(0).getFirst());
                    index = 0;
                } else {
                    this.valueChange(this, flattenMap.get(index).getFirst());
                }
                integer.set(index);
            };
            kvUpdater.run();
            SubScreenWidget subScreen = new SubScreenWidget(x, y, dx, dy);
            Runnable indexUpdater = () -> {
                this.valueChange(this, flattenMap.get(integer.get()).getFirst());
                changelistener.accept(this);
            };
            subScreen.addDrawableChild(ExecutableWidget.instance(1, 1, dx - dy - 2, dy - 2)
                    .setElementHandler(new ButtonElement(
                                    (ign) -> {
                                        kvUpdater.run();
                                        return flattenMap
                                                .get(integer.get())
                                                .getSecond()
                                                .get();
                                    },
                                    ButtonAction.run(() -> {
                                        kvUpdater.run();
                                        int index0 = integer.get();
                                        index0 = (index0 + 1) % choices;
                                        integer.set(index0);
                                        indexUpdater.run();
                                    }))
                            .withTooltips(TooltipHandler.of(List.of(Text.translatable(this.getKeyName()))))));
            MutableBoolean show = new MutableBoolean(false);
            subScreen.addDrawableChild(ExecutableWidget.instance(dx - dy + 2, 2, dy - 4, dy - 4)
                    .setElementHandler(IconElement.statedGuiPredicate(
                            Constants.EXPAND_GUI_ON_SPRITE,
                            Constants.EXPAND_GUI_OFF_SPRITE,
                            ButtonAction.run(() -> show.setValue(!show.booleanValue())),
                            (eee) -> show.booleanValue())));
            Supplier<SubScreenWidget> subScreenSupplier = Suppliers.memoize(() -> {
                SubScreenWidget selectors = new SubScreenWidget(0, 0, 0, 0).setPriority(1);
                int height = 0;
                for (int i = 0; i < choices; ++i) {
                    var section = flattenMap.get(i);
                    var supplier = section.getSecond();
                    final int finalI = i;
                    selectors.addDrawableChild(ExecutableWidget.instance(0, height, dx - dy - 4, dy)
                            .setElementHandler(new ColorBoxElement(
                                    ButtonAction.run(() -> {
                                        integer.set(finalI);
                                        indexUpdater.run();
                                        show.setValue(false);
                                    }),
                                    (el) -> supplier.get(),
                                    ColorSampler.of(Color.GRAY.getRGB()),
                                    ColorSampler.WHITE,
                                    (el, rb) -> {
                                        kvUpdater.run();
                                        if (integer.get() == finalI) {
                                            return Colors.GREEN;
                                        } else if (rb) {
                                            return Colors.WHITE;
                                        } else return null;
                                    })));
                    height += dy;
                }
                return selectors;
            });
            ContentDelegateWidget<SubScreenWidget> dynamicDelegate =
                    new DynamicContentWidget<>(() -> show.booleanValue() ? subScreenSupplier.get() : null, 2, dy);
            subScreen.addDrawableChild(dynamicDelegate);
            return subScreen;
        } else {
            // no choice
            return ExecutableWidget.instance(x + 1, y + 1, dx - 2, dy - 2)
                    .setElementHandler(new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.empty())
                            .withTooltips(TooltipHandler.of(List.of(Text.translatable(this.getKeyName())))));
        }
    }
}
