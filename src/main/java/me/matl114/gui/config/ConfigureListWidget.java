package me.matl114.gui.config;

import com.mojang.datafixers.util.Pair;
import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.index.IndexedSubScreen;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.lists.ListUnmodifiableWidget;
import me.matl114.managers.config.Config;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CollectionUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.PropertyTracker;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;

public class ConfigureListWidget extends IndexedSubScreen<Pair<String, Map<String, AttrKeyValue<?>>>, ListUnmodifiableWidget> {
    // ...                | fliter
    // second index list  | <key> : <value> |
    // total x
    //   indexDx          | buttonDx blankDx inputDx
    // column width 10
    public static ConfigureListWidget createConfigConfigure(Config config, int x, int y, int indexDx, int buttonDx, int blankDx, int inputDx, int dy, int dx,  int maxDy){

        return new ConfigureListWidget(config, x, y, dx, maxDy, indexDx, dy, blankDx, inputDx, buttonDx);
    }


    protected Config config;
    protected Map<String, ListEntryWidgetController> cache ;
    private static final Map<String, String> cachedConfigUserSelectIndex = new HashMap<>();
    private ContentDelegateWidget<TextFieldWidget> filterInputWidget;
    private boolean initialized = false;
    protected int blankDx;
    protected int inputDx;
    protected int buttonDx;
    // <key> : <value>
    // button blank input
    private ConfigureListWidget(Config config, int x, int y, int dx, int dy, int indexDx, int indexDy, int blankDx, int inputDx, int buttonDx) {
        super(getConfigIndexes(config), x, y, dx, dy, indexDx, indexDy);
        this.config = config;
        this.blankDx = blankDx;
        this.inputDx = inputDx;
        this.buttonDx = buttonDx;
        this.initialized = true;
        init();
    }


    @Override
    protected ElementHandler createIndexHandler(Pair<String,Map<String,AttrKeyValue<?>>> str) {
        return new ButtonElement(TextProvider.of(Text.translatable("config.index." + str.getFirst())), ButtonAction.run(()->this.setGlobal(str)))
            .setInactiveId(ButtonElement.BUTTON)
            .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
            .setActivePredicate((el)-> Objects.equals( cachedConfigUserSelectIndex.get(this.config.getConfigName()), str.getFirst()))
            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation("config.index." + str + ".tooltips", "暂无介绍")));
    }

    @Override
    public void setGlobal(Pair<String,Map<String,AttrKeyValue<?>>> config) {
        cachedConfigUserSelectIndex.put(this.config.getConfigName(), config.getFirst());
        this.selectIndexToDisplay(config);
    }


    @Override
    protected ListUnmodifiableWidget createSelectingDisplayWidget(Pair<String, Map<String, AttrKeyValue<?>>> val) {
        return new ListUnmodifiableWidget(
            this.cache.computeIfAbsent(val.getFirst(), (str)-> ListEntryWidgetController.immutable(
            this.getFromKeyOr(str, Map.of()).getSecond().values().stream().filter(this::applyFilter).toList(),
            b -> b.generateKeyValueInput(blankDx , 0 , this.buttonDx, blankDx, inputDx, this.buttonDy),
            buttonDy,
            buttonDx + blankDx + inputDx
        )), 20,
            buttonDy,
            buttonDx + blankDx + inputDx + 10,
            //减去 filter input
            this.dy - buttonDy
        );
    }
    protected Pair<String, Map<String, AttrKeyValue<?>>> getFromKey(String str){
        return this.list.stream().filter(s -> Objects.equals(str, s.getFirst())).findFirst().orElse(null);
    }
    protected Pair<String, Map<String, AttrKeyValue<?>>> getFromKeyOr(String str, Map<String, AttrKeyValue<?>> map){
        return this.list.stream().filter(s -> Objects.equals(str, s.getFirst())).findFirst().orElseGet(()-> new Pair<>(str, map));
    }
    @Override
    public Pair<String, Map<String, AttrKeyValue<?>>> getGlobal() {
        return getFromKey(cachedConfigUserSelectIndex.get(this.config.getConfigName()));
    }
    protected static List<Pair<String,Map<String,AttrKeyValue<?>>>> getConfigIndexes(Config config){
        Map<String, Map<String, AttrKeyValue<?>>> originValueWithIndex = new LinkedHashMap<>();

        for (var path : config.getPaths()){
            //todo: can we generate the widget by Ref, not attrKeyValue
            if(ChatUtils.hasTranslation(path)){
                String[] cut = Config.cutToPath(path);
                AttrKeyValue<?> keyValue = AttrKeyValue.ofConfigValue(path,  config.get(cut));
                //assert not empty
                String index = cut[0];
                originValueWithIndex.computeIfAbsent(index, (k)-> new LinkedHashMap<>()).put(path, keyValue);
            }

        }
        return originValueWithIndex.entrySet().stream().map(CollectionUtils::entryToPair).toList();
    }

    protected void init(){
        //cancel init in super
        if(!initialized)return;
        this.cache = new LinkedHashMap<>();
        this.filterInputWidget = McWidgetHelpers.createTextFieldEditBox(this.indexDx + 20, 1, this.inputDx + this.blankDx + this.buttonDx + 20 , this.buttonDy - 2, PropertyTracker.event(this::refreshFilter), "")
            .addToSub(this);
        ;
        super.init();
    }

    @Override
    public void saveSelected() {
        for(var entry : this.list) {
            for (var value: entry.getSecond().entrySet()){
                config.setValueNoNew(value.getValue().getOriginValue(), Config.cutToPath(value.getKey()));
            }
        }
        config.markForSave();
        Config.launchSaveTasks();
    }

    protected boolean applyFilter(AttrKeyValue<?> keyValue){
        String filter = filterInputWidget.getDelegate().getText();
        if (filter.isEmpty()){
            return true;
        }else{
            return FilterService.nameMatch(Text.translatableWithFallback(keyValue.getKeyName(), keyValue.getKeyName()).getString(), filter);
        }
    }
    protected void refreshFilter(String filter){
        String value = cachedConfigUserSelectIndex.get(this.config.getConfigName());
        if(value != null){
            recreateIndexWidget(value);
            selectIndexToDisplay(getFromKey(value));
        }
    }

    protected void recreateIndexWidget(String key){
        this.cache.remove(key);
    }

}
