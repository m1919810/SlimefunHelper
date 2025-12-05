package me.matl114.gui.slimefun;

import com.google.common.collect.ImmutableList;
import com.sun.jna.platform.win32.WinNT;
import me.matl114.access.ScreenAccess;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.GridSelectSubScreen;
import me.matl114.gui.config.RegistrySelectScreen;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.UtilClass.AttrKeyValue;
import me.matl114.utils.UtilClass.Displayable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class SlimefunChoiceScreen<T> extends SlimefunScreen {
    final GridSelectSubScreen<T> selectGrid;
    ContentDelegateWidget<GridSelectSubScreen<T>> gridDelegate;
    Function<T, ItemStack> itemFilterFunction;
    List<Text> labelTooltips;
    public SlimefunChoiceScreen(Text title, List<T> values, Function<T, DrawableWidget> widgetFunction, Function<T, ItemStack> itemFilterFunction){
        this(title, null, ()-> values,  widgetFunction, itemFilterFunction);
    }

    public SlimefunChoiceScreen(Text title, List<Text> titleTooltips, Supplier<List<T>> originValue,  Function<T, DrawableWidget> widgetFunction, Function<T, ItemStack> itemFilterFunction) {
        super(title);
        this.itemFilterFunction = itemFilterFunction;
        this.selectGrid = new GridSelectSubScreen<>(
            0,TITLE_OCCUPIED , this.backgroundWidth  ,PAGE_LABEL_HEIGHT, 0, this.backgroundHeight - LABEL_OCCUPIED, -4,16,
            16, 16, this.wrapOriginValueProviders(originValue),null, widgetFunction
        );
        this.labelTooltips = titleTooltips;
    }
    private static enum NbtFilterRule implements Displayable {
        ANY(i->true, "&b无", "不进行任何过滤"),
        HAS_NBT_ONLY(ItemStackUtils::hasInPatch, "&aNBT", "保留含有NBT的物品"),
        NO_NBT_ONLY(i -> !ItemStackUtils.hasInPatch(i), "&cNBT","保留不含有NBT的物品"),
        HAS_CUSTOM_DATA_ONLY(ItemStackUtils::hasCustomData,"&aCNBT","保留含有CustomData的物品"),
        NO_CUSTOM_DATA_ONLY(i-> !ItemStackUtils.hasCustomData(i), "&cCNBT", "保留不含有CustomData的物品")
        ;
        final Predicate<ItemStack> itemFilter;
        final Text displayName;
        final String detail;
        NbtFilterRule(Predicate<ItemStack> itemFilter, String displayName, String detail){
            this.itemFilter = itemFilter;
            this.displayName = ChatUtils.stringToText( displayName);
            this.detail =detail;
        }

        @Override
        public Text getDisplay() {
            return displayName;
        }
    }
    private static final AttrKeyValue.EnumAttrKeyValue<NbtFilterRule> nbtFilter =
        AttrKeyValue.enumMap("NBT过滤规则",
                NbtFilterRule.ANY,
                Arrays.stream(NbtFilterRule.values())
                    .collect(Collectors.<NbtFilterRule, String, NbtFilterRule, LinkedHashMap<String, NbtFilterRule>>toMap(i -> i.detail, Function.identity(),  (existing, replacement) -> existing, LinkedHashMap::new))
            )
            .setIdentifier(NbtFilterRule.class);
    private static class ItemFilterRule{
        boolean blacklist = true;
        Set<Item> items = new LinkedHashSet<>();

        public void openModifyItemScreen(Runnable callback){
            ScreenAccess.of(new RegistrySelectScreen<Item>(Registries.ITEM, items, (i)->{
                items = i;
                callback.run();
            })).openFromCurrent();
        }
        public void reset(Runnable callback){
            items.clear();
            blacklist = true;
            callback.run();
        }

        public boolean acceptable(ItemStack stack){
            return blacklist != items.contains(stack.getItem());
        }


    }
    private static final ItemFilterRule itemFilter = new ItemFilterRule();
    private void resetNbtFilter(){
        nbtFilter.valueChange(null, NbtFilterRule.ANY.detail);
        executeFilterTask();
    }


    public Supplier<List<T>> wrapOriginValueProviders(Supplier<List<T>> originValue){
        return ()->originValue.get().stream()
            .filter(i -> nbtFilter.getOriginValue().itemFilter.test(itemFilterFunction.apply(i)))
            .filter(i -> itemFilter.acceptable(itemFilterFunction.apply(i)))
            .toList();
    }

    public SlimefunChoiceScreen<T> setSearchFilter(BiPredicate<String,T> filter){
        this.selectGrid.setFilter(filter);
        return this;
    }

    protected static final List<Text> SEARCH_TOOLTIP = List.of(Text.literal("在下方的输入框输入匹配字符"),Text.literal("点击本按钮用于刷新界面"), Text.literal("正常输入将按名字匹配"), Text.literal("输入空字符串将取消匹配"), Text.literal("输入@按id匹配(如果有id)").formatted(Formatting.GREEN));
    protected static final List<Text> NBT_FILTER_TOOLTIPS = List.of(Text.literal("NBT过滤规则设置: "),Text.literal("点击切换NBT过滤规则"), Text.literal("Shift点击重置NBT过滤规则"), Text.literal("-----------------------"));
    protected static final List<Text> ITEM_TYPE_FILTER_TOOLTIPS = List.of(Text.literal("物品类型过滤规则设置: "), Text.literal("左键点击选择物品类型名单"), Text.literal("右键点击切换黑白名单"),Text.literal( "Shift点击清空设置"), Text.literal("-----------------------"));

    protected List<Text> getSearchButtonTooltips(){
        return this.selectGrid.getFilter() != null ? SEARCH_TOOLTIP : super.getSearchButtonTooltips();
    }


    public void executeFilterTask(){
        this.selectGrid.getFilterTask().accept(FilterService.currentUserInput);
    }

    @Override
    protected List<Text> provideTitleTooltips(DrawableWidget widget) {
        return this.labelTooltips;
    }
    //filters
    //add NBT filter
    //add Material filter
    //todo add More filter
    //change vanilla recipe display to vanilla item display
    @Override
    protected void init() {
        super.init();

        int availableRenderSpace = this.backgroundHeight - LABEL_OCCUPIED ;
        this.selectGrid.resetGridHeightAndRefresh(availableRenderSpace);
        this.gridDelegate = new ContentDelegateWidget<>(this.x, this.y, 0,0)
            .setContentDelegate(this.selectGrid)
            .addTo(this);
        //Search button
        if(this.selectGrid.getFilter() != null){

            this.searchButton.setMouseHandler(InputHandler.run(this::executeFilterTask));

        }

        ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 12, 26, 26)
            .setMouseHandler(InputHandler.run(this::close))
            .setRenderHandler(PlateElement.instance().combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE,4, 4, 18,18)))
            .addTo(this)
        ;
    }

    protected void initBackground(){
        DisplayWidget.instance(this.x + this.backgroundWidth - 3, this.y + 64, 26, 26)
            .setRenderHandler(
                PlateElement.instance()
            )
            .addTo(this);
        nbtFilter.setIdentifier(NbtFilterRule.class);
        nbtFilter.generateSwitchingButton(this.x + this.backgroundWidth + 1, this.y + 68, 18, 18, (attr)->{
                if(Screen.hasShiftDown()){
                    //avoid recursive call

                    if(nbtFilter.getOriginValue() != NbtFilterRule.ANY){
                        resetNbtFilter();
                        //will definitely refresh in resetNbtFilter
                        return;
                    }
                }
                //run filter if not reset
                executeFilterTask();
            })
            .updateRenderHandler(h->((AbstractElement)h)
                .withTooltips(TooltipHandler.of(()->{
                    var builder = ImmutableList.<Text>builder();
                    builder.addAll(NBT_FILTER_TOOLTIPS);
                    builder.add(ChatUtils.stringToText("&7当前选项: &a%s".formatted(nbtFilter.getOriginValue().detail)));
                    return builder.build();
                }))
            )
            .addTo(this)
        ;
        TooltipHandler bwlistTooltips =TooltipHandler.of( ()->{
            var builder = ImmutableList.<Text>builder();
            builder.addAll(ITEM_TYPE_FILTER_TOOLTIPS);
            builder.add(ChatUtils.stringToText("&7当前选项: &a%s".formatted(itemFilter.blacklist? "黑名单": "白名单")));
            builder.add(ChatUtils.stringToText("&7名单内容:"));
            for (var re: itemFilter.items){
                builder.add(re.getName());
            }
            return builder.build();
        });
        SubScreenWidget.instance(this.x + this.backgroundWidth - 3, this.y + 90, 26, 26)
            .addDrawableChild(
                DisplayWidget.instance(0,0, 26,26)
                    .setRenderHandler(
                        PlateElement.instance()
                    )
            )
            .addDrawableChild(
                ExecutableWidget.instance(4,4, 18, 18)
                    .setMouseHandler(
                        new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.isLeft((left)->{
                            Runnable callback = this::executeFilterTask;
                            if (Screen.hasShiftDown()){
                                //clear
                                itemFilter.reset(callback);
                            }else {
                                if(left){
                                    itemFilter.openModifyItemScreen(callback);
                                }else {
                                    itemFilter.blacklist = !itemFilter.blacklist;
                                    callback.run();
                                }
                            }
                        }))
                    )
                    .setRenderHandler(
                        new AbstractElement()
                            .combineRender(
                                RenderHandler.ofSingleItem(()-> itemFilter.blacklist ? new ItemStack(Items.BLACK_WOOL): new ItemStack(Items.WHITE_WOOL), 1,1, false)
                            )
                            .withTooltips(bwlistTooltips)
                    )
            )
            .addTo(this);
        super.initBackground();
    }
}
