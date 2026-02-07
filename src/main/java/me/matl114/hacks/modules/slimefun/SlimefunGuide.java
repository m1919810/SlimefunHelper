package me.matl114.hacks.modules.slimefun;

import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.basic.SlotElement;
import me.matl114.gui.presets.choices.QuestionScreen;
import me.matl114.gui.slimefun.SavedItemWidget;
import me.matl114.gui.slimefun.SlimefunChoiceScreen;
import me.matl114.gui.slimefun.SlimefunEntryListScreen;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.RecipeTasks;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.managers.TaskManagers;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static me.matl114.gui.FilterService.*;

public class SlimefunGuide extends BaseModule {
    //todo: support big recipe
    /// gui
    public SlimefunGuide() {

    }

    public static final String OPEN_GUIDE = "slime-guide";
    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + OPEN_GUIDE, this::openMainGuideMenu);
    }

    public void handleAutoEnable(){
        RecipeDatabase database = SlimefunTasks.getRecipeDatabase();
        database.enable.set(true);
        database.saveData.set(true);
        Debug.chat("配方自动记录功能已开启,请使用ctrl+G打开Slimefun settings设置具体参数");
        Debug.chat(Text.literal("注意: 在1.20.5以上的物品数据和1.20.4及以下不互通,如果你进入了via支持的服务器,请注意这一点!").formatted(Formatting.YELLOW));
    }
    private boolean reject = false;
    public void handleRejectEnable(){
        Debug.chat("您仍旧可以继续使用GUIDE功能,在这次启动中该弹窗将不再弹出");
        reject = true;
    }
    private static final Text QUESTION_NOT_ENABLE = Text.literal("您当前并未启用配方记录功能,无法体验完整版GUIDE功能,请问您该如何选择?");
    private final List<QuestionScreen.Solution> QUESTION_SOLUTIONS = List.of(
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,一键启用"), this::handleAutoEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,但不启用"), this::handleRejectEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,一键启用"), this::handleAutoEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,但不启用"), this::handleRejectEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,一键启用"), this::handleAutoEnable)
    );
    public boolean handleNotEnable(){
        //没有启用recipe或者没有启用
        RecipeDatabase database = SlimefunTasks.getRecipeDatabase();
        if((!database.enable.get()|| !database.saveData.get() )&& !reject){
            SlimefunTasks.openOrSwitch(new QuestionScreen(QUESTION_NOT_ENABLE, QUESTION_SOLUTIONS));
            return true;
        }
        return false;
    }
    //vanilla typed screen
    //optimize vanilla type display

    public void openRecipeEntryMenu(RecipeEntry recipe){
        if(handleNotEnable())return;
        SlimefunTasks.openOrSwitch( SlimefunEntryListScreen.recipeEntry(List.of(recipe)));
    }

    public void openCraftTypeMenu(RecipeDatabase.CraftingType type){
        if(handleNotEnable())return;
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(SlimefunTasks.getAllRecipes().values()
            .stream()
            .filter(i->Objects.equals(i.rid(), type.id()))
            .map(RecipeEntry.class::cast)
            .toList()
        ));
    }


    private static final Text TITLE_ALL_ITEM = Text.literal("全部记录物品");
    public static final List<Text> TOOLTIPS_ITEM_RULE =  List.of(
        Text.literal("左键查看当前物品合成表"),
        Text.literal("右键查看包含当前物品的合成表"),
        Text.literal("Shift点击的时候会同时显示原版物品配方")
    );
    private static final Text TITLE_ALL_TYPE = Text.literal("全部记录配方类型");
    private static final Text TITLE_ALL_VANILLA =Text.literal("全部原版配方");
    private static final Text TITLE_ALL_SAVED = Text.literal("全部保存物品");
    public static final List<Text> TOOLTIPS_SAVED_RULE =  List.of(
        Text.literal("左键获得一组该物品(仅限创造)"),
        Text.literal("shift左键拷贝/give指令"),
        Text.literal("右键打开物品编辑器")
    );

    public void openMainGuideMenu(){
        if(handleNotEnable())return;
        SlimefunTasks. openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_ITEM ,TOOLTIPS_ITEM_RULE, ()->SlimefunTasks.getRecipeDatabase().getId2Recipe().values()
                .stream()
                .toList(),
                (entry)-> new ExecutableWidget(0,0,16,16).setElementHandler(SlotElement.instance(entry.output().copyWithCount(1)).withInputHandler(InputHandler.isLeft(t->{
                    if(t){
                         openRecipeEntryMenu(entry);
                    }else {
                        onClickItemStack(entry.output(), false);
                    }
                }))), RecipeDatabase.SlimefunRecipeEntry::output
            ).setSearchFilter((BiPredicate) RECIPE_FILTER)
        );
    }

    public void openSaveItemMenu(){
        if(handleNotEnable())return;
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_SAVED ,TOOLTIPS_SAVED_RULE, ()-> InvTasks.getSaveItem().getSavedItemDataMap().keySet()
                .stream()
                .map(SlimefunTasks::byId)
                .toList(),
                (entry)-> new ExecutableWidget(0,0,16,16).setElementHandler(SlotElement.instance(entry.copyWithCount(1)).withInputHandler(InputHandler.isLeft(t->{
                    if(t){
                        if(ScreenUtils.hasShiftDown()){
                            InvTasks.copyGiveCommand(entry.copy());
                        }else {
                            if(mc.player != null && mc.player.isCreative()){
                                InvTasks.creativeAddItem(entry.copy(), 64);
                            }else {
                                Debug.chat(Text.literal("当前并不处于创造模式,无法获取保存物品!").formatted(Formatting.YELLOW));
                            }
                        }
                    }else {
                        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.mapToWidget(
                            List.of(entry)
                            , (item)->new SavedItemWidget(0,0,item, null)));
                        //ItemEditTasks.openEditScreen(entry, (it)->{});
                    }
                }))), Function.identity()
            ).setSearchFilter(ITEM_FILTER)
        );
    }
    public static BiPredicate<String, RecipeDatabase.CraftingType> RTYPE_FILTER = (str, i)->nameMatch(i.id(), str);


    //rtype icon
    public  void openCraftTypeMenu(){
        if(handleNotEnable())return;
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_TYPE, SlimefunTasks.getRecipeDatabase().getId2CraftType().values()
                .stream()
                .toList(),
                (ct)->new ExecutableWidget(0,0,16,16).setElementHandler(SlotElement.instance(ct.iconStack())
                    .withInputHandler(InputHandler.isLeft(t-> openCraftTypeMenu(ct)))
                ), RecipeDatabase.CraftingType::iconStack
            ).setSearchFilter(RTYPE_FILTER)
        );
    }

    public void openVanillaRecipesMenu(){
        if(handleNotEnable())return;
        // remake
        SlimefunTasks.openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_VANILLA, TOOLTIPS_ITEM_RULE,()-> RecipeTasks.getAllRecipe().values().stream().toList(),
                (rp)-> new ExecutableWidget(0,0,16,16)
                    .setElementHandler(
                        SlotElement.instance(rp.output().copyWithCount(1))
                            .withInputHandler(InputHandler.isLeft((r)->{
                                if(r){
                                    openRecipeEntryMenu((RecipeEntry) rp);
                                }else{
                                    onClickItemStack(rp.output(), false);
                                }
                            }))
                    )

                ,
                RecipeTasks.RecipeRecord::output
            ).setSearchFilter((BiPredicate<String, RecipeTasks.RecipeRecord>)(BiPredicate) RECIPE_FILTER)
        );
    }

    public void onClickRecipeType(String type, boolean isLeft){
        if(handleNotEnable())return;
        List<RecipeEntry> myEntry;

        if(RecipeTasks.isVanillaRecipeType(type)){
            Map<Identifier, RecipeTasks.RecipeRecord> myCache = RecipeTasks.getAllRecipe();
            myEntry = myCache.values()       // RecipeTasks.getRecipeByType(type1)
                .stream()
                .filter(i-> Objects.equals(i.rid(), type))
                .map(RecipeEntry.class::cast)
                //  .map(i->(RecipeEntry)myCache.get(i.id()))
                //  .filter(Objects::nonNull)
                .toList();
        }else {

            myEntry = SlimefunTasks.getAllRecipes().values()
                .stream()
                .filter(i->i.rid().equals(type))
                .toList();

        }
        if(myEntry.isEmpty())return;
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(myEntry));
    }

    public void onClickItemStack(ItemStack item, boolean isLeft){
        if(handleNotEnable())return;
        if(item.isEmpty()){
            return;
        }
        List<RecipeEntry> resultToDisplay = new ArrayList<>();
        //logic remake
        boolean shiftDown = ScreenUtils.hasShiftDown();
        if(isLeft){
            //搞到当前物品的配方表
            //显示每个输出和当前物品相同的配方表。使用sfid匹配sf物品，弱匹配 匹配其他物品
            //shift点击的时候以itemtype匹配
            String sfid = SlimefunTasks.generateId(item);
            for (var re: SlimefunTasks.getAllRecipes().values()){
                if(shiftDown){
                    if(Objects.equals(sfid, SlimefunTasks.generateId(re.output()))){
                        resultToDisplay.add(re);
                        continue;
                    }
                }else{
                    if(ItemStackUtils.matchItemWithout(re.output(), item, false, false, false)){
                        resultToDisplay.add(re);
                    }
                }

            }
            for (var re :RecipeTasks.getAllRecipe().values()){
                if(shiftDown){
                    if (re.output().isOf(item.getItem())){
                        resultToDisplay.add(re);
                        continue;
                    }
                }else{
                    if(ItemStackUtils.matchItemWithout(re.output(), item, false, false,false)){
                        resultToDisplay.add(re);
                    }
                }

            }
        }else {

            String generatedId = SlimefunTasks.generateId(item);
            search:
            for (var re: SlimefunTasks.getRecipeDatabase().getId2Recipe().values()){
                for (var ingre : re.inputs()){
                    if(shiftDown){
                        if(Objects.equals(generatedId, SlimefunTasks.generateId(ingre))){
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }else{
                        if(ItemStackUtils.matchItemWithout(item, ingre, false, false, false)){
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }


                }
            }
            search:
            for (var re :RecipeTasks.getAllRecipe().values()){
                for (var ingre : re.ingredient()){
                    if(shiftDown){
                        if(ingre.testItemType(item)){
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }else{
                        if(!item.isEmpty()){
                            for(var matchingStack : ingre.matchingStack()){
                                if(ItemStackUtils.matchItemWithout(matchingStack, item, false, false, false)){
                                    resultToDisplay.add(re);
                                    continue  search;
                                }
                            }
                        }
                    }
                }

            }

        }
        if(resultToDisplay.isEmpty()){
            return;
        }
        SlimefunTasks.openOrSwitch(SlimefunEntryListScreen.recipeEntry(resultToDisplay));
    }
}
