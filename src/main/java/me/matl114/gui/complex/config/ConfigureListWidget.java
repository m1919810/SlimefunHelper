package me.matl114.gui.complex.config;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.presets.index.IndexedSubScreen;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.lists.ListUnmodifiableWidget;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CollectionUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.PropertyTracker;
import me.matl114.utils.containers.ArgsMap;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigureListWidget
        extends IndexedSubScreen<Pair<String, Map<String, ConfigureListWidget.Entry<?>>>, ListUnmodifiableWidget> {
    // ...                | fliter
    // second index list  | <key> : <value> |
    // total x
    //   indexDx          | buttonDx blankDx inputDx
    // column width 10
    public static ConfigureListWidget createConfigConfigure(
            Config config,
            int x,
            int y,
            int indexDx,
            int buttonDx,
            int blankDx,
            int inputDx,
            int dy,
            int dx,
            int maxDy,
            StringRef filterWidget) {

        return new ConfigureListWidget(
                config,
                x,
                y,
                dx,
                maxDy,
                indexDx,
                dy,
                blankDx,
                inputDx,
                buttonDx,
                new ArgsMap().put(FILTER_TEXT_WIDGET, filterWidget));
    }

    protected Config config;
    protected Map<String, ListEntryWidgetController> cache;
    private static final Map<String, String> cachedConfigUserSelectIndex = new HashMap<>();
    public static final String FILTER_TEXT_WIDGET = "slimefunhelper:configure_list_widget/filter_text_widget";
    private ArgsMap argsMap;
    private ContentDelegateWidget<TextFieldWidget> filterInputWidget;
    private boolean initialized = false;
    protected int blankDx;
    protected int inputDx;
    protected int buttonDx;
    // <key> : <value>
    // button blank input
    private ConfigureListWidget(
            Config config,
            int x,
            int y,
            int dx,
            int dy,
            int indexDx,
            int indexDy,
            int blankDx,
            int inputDx,
            int buttonDx,
            ArgsMap args) {
        super(getConfigIndexes(config), x, y, dx, dy, indexDx, indexDy);
        this.config = config;
        this.blankDx = blankDx;
        this.inputDx = inputDx;
        this.buttonDx = buttonDx;
        this.initialized = true;
        this.argsMap = args;
        init();
    }

    @Override
    protected ElementHandler createIndexHandler(Pair<String, Map<String, Entry<?>>> str) {
        return new ButtonElement(
                        TextProvider.of(Text.translatable("config.index." + str.getFirst())),
                        ButtonAction.run(() -> this.setGlobal(str)))
                .setInactiveId(ButtonElement.BUTTON)
                .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
                .setActivePredicate((el) ->
                        Objects.equals(cachedConfigUserSelectIndex.get(this.config.getConfigName()), str.getFirst()))
                .withTooltips(TooltipHandler.of(
                        ChatUtils.parseTooltipsTranslation("config.index." + str + ".tooltips", "暂无介绍")));
    }

    @Override
    public void setGlobal(Pair<String, Map<String, Entry<?>>> config) {
        cachedConfigUserSelectIndex.put(this.config.getConfigName(), config.getFirst());
        this.selectIndexToDisplay(config, false);
    }

    @Override
    protected ListUnmodifiableWidget createSelectingDisplayWidget(Pair<String, Map<String, Entry<?>>> val) {
        String str = val.getFirst();
        return new ListUnmodifiableWidget(
                ListEntryWidgetController.<Entry<?>, SubScreenWidget>immutable(
                        this.getFromKeyOr(str, Map.of()).getSecond().values().stream()
                                .filter(this::applyFilter)
                                .toList(),
                        b -> new DefaultedKeyValueInputWidget(
                                blankDx,
                                0,
                                this.buttonDx + blankDx + inputDx,
                                this.buttonDy,
                                this.buttonDx,
                                blankDx,
                                inputDx,
                                b.ref(),
                                b.keyValue()),
                        buttonDy,
                        buttonDx + blankDx + inputDx),
                20,
                buttonDy,
                buttonDx + blankDx + inputDx + 10,
                // 减去 filter input
                this.dy - buttonDy);
    }

    protected Pair<String, Map<String, Entry<?>>> getFromKey(String str) {
        return this.list.stream()
                .filter(s -> Objects.equals(str, s.getFirst()))
                .findFirst()
                .orElse(null);
    }

    protected Pair<String, Map<String, Entry<?>>> getFromKeyOr(String str, Map<String, Entry<?>> map) {
        return this.list.stream()
                .filter(s -> Objects.equals(str, s.getFirst()))
                .findFirst()
                .orElseGet(() -> new Pair<>(str, map));
    }

    @Override
    public Pair<String, Map<String, Entry<?>>> getGlobal() {
        return getFromKey(cachedConfigUserSelectIndex.get(this.config.getConfigName()));
    }

    protected static List<Pair<String, Map<String, Entry<?>>>> getConfigIndexes(Config config) {
        Map<String, Map<String, Entry<?>>> originValueWithIndex = new LinkedHashMap<>();

        for (var path : config.getVisiblePaths()) {
            if (!ChatUtils.hasTranslation(path)) {
                Debug.info("Missing translation key for", path);
            }
            String[] cut = Config.cutToPath(path);
            Ref<?> ref = config.get(cut);
            AttrKeyValue<?> keyValue = ref.createKeyValue(path); // AttrKeyValue.ofConfigValue(path, );
            // assert not empty
            String index = cut[0];
            originValueWithIndex
                    .computeIfAbsent(index, (k) -> new LinkedHashMap<>())
                    .put(path, new Entry(ref, keyValue));
        }
        return originValueWithIndex.entrySet().stream()
                .map(CollectionUtils::entryToPair)
                .toList();
    }

    protected void init() {
        // cancel init in super
        if (!initialized) return;
        this.cache = new LinkedHashMap<>();
        StringRef filterWidget = this.argsMap.get(FILTER_TEXT_WIDGET);
        this.filterInputWidget = McWidgetHelpers.createTextFieldEditBox(
                        this.indexDx + 20,
                        1,
                        this.inputDx + this.blankDx + this.buttonDx + 20,
                        this.buttonDy - 2,
                        PropertyTracker.event(this::refreshFilter),
                        filterWidget.get())
                .addToSub(this);
        ;
        super.init();
    }

    @Override
    public void saveSelected() {
        for (var entry : this.list) {
            for (var value : entry.getSecond().values()) {
                value.save();
            }
        }
        config.markForSave();
        Config.launchSaveTasks();
    }

    protected boolean applyFilter(Entry<?> keyValue) {
        String filter = filterInputWidget.getDelegate().getText();
        if (filter.isEmpty()) {
            return true;
        } else {
            return FilterService.nameMatch(
                    Text.translatableWithFallback(
                                    keyValue.keyValue().getKeyName(),
                                    keyValue.keyValue().getKeyName())
                            .getString(),
                    filter);
        }
    }

    protected void refreshFilter(String filter) {
        StringRef filterWidget = this.argsMap.get(FILTER_TEXT_WIDGET);
        filterWidget.set(filter);
        String value = cachedConfigUserSelectIndex.get(this.config.getConfigName());
        if (value != null) {
            recreateIndexWidget(value);
            selectIndexToDisplay(getFromKey(value), true);
        }
    }

    protected void recreateIndexWidget(String key) {
        this.cache.remove(key);
    }

    public record Entry<T>(Ref<T> ref, AttrKeyValue<T> keyValue) {
        public void save() {
            ref.setValue(keyValue.getOriginValue());
        }
    }
}
