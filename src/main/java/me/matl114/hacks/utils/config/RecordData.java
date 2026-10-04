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
        MutableRecord record = keyValue.get().toMutable();
        DrawableWidget widget = WidgetUtils.createMutableRecordEditScreen(
                Text.translatable("widget.nbt-parsable.record-data.edit-screen.title"),
                List::of,
                record,
                Function.identity(),
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT);
        CenterScreen newScreen = new CenterScreen(widget);
        newScreen.access().addCloseFuture(() -> {
            keyValue.accept(new RecordData(record.toOrderedMap()));
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
            (factory, custom, x, y, dx, dy) -> {
                SubScreenWidget widget = new SubScreenWidget(x, y, dx, dy);
                int size = custom.get().map().size();
                if (size > 0) {
                    int dxx = dx > 2 * dy ? dx - dy : dx;
                    widget.addDrawableChild(factory.createExecuteButton(
                            () -> Text.translatable("widget.nbt-parsable.record-data.open-edit-screen"),
                            () -> ChatUtils.parseTooltipsTranslation(
                                    "widget.nbt-parsable.record-data.open-edit-screen.tooltips", ""),
                            ButtonAction.run(() -> openEditorScreen(custom)),
                            0,
                            0,
                            dxx,
                            dy));
                    if (dxx < dx) {
                        widget.addDrawableChild(factory.createElement(
                                IconElement.fixedGui(
                                                Constants.EDITOR_SPRITE,
                                                ButtonAction.run(() -> openEditorScreen(custom)))
                                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                "widget.nbt-parsable.record-data.open-edit-screen.tooltips", ""))),
                                null,
                                null,
                                dxx,
                                0,
                                dx - dxx,
                                dy));
                    }

                } else {
                    widget.addDrawableChild(factory.createExecuteButton(
                            () -> Text.translatable("widget.nbt-parsable.record-data.no-editable-field"),
                            () -> ChatUtils.parseTooltipsTranslation(
                                    "widget.nbt-parsable.record-data.no-editable-field.tooltips", ""),
                            ButtonAction.empty(),
                            0,
                            0,
                            dx,
                            dy));
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
