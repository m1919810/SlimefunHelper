package me.matl114.hacks.utils.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import me.matl114.gui.Constants;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.managers.config.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.text.Text;

public class RecordData implements NBTParsable<RecordData> {
    Map<String, ?> recordMap;

    public RecordData(Map<String, ?> recordMap) {
        this.recordMap = new LinkedHashMap<>(recordMap);
    }

    public Map<String, ?> map() {
        return recordMap;
    }

    private static RecordData fromRefs(List<Pair<String, Ref<?>>> refMap) {
        LinkedHashMap<String, Object> linkedMap = new LinkedHashMap<>();
        for (var re : refMap) {
            linkedMap.put(re.getFirst(), re.getSecond().getValue());
        }
        return new RecordData(linkedMap);
    }

    public <T> T get(String key) {
        return (T) recordMap.get(key);
    }

    public <T> T get(String key, T defaultValue) {
        return (T) ((Map) recordMap).getOrDefault(key, defaultValue);
    }

    private List<Pair<String, Ref<?>>> toRefs() {
        return (List) recordMap.entrySet().stream()
                .map(s -> Pair.of(s.getKey(), (Ref) Refs.wrapInstance(s.getValue())))
                .toList();
    }

    public MutableRecord toMutable() {
        return new MutableRecord(recordMap.keySet().stream().toList(), new LinkedHashMap<>(recordMap));
    }

    private static void openEditorScreen(AttrKeyValue<RecordData> keyValue) {
        MutableRecord record = keyValue.getOriginValue().toMutable();
        DrawableWidget widget = WidgetUtils.createMutableRecordEditScreen(
                Text.translatable("widget.nbt-parsable.record-data.edit-screen.title"),
                List::of,
                record,
                Function.identity(),
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                WidgetUtils.DEFAULT_PALETTE);
        CenterScreen newScreen = new CenterScreen(widget);
        newScreen.access().addCloseFuture(() -> {
            keyValue.valueChangeInternal(null, new RecordData(record.toOrderedMap()));
        });
        newScreen.access().openFromCurrent();
    }

    public static final NBTType<RecordData> TYPE = new NBTType<RecordData>(
            "recorddata",
            Codec.<Pair<String, Ref<?>>>list(RecordCodecBuilder.create(oinstance -> oinstance
                            .group(
                                    Codec.STRING.fieldOf("key").forGetter(Pair::getFirst),
                                    Refs.CODEC.fieldOf("value").forGetter(Pair::getSecond))
                            .apply(oinstance, Pair::new)))
                    .<RecordData>xmap(RecordData::fromRefs, RecordData::toRefs),
            (custom, x, y, dx, dy) -> {
                SubScreenWidget widget = new SubScreenWidget(x, y, dx, dy);
                int size = custom.getOriginValue().map().size();
                if (size > 0) {
                    int dxx = dx > 2 * dy ? dx - dy : dx;
                    widget.addDrawableChild(ExecutableWidget.instance(0, 0, dxx, dy)
                            .setElementHandler(new ButtonElement(
                                            TextProvider.of(Text.translatable(
                                                    "widget.nbt-parsable.record-data.open-edit-screen")),
                                            ButtonAction.run(() -> openEditorScreen(custom)))
                                    .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                            "widget.nbt-parsable.record-data.open-edit-screen.tooltips", "")))));
                    if (dxx < dx) {
                        widget.addDrawableChild(ExecutableWidget.instance(dxx, 0, dx - dxx, dy)
                                .setElementHandler(IconElement.fixedGui(
                                                Constants.EDITOR_SPRITE,
                                                ButtonAction.run(() -> openEditorScreen(custom)))
                                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                "widget.nbt-parsable.record-data.open-edit-screen.tooltips", "")))));
                    }

                } else {
                    widget.addDrawableChild(ExecutableWidget.instance(0, 0, dx, dy)
                            .setElementHandler(new ButtonElement(
                                            TextProvider.of(Text.translatable(
                                                    "widget.nbt-parsable.record-data.no-editable-field")),
                                            ButtonAction.empty())
                                    .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                            "widget.nbt-parsable.record-data.no-editable-field.tooltips", "")))));
                }

                return widget;
            },
            new RecordData(Map.of()));

    @Override
    public NBTType<RecordData> type() {
        return TYPE;
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof RecordData data
                && Objects.equals(data.map().keySet(), map().keySet());
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof RecordData data && Objects.equals(data.recordMap, recordMap));
    }

    @Override
    public int hashCode() {
        return recordMap.hashCode();
    }
}
