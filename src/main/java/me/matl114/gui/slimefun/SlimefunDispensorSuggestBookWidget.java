package me.matl114.gui.slimefun;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import lombok.Getter;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.single.IntFastInputWidget;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.modules.slimefun.MultiBlockHelper;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
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
    protected static final List<Text> BOOK_TOOLTIPS = List.of(
            Text.literal("点我切换粘液配方书是否开启"),
            Text.literal("配方书将显示粘液配方"),
            Text.literal("若发射器为已记录的多方块,"),
            Text.literal("则只会显示相应配方类型的配方"));
    protected static final List<Text> REFRESH_TOOLTIPS = List.of(
            Text.literal("点我刷新配方书"),
            Text.literal("配方书将根据玩家背包物品"),
            Text.literal("和玩家的搜索过滤器内容"),
            Text.literal("匹配配方,过滤器支持拼音搜索"));
    protected static boolean refreshHard = true;
    protected static final Text REFRESH_HARD = Text.literal("严格");
    protected static final Text REFRESH_SOFT = Text.literal("宽松");
    protected static final List<Text> REFRESH_RULE_TOOLTIPS = List.of(
            Text.literal("切换严格搜索和宽松搜索"),
            Text.literal("当使用\"根据玩家背包内容搜索配方\"时"),
            Text.literal("严格搜索需要你持有全部的原材料"),
            Text.literal("宽松搜索只需要你持有一种原材料"));
    protected static final Text TITLE = Text.literal("补全书");

    protected static final List<Text> TITLE_TOOLTIPS_SHOWALL = List.of(
            Text.literal("当前展示全部配方"),
            Text.literal("点击标题切换为根据背包物品展示"),
            Text.literal("左键放入1份,shift+左键放入64份"),
            Text.literal("右键预览配方"));
    protected static final List<Text> TITLE_TOOLTIPS_SHOWRE = List.of(
            Text.literal("当前展示和背包物品相关的配方"),
            Text.literal("点击标题切换为展示全部配方"),
            Text.literal("左键放入1份,shift+左键放入64份"),
            Text.literal("右键预览配方"));
    protected static final List<Text> MULTIBLOCK_TOOLTIPS_EXECUTE = List.of(
            Text.literal("仅当识别到发射器才可使用,自动搜索多方块结构"),
            Text.literal("点击合成多次(点击数)").formatted(Formatting.YELLOW),
            Text.literal("Shift点击合成多次(2*点击数)").formatted(Formatting.YELLOW),
            Text.literal("服务端普遍限速点击频率,为9/300ms"),
            Text.literal("点击数可以在配置文件中配置,和自动多方块连点功能相同"));
    protected static final List<Text> MULTIBLOCK_TOOLTIPS_EXECUTEONE = List.of(
            Text.literal("仅当识别到发射器才可使用,自动搜索多方块结构"),
            Text.literal("点击合成1次").formatted(Formatting.YELLOW),
            Text.literal("若搜索出多个多方块结构,则都会交互(这可能无法通过反作弊和发包限制)").formatted(Formatting.YELLOW));
    protected static final List<Text> MULTIBLOCK_TOOLTIPS_AUTO = List.of(
            Text.literal("仅当识别到发射器才可使用,自动搜索多方块结构,自动使用"),
            Text.literal("服务端普遍限速点击频率,为9/300ms"),
            Text.literal("点击数可以在配置文件中配置,和自动多方块连点功能相同"),
            Text.literal("若搜索出多个多方块结构,则都会交互(这可能无法通过反作弊和发包限制)").formatted(Formatting.YELLOW));

    protected static final Text MULTIBLOCK_EXECUTE = Text.literal("合成");
    protected static final Text MULTIBLOCK_AUTO = Text.literal("自动");

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
    protected TileInventoryScreen tile;

    public SlimefunDispensorSuggestBookWidget(
            TileInventoryScreen tile,
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
                        .withTooltips(TooltipHandler.of(BOOK_TOOLTIPS)))
                .addToSub(this);
        // add delegates to
        // hovering have higher priority so it will trigger first whenever interact or renderHighlight
        this.hovering = new ContentDelegateWidget<>(36, 14, HOVER_DX, HOVER_DY).addToSub(this, 500);

        switchHard = ExecutableWidget.instance(12, 0, 18, 8)
                .setElementHandler(
                        new ButtonElement((el) -> refreshHard ? REFRESH_HARD : REFRESH_SOFT, ButtonAction.run(() -> {
                                    refreshHard = !refreshHard;
                                    Tasks.scheduleDelayed(this::refreshContents, 5);
                                }))
                                .withTooltips(TooltipHandler.of(REFRESH_RULE_TOOLTIPS)))
                .addToSub(this);
        multiblockExecuteOneWidget = ExecutableWidget.instance(30, 0, 18, 8)
                .setElementHandler(new ButtonElement(TextProvider.of(MULTIBLOCK_EXECUTE), ButtonAction.run(() -> {
                            SlimefunTasks.getMultiBlockHelper()
                                    .onMultiBlockExecute(MinecraftClient.getInstance().currentScreen, false, false);
                            Tasks.scheduleDelayed(this::refreshContents, 2);
                        }))
                        .withTooltips(TooltipHandler.of(MULTIBLOCK_TOOLTIPS_EXECUTEONE))
                        .withActiveActionCondition((el) -> {
                            return MinecraftClient.getInstance().currentScreen instanceof TileInventoryScreen tile
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
                        .withTooltips(TooltipHandler.of(() -> {
                            return this.onlyShowRelated ? TITLE_TOOLTIPS_SHOWRE : TITLE_TOOLTIPS_SHOWALL;
                        }))
                        .withPresentCondition(this::active))
                .addToSub(this);
        refresh = ExecutableWidget.instance(DX - 8, 0, 8, 8)
                .setElementHandler(SlotElement.instance(REFRESH_ICON)
                        .withInputHandler(InputHandler.run(this::refreshContents))
                        .withTooltips(TooltipHandler.of(REFRESH_TOOLTIPS))
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
                        .withTooltips(TooltipHandler.of(MULTIBLOCK_TOOLTIPS_EXECUTE))
                        .withActiveActionCondition((el) -> {
                            return MinecraftClient.getInstance().currentScreen instanceof TileInventoryScreen tile
                                    && !tile.isVirtual();
                        }))
                .addToSub(this);
        multiblockAutoExecute = ExecutableWidget.instance(DX - 30, 0, 18, 8)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(MULTIBLOCK_AUTO), ((element, widget, mouseButton) -> {
                                    if (MinecraftClient.getInstance().currentScreen
                                            instanceof TileInventoryScreen handledScreen) {
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
                        .withTooltips(TooltipHandler.of(MULTIBLOCK_TOOLTIPS_AUTO))
                        .withActiveActionCondition((el) -> {
                            return MinecraftClient.getInstance().currentScreen instanceof TileInventoryScreen tile
                                    && !tile.isVirtual();
                        }))
                .setAlpha(SlimefunTasks.getMultiBlockHelper().isMultiBlockExecuting(this.tile) ? 1.0F : 0.4f)
                .addToSub(this);

        for (int i = 0; i < maxElementInPage; ++i) {
            contents[i].addToSub(this);
        }

        //        textField = McWidgetHelpers.createTextFieldEditBox( TEXT_START_DX + 1 , TEXT_START_DY , DX - 2*
        // TEXT_START_DX, DY - TEXT_START_DY,(t, r)->{
        //            FilterService.currentUserInput = r;
        //            if(refreshFilter()){
        //                resetPage();
        //            }
        //        }, FilterService.currentUserInput);
        textField = FilterService.createFilter(
                () -> {
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
        if (FilterService.currentUserInput == null || FilterService.currentUserInput.isEmpty()) {
            if (this.filterItems != originItems) {
                this.filterItems = originItems;
                return true;
            }
            return false;
        } else {
            // append Empty to every Filter
            this.filterItems = Streams.concat(
                            Stream.of(RecipeEntry.EMPTY),
                            originItems.stream()
                                    .filter(t -> FilterService.RECIPE_FILTER.test(FilterService.currentUserInput, t)))
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

    protected void setHoveringRecipe(RecipeEntry entry, double mouseX, double mouseY) {
        if (this.hovering != null) {
            DrawableWidget widget = SlimefunEntryListScreen.generateRecipeEntryContent(entry, -24, 0)
                    .setCancelCallback(() -> this.hovering.setContentDelegate(null));
            this.hovering.setContentDelegate(widget);
        }
    }

    protected void openInputIntScreen(int originValue, IntConsumer intCallback) {
        if (this.hovering != null) {
            AttrKeyValue<Integer> integerAttrKeyValue = AttrKeyValue.clampedInt("输入数量", originValue, 0, 64);
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
