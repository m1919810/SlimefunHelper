package me.matl114.gui.slimefun;

import lombok.Getter;
import me.matl114.access.TileInventoryScreen;
import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.hackUtils.SlimefunTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

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
        Text.literal("则只会显示相应配方类型的配方")
    );
    protected static final List<Text> REFRESH_TOOLTIPS = List.of(
        Text.literal("点我刷新配方书"),
        Text.literal("配方书将根据玩家背包物品"),
        Text.literal("和玩家的搜索过滤器内容"),
        Text.literal("匹配配方,过滤器支持拼音搜索")
    );
    protected static final Text TITLE = Text.literal("粘液配方补全书");

    protected static final List<Text> TITLE_TOOLTIPS_SHOWALL = List.of(
        Text.literal("当前展示全部配方"),
        Text.literal("点击标题切换为根据背包物品展示"),
        Text.literal("左键放入1份,shift+左键放入64份"),
        Text.literal("右键预览配方")
    );
    protected static final List<Text> TITLE_TOOLTIPS_SHOWRE = List.of(
        Text.literal("当前展示和背包物品相关的配方"),
        Text.literal("点击标题切换为展示全部配方"),
        Text.literal("左键放入1份,shift+左键放入64份"),
        Text.literal("右键预览配方")
    );
    protected static final List<Text> MULTIBLOCK_TOOLTIPS_EXECUTE = List.of(
        Text.literal("仅当识别到发射器才可使用,自动搜索多方块结构"),
        Text.literal("服务端普遍限速点击频率,为9/300ms"),
        Text.literal("点击数可以在配置文件中配置,和自动多方块连点功能相同")
    );
    protected static final List<Text> MULTIBLOCK_TOOLTIPS_AUTO =List.of(
        Text.literal("仅当识别到发射器才可使用,自动搜索多方块结构,自动使用"),
        Text.literal("服务端普遍限速点击频率,为9/300ms"),
        Text.literal("点击数可以在配置文件中配置,和自动多方块连点功能相同")
    );

    protected static final Text MULTIBLOCK_EXECUTE = Text.literal("合成");
    protected static final Text MULTIBLOCK_AUTO = Text.literal("自动");

    protected ExecutableWidget toggleBookWidget;
    protected ExecutableWidget prevPage;
    protected ExecutableWidget nextPage;
    protected ExecutableWidget titleWidget;
    protected ExecutableWidget multiblockExecuteWidget;
    protected ExecutableWidget multiblockAutoExecute;
    protected ExecutableWidget refresh;
    protected DrawableWidget textField;
    protected ContentDelegateWidget<DrawableWidget> toggleActivateTextField;
    protected ContentDelegateWidget<DrawableWidget> hoveringItem;

    protected List<SlimefunTasks.RecipeEntry> originItems;
    protected List<SlimefunTasks.RecipeEntry> filterItems;

    protected BiConsumer<Boolean, SlimefunTasks.RecipeEntry> callback;
    protected boolean onlyShowRelated = true;
    @Getter
    int page = 1;
    @Getter
    int maxPage = 1;
    protected static final int maxElementInPage = 2 * 4 * 5;
    protected static final ContentDelegateWidget<ExecutableWidget>[] contents = new ContentDelegateWidget[maxElementInPage];
    static{
        for (int i=0; i<maxElementInPage; ++i){
            if(i < maxElementInPage/2){
                int ix = i % 4;
                int iy = i/4;
                contents[i] = new ContentDelegateWidget<>( ix *12, START_DY + iy * 12, 12,12);
            }else {
                int it  = i - maxElementInPage / 2;
                int ix = it % 4;
                int iy = it/4;
                contents[i] = new ContentDelegateWidget<>( DX - 48 + ix *12, START_DY + iy * 12, 12,12);
            }
        }
    }
    public void setPage(int p){
        int oldPage = this.page;
        this.page = MathHelper.clamp(p,1,maxPage);
        if(this.page != oldPage){
            resetPage();
        }
    }
    protected static boolean activate;
    protected Collection<String> type;
    public SlimefunDispensorSuggestBookWidget(int x, int y, Collection<String> optionalType, BiConsumer<Boolean, SlimefunTasks.RecipeEntry> callback){
        super(x, y, DX, DY);
        this.callback = callback;
        this.type = optionalType;
        init();
    }
    protected void toggleActive(){
        activate = !activate;
        refreshActiveState();
    }
    protected void refreshActiveState(){
        if(activate){
            this.toggleActivateTextField.setContentDelegate(this.textField);
            refreshContents();
        }else {
            this.toggleActivateTextField.setContentDelegate(null);
        }
    }
    protected boolean checkInactive(){
        return !activate;
    }
    protected boolean active(ElementHandler el){
        return this.activate;
    }
    public void init(){
        toggleBookWidget = ExecutableWidget.instance(0,0,8,8)
            .setElementHandler(
                SlotElement.instance(BOOK_ICON)
                    .withMouseHandler(MouseHandler.run(this::toggleActive))
                    .withTooltips(TooltipHandler.of(BOOK_TOOLTIPS))
            )
            .addToSub(this)
        ;
        prevPage = ExecutableWidget.instance(12,0,8,8)
            .setElementHandler(
                PageButtonElement.prev(this::getMaxPage, this::getPage, this::setPage)
                    .withPresentCondition(this::active)
            )
            .addToSub(this);
        nextPage = ExecutableWidget.instance(DX - 60, 0, 8,8)
            .setElementHandler(
                PageButtonElement.next(this::getMaxPage, this::getPage, this::setPage)
                    .withPresentCondition(this::active)
            )
            .addToSub(this);
        titleWidget = ExecutableWidget.instance( 20, 0, DX - 80, 8)
            .setElementHandler(
                new LabelElement(TITLE, Colors.WHITE)
                    .withMouseHandler(MouseHandler.run(()->{
                        this.onlyShowRelated = !this.onlyShowRelated;
                        refreshContents();
                    }))
                    .withTooltips(TooltipHandler.of(()->{
                        return this.onlyShowRelated ? TITLE_TOOLTIPS_SHOWRE : TITLE_TOOLTIPS_SHOWALL;
                    }))
                    .withPresentCondition(this::active)
            )
            .addToSub(this)
        ;
        refresh = ExecutableWidget.instance(DX - 48, 0, 8, 8)
            .setElementHandler(
                SlotElement.instance(REFRESH_ICON)
                    .withMouseHandler(MouseHandler.run(this::refreshContents))
                    .withTooltips(TooltipHandler.of(REFRESH_TOOLTIPS))
                    .withPresentCondition(this::active)
            )
            .addToSub(this);
        multiblockExecuteWidget = ExecutableWidget.instance(DX - 36,0, 18, 8)
            .setElementHandler(
                new ButtonElement(TextProvider.of(MULTIBLOCK_EXECUTE), ButtonAction.run(SlimefunTasks::handleMultiBlockExecute))
                    .withTooltips(TooltipHandler.of(MULTIBLOCK_TOOLTIPS_EXECUTE))
                    .withActiveActionCondition((el)->{
                        return MinecraftClient.getInstance().currentScreen instanceof TileInventoryScreen tile && !tile.isVirtual();
                    })
            )
            .addToSub(this);
        multiblockAutoExecute = ExecutableWidget.instance(DX - 18,0, 18, 8)
            .setElementHandler(
                new ButtonElement(TextProvider.of(MULTIBLOCK_AUTO), ((element, widget, mouseButton) -> {
                    if(SlimefunTasks.isMultiBlockAutoExecute()){
                        widget.setAlpha(0.4f);
                        SlimefunTasks.handleMultiBlockAutoExecuteToggle(false);
                    }else {
                        widget.setAlpha(1.0f);
                        SlimefunTasks.handleMultiBlockAutoExecuteToggle(true);
                    }
                    return true;
                }))
                    .withTooltips(TooltipHandler.of(MULTIBLOCK_TOOLTIPS_AUTO))
                    .withActiveActionCondition((el)->{
                        return MinecraftClient.getInstance().currentScreen instanceof TileInventoryScreen tile && !tile.isVirtual();
                    })
            )
            .addToSub(this);

        //add delegates to
        this.hoveringItem = new ContentDelegateWidget<>(36,  14,HOVER_DX, HOVER_DY)
            .addToSub(this);
        for (int i =0 ;i< maxElementInPage; ++i){
            contents[i].addToSub(this);
        }
        //text Field directly add to screen! so charType and focus can work
        //text Field use absolute coord
        textField = McWidgetHelpers.createTextFieldEditBox(this.x+ TEXT_START_DX, this.y +TEXT_START_DY , DX - 2* TEXT_START_DX, DY - TEXT_START_DY,(t, r)->{
            FilterService.currentUserInput = r;
            if(refreshFilter()){
                resetPage();
            }
        }, FilterService.currentUserInput);
        this.toggleActivateTextField = new ContentDelegateWidget<DrawableWidget>(0,0,0,0 )
            .setContentDelegate(this.activate? this.textField :(DrawableWidget) null)
        ;
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
    public void calculateMatchingRecipes(){
        if(this.onlyShowRelated){
            //impl here
            this.originItems = MinecraftClient.getInstance().player != null? SlimefunTasks.getInventoryRelativeRecipes(MinecraftClient.getInstance().player.getInventory()): SlimefunTasks.getAllSlimefunRecipeEntry().toList();
        }else {
            this.originItems = SlimefunTasks.getAllSlimefunRecipeEntry().toList();
        }
        if(this.type != null && !this.type.isEmpty()){
            this.originItems = this.originItems.stream()
                .filter(it->this.type.contains(it.rid()))
                .toList();
        }


    }
    public boolean refreshFilter(){
        if(FilterService. currentUserInput == null || FilterService.currentUserInput.isEmpty()){
            if(this.filterItems != this.originItems){
                this.filterItems = this.originItems;
                return true;
            }
            return false;
        }else {
            this.filterItems = originItems.stream()
                .filter(t->FilterService.RECIPE_FILTER.test(FilterService. currentUserInput, t))
                .toList();
            return true;
        }
    }
    public void resetPage(){
        if(this.filterItems != null && !this.filterItems.isEmpty()){
            int size = filterItems.size();
            maxPage = (size -1)/maxElementInPage +1;
            this.page = MathHelper.clamp(this.page,1, maxPage);
            int startIndex = (this.page - 1)* maxElementInPage;
            for (int i= startIndex ; i< startIndex + maxElementInPage; ++i){
                if(i < size){
                    contents[i - startIndex].setContentDelegate(generateRecipeEntry(this.filterItems.get(i)));
                }else {
                    contents[i - startIndex].setContentDelegate(null);
                }
            }
        }else {
            maxPage = 1;
            this.page = 1;
            for (int i=0 ;i< maxElementInPage; ++i){
                contents[i].setContentDelegate(null);
            }
        }


    }
    public ExecutableWidget generateRecipeEntry(SlimefunTasks.RecipeEntry  recipeEntry){
        return ExecutableWidget.instance(0,0,12,12)
            .setElementHandler(
                SlotElement.instance(recipeEntry.output())
                    .withMouseHandler(
                        new MouseHandler() {
                              @Override
                              public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                                  throw new UnsupportedOperationException();
                              }

                              @Override
                              public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                                  if(button == 0){
                                      if (type == Type.MOUSE_CLICK && SlimefunDispensorSuggestBookWidget.this.callback != null) {
                                          SlimefunDispensorSuggestBookWidget.this.callback.accept(Screen.hasShiftDown(), recipeEntry);
                                          return true;
                                      }
                                  }else if(button == 1){
                                        if(type == Type.MOUSE_CLICK){
                                            //init

                                            setHoveringItem(recipeEntry, element.getX() + mouseX, element.getY()+ mouseY);
                                            return true;
                                        }
                                  }
                                  return false;
                              }
                          }
                        )
                    .withPresentCondition(this::active)
            )
            ;
    }
    protected static final int HOVER_DX = 96;
    protected static final int HOVER_DY = 42;
    protected void setHoveringItem(SlimefunTasks.RecipeEntry entry, double mouseX, double mouseY){
        if(this.hoveringItem != null){

            DrawableWidget widget = SlimefunEntryListScreen.generateRecipeEntryContent(entry, 0,0)
                .setTextureScale(0.66666f)
                .setExtraDepth(500);
            this.hoveringItem.setContentDelegate(widget);
        }

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if( super.mouseClicked(mouseX, mouseY, button)){
            return true;
        }else {
            this.hoveringItem.setContentDelegate(null);
            return false;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if( keyCode == 256 &&this.hoveringItem.getDelegate() != null ){
            this.hoveringItem.setContentDelegate( null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // release any delegating hover

        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void refreshContents(){
        calculateMatchingRecipes();
        refreshFilter();
        resetPage();
    }
}
