package me.matl114.gui.complex.slimefun;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import lombok.Getter;
import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.LabelElement;
import me.matl114.gui.elements.PageButtonElement;
import me.matl114.gui.elements.SlotElement;
import me.matl114.gui.presets.single.IntFastInputWidget;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.hacks.modules.slimefun.MultiBlockHelper;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.managers.Tasks;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

public class SlimefunDispensorSuggestBookWidget extends SubScreenWidget {

    protected static final int DX = 168;
    protected static final int DY = 75;
    protected static final int START_DY = 10;
    protected static final int END_DY = 74;
    protected static final int SLOT_SIZE = 8;
    protected static final int TEXT_START_DY = 68;
    protected static final int TEXT_START_DX = 56;
    protected static final ItemStack BOOK_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);
    protected static final ItemStack REFRESH_ICON = new ItemStack(Items.STRUCTURE_VOID);
    protected static final Text REFRESH_HARD =
            Text.translatable("widget.gui.slimefun-dispensor-suggest-book-widget.refresh-hard");
    protected static final Text REFRESH_SOFT =
            Text.translatable("widget.gui.slimefun-dispensor-suggest-book-widget.refresh-soft");
    protected static final Text TITLE = Text.translatable("widget.gui.slimefun-dispensor-suggest-book-widget.title");

    protected static final Text MULTIBLOCK_EXECUTE =
            Text.translatable("widget.gui.slimefun-dispensor-suggest-book-widget.multiblock-execute");
    protected static final Text MULTIBLOCK_AUTO =
            Text.translatable("widget.gui.slimefun-dispensor-suggest-book-widget.multiblock-auto");

    protected ExecutableWidget toggleBookWidget;
    protected ExecutableWidget prevPage;
    protected ExecutableWidget nextPage;
    protected ExecutableWidget switchHard;
    protected ExecutableWidget multiblockExecuteOneWidget;
    protected ExecutableWidget titleWidget;
    protected ExecutableWidget multiblockExecuteWidget;
    protected ExecutableWidget multiblockAutoExecute;
    protected ExecutableWidget refresh;
    protected DrawableWidget textField;
    protected ContentDelegateWidget<DrawableWidget> toggleActivateTextField;
    protected ContentDelegateWidget<DrawableWidget> hovering;

    protected volatile List<RecipeEntry> originItems;
    protected volatile List<RecipeEntry> filterItems;

    protected BiConsumer<Integer, RecipeEntry> callback;
    protected boolean onlyShowRelated = true;

    @Getter
    int page = 1;

    @Getter
    int maxPage = 1;

    protected static final int maxElementInPage = 2 * 4 * 5;
    protected static final ContentDelegateWidget<ExecutableWidget>[] contents =
            new ContentDelegateWidget[maxElementInPage];

    static {
        for (int i = 0; i < maxElementInPage; ++i) {
            if (i < maxElementInPage / 2) {
                int ix = i % 4;
                int iy = i / 4;
                contents[i] = new ContentDelegateWidget<>(ix * 12, START_DY + iy * 12, 12, 12);
            } else {
                int it = i - maxElementInPage / 2;
                int ix = it % 4;
                int iy = it / 4;
                contents[i] = new ContentDelegateWidget<>(DX - 48 + ix * 12, START_DY + iy * 12, 12, 12);
            }
        }
    }

    public void setPage(int p) {
        int oldPage = this.page;
        this.page = MathHelper.clamp(p, 1, maxPage);
        if (this.page != oldPage) {
            resetPage();
        }
    }

    protected static boolean activate;
    protected Collection<String> type;
    protected TileInventory tile;

    public SlimefunDispensorSuggestBookWidget(
            TileInventory tile,
            int x,
            int y,
            Collection<String> optionalType,
            BiConsumer<Integer, RecipeEntry> callback) {
        super(x, y, DX, DY);
        this.callback = callback;
        this.type = optionalType;
        this.tile = tile;
        init();
    }

    protected void toggleActive() {
        activate = !activate;
        refreshActiveState();
    }

    public void refreshActiveState() {
        if (activate) {
            this.toggleActivateTextField.setContentDelegate(this.textField);
            Tasks.scheduleDelayed(this::refreshContents, 4);
        } else {
            this.toggleActivateTextField.setContentDelegate(null);
        }
    }

    protected boolean checkInactive() {
        return !activate;
    }

    protected boolean active(ElementHandler el) {
        return activate;
    }

    protected static boolean refreshHard = true;
    protected static String currentFilterInput = "";

    public void init() {
        // toggle any autoExecute off
        //        if(SlimefunTasks.isMultiBlockAutoExecute()){
        //            SlimefunTasks.handleMultiBlockAutoExecuteToggle(MinecraftClient.getInstance().currentScreen,
        // false);
        //            Debug.chat(Text.literal("[自动多方块] 已关闭自动执行!"));
        //        }
        toggleBookWidget = ExecutableWidget.instance(0, 0, 8, 8)
                .setElementHandler(SlotElement.instance(BOOK_ICON)
                        .withInputHandler(InputHandler.run(this::toggleActive))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.book.tooltips", ""))))
                .addToSub(this);
        // add delegates to
        // hovering have higher priority so it will trigger first whenever interact or renderHighlight
        this.hovering = new ContentDelegateWidget<>(36, 14, HOVER_DX, HOVER_DY).addToSub(this, 500);

        switchHard = ExecutableWidget.instance(12, 0, 18, 8)
                .setElementHandler(new ButtonElement(
                                (el) -> refreshHard ? REFRESH_HARD : REFRESH_SOFT, ButtonAction.run(() -> {
                                    refreshHard = !refreshHard;
                                    Tasks.scheduleDelayed(this::refreshContents, 5);
                                }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.refresh-rule.tooltips", ""))))
                .addToSub(this);
        multiblockExecuteOneWidget = ExecutableWidget.instance(30, 0, 18, 8)
                .setElementHandler(new ButtonElement(TextProvider.of(MULTIBLOCK_EXECUTE), ButtonAction.run(() -> {
                            SlimefunTasks.getMultiBlockHelper()
                                    .onMultiBlockExecute(MinecraftClient.getInstance().currentScreen, false, false);
                            Tasks.scheduleDelayed(this::refreshContents, 2);
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.multiblock-execute-one.tooltips",
                                "")))
                        .withActiveActionCondition((el) -> {
                            return MinecraftClient.getInstance().currentScreen instanceof TileInventory tile
                                    && !tile.isVirtual();
                        }))
                .addToSub(this);
        prevPage = ExecutableWidget.instance(52, 0, 8, 8)
                .setElementHandler(PageButtonElement.prev(this::getMaxPage, this::getPage, this::setPage)
                        .withPresentCondition(this::active))
                .addToSub(this);
        nextPage = ExecutableWidget.instance(DX - 60, 0, 8, 8)
                .setElementHandler(PageButtonElement.next(this::getMaxPage, this::getPage, this::setPage)
                        .withPresentCondition(this::active))
                .addToSub(this);
        titleWidget = ExecutableWidget.instance(60, 0, DX - 120, 8)
                .setElementHandler(new LabelElement(TITLE, Colors.WHITE)
                        .withInputHandler(InputHandler.run(() -> {
                            this.onlyShowRelated = !this.onlyShowRelated;
                            Tasks.scheduleDelayed(this::refreshContents, 5);
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.title.show-related.tooltips", "")))
                        .withPresentCondition(this::active))
                .addToSub(this);
        refresh = ExecutableWidget.instance(DX - 8, 0, 8, 8)
                .setElementHandler(SlotElement.instance(REFRESH_ICON)
                        .withInputHandler(InputHandler.run(this::refreshContents))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.refresh.tooltips", "")))
                        .withPresentCondition(this::active))
                .addToSub(this);
        multiblockExecuteWidget = ExecutableWidget.instance(DX - 48, 0, 18, 8)
                .setElementHandler(new ButtonElement(TextProvider.of(MULTIBLOCK_EXECUTE), ButtonAction.run(() -> {
                            SlimefunTasks.getMultiBlockHelper()
                                    .onMultiBlockExecute(
                                            MinecraftClient.getInstance().currentScreen,
                                            true,
                                            ScreenUtils.hasShiftDown());
                            Tasks.scheduleDelayed(this::refreshContents, 5);
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.multiblock-execute.tooltips", "")))
                        .withActiveActionCondition((el) -> {
                            return MinecraftClient.getInstance().currentScreen instanceof TileInventory tile
                                    && !tile.isVirtual();
                        }))
                .addToSub(this);
        multiblockAutoExecute = ExecutableWidget.instance(DX - 30, 0, 18, 8)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(MULTIBLOCK_AUTO), ((element, widget, mouseButton) -> {
                                    if (MinecraftClient.getInstance().currentScreen
                                            instanceof TileInventory handledScreen) {
                                        MultiBlockHelper multiBlockHelper = SlimefunTasks.getMultiBlockHelper();
                                        if (multiBlockHelper.isMultiBlockExecuting(handledScreen)) {
                                            widget.setAlpha(0.4f);
                                            multiBlockHelper.toggleMultiBlockAutoExecuteState(handledScreen, false);
                                        } else {
                                            widget.setAlpha(1.0f);
                                            multiBlockHelper.toggleMultiBlockAutoExecuteState(handledScreen, true);
                                        }
                                    }

                                    return true;
                                }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-dispensor-suggest-book-widget.multiblock-auto.tooltips", "")))
                        .withActiveActionCondition((el) -> {
                            return MinecraftClient.getInstance().currentScreen instanceof TileInventory tile
                                    && !tile.isVirtual();
                        }))
                .setAlpha(SlimefunTasks.getMultiBlockHelper().isMultiBlockExecuting(this.tile) ? 1.0F : 0.4f)
                .addToSub(this);

        for (int i = 0; i < maxElementInPage; ++i) {
            contents[i].addToSub(this);
        }

        textField = FilterService.createFilter(
                ValueAccessor.of(() -> currentFilterInput, (s) -> currentFilterInput = s),
                (v) -> {
                    if (refreshFilter()) {
                        resetPage();
                    }
                },
                TEXT_START_DX + 1,
                TEXT_START_DY,
                DX - 2 * TEXT_START_DX,
                DY - TEXT_START_DY);
        this.toggleActivateTextField = new ContentDelegateWidget<DrawableWidget>(0, 0, 0, 0)
                .setContentDelegate(activate ? this.textField : (DrawableWidget) null)
                .addToSub(this);
        refreshActiveState();
    }

    @Override
    public <T extends DrawableWidget> T addTo(Screen screen) {
        toggleActivateTextField.addTo(screen);
        return super.addTo(screen);
    }

    @Override
    public <T extends DrawableWidget> T addToSub(SubScreenWidget screen) {
        textField.addToSub(screen);
        return super.addToSub(screen);
    }

    public synchronized void calculateMatchingRecipes() {
        List<RecipeEntry> recipeEntries = new ArrayList<>();
        List<RecipeEntry> recipes;
        if (this.onlyShowRelated) {
            // impl here
            // todo: add a empty recipe to clear the slots in one click
            recipes = MinecraftClient.getInstance().player != null
                    ? SlimefunTasks.getInventoryRelativeRecipes(
                            MinecraftClient.getInstance().currentScreen, refreshHard)
                    : SlimefunTasks.getAllSlimefunRecipeEntry().toList();
        } else {
            recipes = SlimefunTasks.getAllSlimefunRecipeEntry().toList();
        }
        if (this.type != null && !this.type.isEmpty()) {
            recipes =
                    recipes.stream().filter(it -> this.type.contains(it.rid())).toList();
        }
        recipeEntries.add(RecipeEntry.EMPTY);
        recipeEntries.addAll(recipes);
        this.originItems = recipeEntries;
    }

    public synchronized boolean refreshFilter() {
        List<RecipeEntry> originItems = this.originItems;
        if (currentFilterInput == null || currentFilterInput.isEmpty()) {
            if (this.filterItems != originItems) {
                this.filterItems = originItems;
                return true;
            }
            return false;
        } else {
            // append Empty to every Filter
            this.filterItems = Streams.concat(
                            Stream.of(RecipeEntry.EMPTY),
                            originItems.stream().filter(t -> FilterService.RECIPE_FILTER.test(currentFilterInput, t)))
                    .toList();
            return true;
        }
    }

    public void resetPage() {
        List<RecipeEntry> filterItems = this.filterItems;
        if (filterItems != null && !filterItems.isEmpty()) {
            int size = filterItems.size();
            maxPage = (size - 1) / maxElementInPage + 1;
            this.page = MathHelper.clamp(this.page, 1, maxPage);
            int startIndex = (this.page - 1) * maxElementInPage;
            for (int i = startIndex; i < startIndex + maxElementInPage; ++i) {
                if (i < size) {
                    contents[i - startIndex].setContentDelegate(generateRecipeEntry(filterItems.get(i)));
                } else {
                    contents[i - startIndex].setContentDelegate(null);
                }
            }
        } else {
            maxPage = 1;
            this.page = 1;
            for (int i = 0; i < maxElementInPage; ++i) {
                contents[i].setContentDelegate(null);
            }
        }
    }

    public ExecutableWidget generateRecipeEntry(RecipeEntry recipeEntry) {
        return ExecutableWidget.instance(0, 0, 12, 12)
                .setElementHandler(SlotElement.instance(recipeEntry.output())
                        .withInputHandler(new InputHandler() {
                            @Override
                            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public boolean onAction(
                                    ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                                if (button == 0 || button == 1) {
                                    if (type == Type.MOUSE_CLICK
                                            && SlimefunDispensorSuggestBookWidget.this.callback != null) {

                                        if (ScreenUtils.hasShiftDown()) {
                                            int amount = button == 0 ? 64 : 0;
                                            openInputIntScreen(amount, (i) -> {
                                                SlimefunDispensorSuggestBookWidget.this.callback.accept(i, recipeEntry);
                                                Tasks.scheduleDelayed(
                                                        SlimefunDispensorSuggestBookWidget.this::refreshContents, 5);
                                            });
                                        } else {
                                            int amount = button == 0 ? 64 : 1;
                                            SlimefunDispensorSuggestBookWidget.this.callback.accept(
                                                    amount, recipeEntry);
                                            // refresh after callback modify the backpack content
                                            Tasks.scheduleDelayed(
                                                    SlimefunDispensorSuggestBookWidget.this::refreshContents, 5);
                                        }
                                        return true;
                                    }
                                }
                                if (button == 2) {
                                    if (type == Type.MOUSE_CLICK) {
                                        // init
                                        setHoveringRecipe(
                                                recipeEntry, element.getX() + mouseX, element.getY() + mouseY);
                                        // what can I say?
                                        // 最好加一个
                                        Tasks.scheduleDelayed(
                                                SlimefunDispensorSuggestBookWidget.this::refreshContents, 5);
                                        return true;
                                    }
                                }

                                return false;
                            }
                        })
                        .withPresentCondition(this::active));
    }

    protected static final int HOVER_DX = 96;
    protected static final int HOVER_DY = 42;
    // todo: fix resolve failure air item
    protected void setHoveringRecipe(RecipeEntry entry, double mouseX, double mouseY) {
        if (this.hovering != null) {
            DrawableWidget widget = SlimefunEntryListScreen.generateRecipeEntryContent(entry, -24, 0)
                    .setCancelCallback(() -> this.hovering.setContentDelegate(null));
            this.hovering.setContentDelegate(widget);
        }
    }

    protected void openInputIntScreen(int originValue, IntConsumer intCallback) {
        if (this.hovering != null) {
            AttrKeyValue<Integer> integerAttrKeyValue = AttrKeyValue.clampedInt(
                    "widget.gui.slimefun-dispensor-suggest-book-widget.input-count", originValue, 0, 64);
            DrawableWidget widget = IntFastInputWidget.instance(
                            integerAttrKeyValue,
                            (attr) -> {
                                intCallback.accept((int) attr.getOriginValue());
                                // cancel

                            },
                            0,
                            16,
                            96,
                            30,
                            64)
                    .setFinishRunning(() -> this.hovering.setContentDelegate(null));
            this.hovering.setContentDelegate(widget);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // when click, schedule a refresh
        // probably move item from-to inv
        if (this.onlyShowRelated) Tasks.scheduleDelayed(this::refreshContents, 5);
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else {
            // remove hovering cancel, add cancel buttons in hover instead
            //    this.hovering.setContentDelegate(null);
            return false;
        }
    }

    // useless: screen exit faster than me
    //    @Override
    //    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //        if( keyCode == 256 &&this.hovering.getDelegate() != null ){
    //            this.hovering.setContentDelegate( null);
    //            return true;
    //        }
    //        return super.keyPressed(keyCode, scanCode, modifiers);
    //    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // release any delegating hover

        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void refreshContents() {
        // calculateMatchingRecipes();
        CompletableFuture.runAsync(this::calculateMatchingRecipes)
                .thenRun(this::refreshFilter)
                .thenRun(this::resetPage);
        //        refreshFilter();
        //        resetPage();
    }
}
