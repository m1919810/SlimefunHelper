package me.matl114.gui.config;

import me.matl114.gui.basic.*;
import me.matl114.managers.Config;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.UtilClass.AttrKeyValue;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Function;

public class ConfigureListWidget extends SubScreenWidget {
    public static ConfigureListWidget createConfigConfigure(Config config, int x, int y, int indexDx, int buttonDx, int blankDx, int inputDx, int dy, int dx,  int maxDy){
        Function<List<AttrKeyValue<?>>, ListEntryWidgetController> factory = (list)-> ListEntryWidgetController.immutable(
            list,
            b -> b.generateKeyValueInput(blankDx, 0, buttonDx, blankDx, inputDx, dy),
            dy,
            buttonDx + blankDx + inputDx
            );
        return new ConfigureListWidget(config, factory, x, y, dx, maxDy, indexDx, dy);
    }
    private Config config;
    private Function<List<AttrKeyValue<?>>, ListEntryWidgetController> listFactory;
    private Map<String, Map<String, AttrKeyValue<?>>> originValueWithIndex;
    private Map<String, ListEntryWidgetController> cache = new HashMap<>();
    private static final Map<String, String> cachedConfigUserSelectIndex = new HashMap<>();
    private ListUnmodifiableWidget indexListWidget;
    private ContentDelegateWidget<ListUnmodifiableWidget> displayedList;
    private int indexDx;
    private int buttonDy;
    private ConfigureListWidget(Config config, Function<List<AttrKeyValue<?>>, ListEntryWidgetController> listFactory, int x, int y, int dx, int dy, int indexDx, int indexDy) {
        super(x, y, dx, dy);
        this.indexDx = indexDx;
        this.buttonDy = indexDy;
        this.config = config;
        this.listFactory = listFactory;
        init();
    }
    protected void init(){
        this.originValueWithIndex = new LinkedHashMap<>();

        for (var path : config.getPaths()){
            String[] cut = Config.cutToPath(path);
            //todo: can we generate the widget by Ref, not attrKeyValue
            AttrKeyValue<?> keyValue = AttrKeyValue.ofConfigValue(path,  config.get(cut));
            //assert not empty
            String index = cut[0];
            this.originValueWithIndex.computeIfAbsent(index, (k)-> new LinkedHashMap<>()).put(path, keyValue);
        }
        List<String> indexList = this.originValueWithIndex.keySet().stream().toList();
        ListEntryWidgetController controller = ListEntryWidgetController.immutable(
            indexList, (str)-> ExecutableWidget.instance(0, 0, this.indexDx , this.buttonDy)
                .setElementHandler(
                    new ButtonElement(TextProvider.of(Text.translatable("config.index." + str)), ButtonAction.run(()->this.selectIndexToDisplay(str)))
                        .setInactiveId(ButtonElement.BUTTON)
                        .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
                        .setActivePredicate((el)-> Objects.equals( cachedConfigUserSelectIndex.get(this.config.getConfigName()), str))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation("config.index." + str + ".tooltips", "暂无介绍")))
                ),
            this.buttonDy, this.indexDx
        );
        this.indexListWidget = new ListUnmodifiableWidget(
            controller, 0,0, this.indexDx + 4, this.dy
        ).addToSub(this);
        this.displayedList = new ContentDelegateWidget<>(this.indexDx + 20,0, this.dx - this.indexDx - 10, this.dy )
            .addToSub(this);
        String userHistory = cachedConfigUserSelectIndex.get(this.config.getConfigName());
        if(userHistory != null){
            selectIndexToDisplay(userHistory);
        }
    }
    private void selectIndexToDisplay(String key){
        cachedConfigUserSelectIndex.put(this.config.getConfigName(), key);
        ListEntryWidgetController controller = this.cache.computeIfAbsent(key, (str)-> listFactory.apply(this.originValueWithIndex.getOrDefault(str, Map.of()).values().stream().toList()));
        this.displayedList.setContentDelegate(
            new ListUnmodifiableWidget(
                controller,
                0,0, this.dx - this.indexDx - 10, this.dy
            )
        );
    }
    public void save(){
        for(var entry: this.originValueWithIndex.values()) {
            for (var value: entry.entrySet()){
                config.setValueNoNew(value.getValue().getOriginValue(), Config.cutToPath(value.getKey()));
            }
        }
        config.save();
    }
}
