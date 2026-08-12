package me.matl114.gui.complex.slimefun;

import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.api.Displayable;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.PlateElement;
import me.matl114.gui.presets.choices.RegistrySelectScreen;
import me.matl114.gui.presets.grids.GridSelectSubScreen;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.kv.EnumAttrKeyValue;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public class SlimefunChoiceScreen<T> extends SlimefunScreen {
    final GridSelectSubScreen<T> selectGrid;
    ContentDelegateWidget<GridSelectSubScreen<T>> gridDelegate;
    Function<T, ItemStack> itemFilterFunction;
    List<Text> labelTooltips;
    public static String currentInputFilter = "";

    public SlimefunChoiceScreen(
            Text title,
            List<T> values,
            Function<T, DrawableWidget> widgetFunction,
            Function<T, ItemStack> itemFilterFunction) {
        this(title, null, () -> values, widgetFunction, itemFilterFunction);
    }

    public SlimefunChoiceScreen(
            Text title,
            List<Text> titleTooltips,
            Supplier<List<T>> originValue,
            Function<T, DrawableWidget> widgetFunction,
            Function<T, ItemStack> itemFilterFunction) {
        super(title);
        this.itemFilterFunction = itemFilterFunction;
        this.selectGrid = new GridSelectSubScreen<>(
                0,
                TITLE_OCCUPIED,
                this.backgroundWidth,
                PAGE_LABEL_HEIGHT,
                0,
                this.backgroundHeight - LABEL_OCCUPIED,
                -4,
                16,
                16,
                16,
                this.wrapOriginValueProviders(originValue),
                null,
                ValueAccessor.of(() -> currentInputFilter, (s) -> currentInputFilter = s),
                widgetFunction);
        this.labelTooltips = titleTooltips;
    }

    private static enum NbtFilterRule implements Displayable {
        ANY(
                i -> true,
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.any",
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.any.detail"),
        HAS_NBT_ONLY(
                ItemStackUtils::hasInPatch,
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.has-nbt",
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.has-nbt.detail"),
        NO_NBT_ONLY(
                i -> !ItemStackUtils.hasInPatch(i),
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.no-nbt",
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.no-nbt.detail"),
        HAS_CUSTOM_DATA_ONLY(
                ItemStackUtils::hasCustomData,
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.has-custom-data",
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.has-custom-data.detail"),
        NO_CUSTOM_DATA_ONLY(
                i -> !ItemStackUtils.hasCustomData(i),
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.no-custom-data",
                "widget.gui.slimefun-choice-screen.nbt-filter.rule.no-custom-data.detail");
        final Predicate<ItemStack> itemFilter;
        final Text displayName;
        final String detailKey;
        final Text detail;

        NbtFilterRule(Predicate<ItemStack> itemFilter, String displayNameKey, String detailKey) {
            this.itemFilter = itemFilter;
            this.displayName = Text.translatable(displayNameKey);
            this.detailKey = detailKey;
            this.detail = Text.translatable(detailKey);
        }

        @Override
        public Text getDisplay() {
            return displayName;
        }
    }

    private static final EnumAttrKeyValue<NbtFilterRule> nbtFilter = AttrKeyValue.enumMap(
            "NBT过滤规则",
            NbtFilterRule.ANY,
            Arrays.stream(NbtFilterRule.values())
                    .collect(
                            Collectors
                                    .<NbtFilterRule, String, NbtFilterRule, LinkedHashMap<String, NbtFilterRule>>toMap(
                                            i -> i.detailKey,
                                            Function.identity(),
                                            (existing, replacement) -> existing,
                                            LinkedHashMap::new)));

    private static class ItemFilterRule {
        boolean blacklist = true;
        Set<Item> items = new LinkedHashSet<>();

        public void openModifyItemScreen(Runnable callback) {
            ScreenAccess.of(new RegistrySelectScreen<Item>(Registries.ITEM, items, (i) -> {
                        items = i;
                        callback.run();
                    }))
                    .openFromCurrent();
        }

        public void reset(Runnable callback) {
            items.clear();
            blacklist = true;
            callback.run();
        }

        public boolean acceptable(ItemStack stack) {
            return blacklist != items.contains(stack.getItem());
        }
    }

    private static final ItemFilterRule itemFilter = new ItemFilterRule();

    private void resetNbtFilter() {
        nbtFilter.valueChange(null, NbtFilterRule.ANY.detailKey);
        executeFilterTask();
    }

    public Supplier<List<T>> wrapOriginValueProviders(Supplier<List<T>> originValue) {
        return () -> originValue.get().stream()
                .filter(i -> nbtFilter.getOriginValue().itemFilter.test(itemFilterFunction.apply(i)))
                .filter(i -> itemFilter.acceptable(itemFilterFunction.apply(i)))
                .toList();
    }

    public SlimefunChoiceScreen<T> setSearchFilter(BiPredicate<String, T> filter) {
        this.selectGrid.setFilter(filter);
        return this;
    }

    protected List<Text> getSearchButtonTooltips() {
        return this.selectGrid.getFilter() != null
                ? ChatUtils.parseTooltipsTranslation("widget.gui.slimefun-choice-screen.search.tooltips", "")
                : super.getSearchButtonTooltips();
    }

    public void executeFilterTask() {
        this.selectGrid.getFilterTask().accept(currentInputFilter);
    }

    @Override
    protected List<Text> provideTitleTooltips(DrawableWidget widget) {
        return this.labelTooltips;
    }
    // filters
    // add NBT filter
    // add Material filter
    // todo add More filter
    // change vanilla recipe display to vanilla item display
    @Override
    protected void init() {
        super.init();

        int availableRenderSpace = this.backgroundHeight - LABEL_OCCUPIED;
        this.selectGrid.resetGridHeightAndRefresh(availableRenderSpace);
        this.gridDelegate = new ContentDelegateWidget<>(this.x, this.y, 0, 0)
                .setContentDelegate(this.selectGrid)
                .addTo(this);
        // Search button
        if (this.selectGrid.getFilter() != null) {

            this.searchButton.setInputHandler(InputHandler.run(this::executeFilterTask));
        }

        ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 12, 26, 26)
                .setInputHandler(InputHandler.run(this::close))
                .setRenderHandler(PlateElement.instance()
                        .combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE, 4, 4, 18, 18)))
                .addTo(this);
    }

    protected void initBackground() {
        DisplayWidget.instance(this.x + this.backgroundWidth - 3, this.y + 64, 26, 26)
                .setRenderHandler(PlateElement.instance())
                .addTo(this);
        nbtFilter
                .generateSwitchingButton(this.x + this.backgroundWidth + 1, this.y + 68, 18, 18, (attr) -> {
                    if (ScreenUtils.hasShiftDown()) {
                        // avoid recursive call

                        if (nbtFilter.getOriginValue() != NbtFilterRule.ANY) {
                            resetNbtFilter();
                            // will definitely refresh in resetNbtFilter
                            return;
                        }
                    }
                    // run filter if not reset
                    executeFilterTask();
                })
                .updateRenderHandler(h -> ((AbstractElement) h).withTooltips(TooltipHandler.of(() -> {
                    var builder = ImmutableList.<Text>builder();
                    builder.addAll(ChatUtils.parseTooltipsTranslation(
                            "widget.gui.slimefun-choice-screen.nbt-filter.tooltips", ""));
                    builder.add(Text.translatable(
                            "widget.gui.slimefun-choice-screen.nbt-filter.current-option",
                            nbtFilter.getOriginValue().detail));
                    return builder.build();
                })))
                .addTo(this);
        TooltipHandler bwlistTooltips = TooltipHandler.of(() -> {
            var builder = ImmutableList.<Text>builder();
            builder.addAll(ChatUtils.parseTooltipsTranslation(
                    "widget.gui.slimefun-choice-screen.item-type-filter.tooltips", ""));
            builder.add(Text.translatable(
                    "widget.gui.slimefun-choice-screen.item-type-filter.current-option",
                    itemFilter.blacklist
                            ? Text.translatable("widget.gui.slimefun-choice-screen.item-type-filter.blacklist")
                            : Text.translatable("widget.gui.slimefun-choice-screen.item-type-filter.whitelist")));
            builder.add(Text.translatable("widget.gui.slimefun-choice-screen.item-type-filter.list-content"));
            for (var re : itemFilter.items) {
                builder.add(re.getName());
            }
            return builder.build();
        });
        SubScreenWidget.instance(this.x + this.backgroundWidth - 3, this.y + 90, 26, 26)
                .addDrawableChild(DisplayWidget.instance(0, 0, 26, 26).setRenderHandler(PlateElement.instance()))
                .addDrawableChild(ExecutableWidget.instance(4, 4, 18, 18)
                        .setInputHandler(
                                new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.isLeft((left) -> {
                                    Runnable callback = this::executeFilterTask;
                                    if (ScreenUtils.hasShiftDown()) {
                                        // clear
                                        itemFilter.reset(callback);
                                    } else {
                                        if (left) {
                                            itemFilter.openModifyItemScreen(callback);
                                        } else {
                                            itemFilter.blacklist = !itemFilter.blacklist;
                                            callback.run();
                                        }
                                    }
                                })))
                        .setRenderHandler(new AbstractElement()
                                .combineRender(RenderHandler.ofSingleItem(
                                        () -> itemFilter.blacklist
                                                ? new ItemStack(Items.BLACK_WOOL)
                                                : new ItemStack(Items.WHITE_WOOL),
                                        1,
                                        1,
                                        false))
                                .withTooltips(bwlistTooltips)))
                .addTo(this);
        super.initBackground();
    }
}
