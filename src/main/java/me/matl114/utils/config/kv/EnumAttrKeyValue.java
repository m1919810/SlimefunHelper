package me.matl114.utils.config.kv;

import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.experimental.Accessors;
import lombok.val;
import me.matl114.api.Displayable;
import me.matl114.gui.basic.*;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.Text;
import org.apache.commons.lang3.function.Consumers;

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

    public ExecutableWidget generateSwitchingButton(
            int x, int y, int dx, int dy, Consumer<EnumAttrKeyValue<T>> changelistener) {
        if (Displayable.class.isAssignableFrom(identifier)) {
            Map<String, Displayable> valueMap = (Map<String, Displayable>) (this).getValueMap();
            List<Pair<String, Displayable>> flattenMap = valueMap.entrySet().stream()
                    .map((entry) -> new Pair<>(entry.getKey(), entry.getValue()))
                    .toList();
            int choices = flattenMap.size();
            if (choices > 0) {
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
                }
                AtomicInteger integer = new AtomicInteger();
                integer.set(index);
                return ExecutableWidget.instance(x + 1, y + 1, dx - 2, dy - 2)
                        .setElementHandler(new ButtonElement(
                                        (ign) -> flattenMap
                                                .get(integer.get())
                                                .getSecond()
                                                .getDisplay(),
                                        ButtonAction.run(() -> {
                                            int index0 = integer.get();
                                            index0 = (index0 + 1) % choices;
                                            integer.set(index0);
                                            this.valueChange(
                                                    this, flattenMap.get(index0).getFirst());
                                            changelistener.accept(this);
                                        }))
                                .withTooltips(TooltipHandler.of(List.of(Text.translatable(this.getKeyName())))));
            } else {
                // no choice
                return ExecutableWidget.instance(x + 1, y + 1, dx - 2, dy - 2)
                        .setElementHandler(new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.empty())
                                .withTooltips(TooltipHandler.of(List.of(Text.translatable(this.getKeyName())))));
            }

            // .addToSub(this);
        } else {
            List<String> flattenMap =
                    ((EnumAttrKeyValue<T>) this).getValueMap().keySet().stream().toList();
            int choices = flattenMap.size();
            if (choices > 0) {
                int index = flattenMap.indexOf(this.getValue());
                if (index == -1) {
                    this.valueChange(this, flattenMap.get(0));
                    index = 0;
                }
                AtomicInteger integer = new AtomicInteger();
                integer.set(index);
                return ExecutableWidget.instance(x + 1, y + 1, dx - 2, dy - 2)
                        .setElementHandler(new ButtonElement(
                                        (ign) -> Text.literal(flattenMap.get(integer.get())), ButtonAction.run(() -> {
                                            int index0 = integer.get();
                                            index0 = (index0 + 1) % choices;
                                            integer.set(index0);
                                            this.valueChange(this, flattenMap.get(index0));
                                            changelistener.accept(this);
                                        }))
                                .withTooltips(TooltipHandler.of(List.of(Text.literal(this.getKeyName())))));
            } else {
                return ExecutableWidget.instance(x + 1, y + 1, dx - 2, dy - 2)
                        .setElementHandler(new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.empty())
                                .withTooltips(TooltipHandler.of(List.of(Text.literal(this.getKeyName())))));
            }
        }
    }
}
