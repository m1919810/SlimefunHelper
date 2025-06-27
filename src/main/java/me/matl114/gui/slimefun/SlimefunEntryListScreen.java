package me.matl114.gui.slimefun;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.hackUtils.SlimefunTasks;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.Function;

public abstract class SlimefunEntryListScreen<T>  extends SlimefunPageScreen{
    protected int entryPerPage;

    List<T> recipeEntries;
    public SlimefunEntryListScreen(List<T> recipeEntries) {
        super(Text.literal("配方展示"));
        this.recipeEntries = recipeEntries;
    }
    private ContentDelegateWidget[] pageContent;
    private static final int LABEL_HEIGHT = 64;
    private static final int LABEL_WIDTH = 144;
    private static final int LABEL_MIN_DISTANCE = 4;
    protected void resetPage(){
        this.pageSwitcher.updateMaxPage(Math.max(1, 1+((recipeEntries.size() -1) / entryPerPage) ));
        int page = this.pageSwitcher.getPage();
        int sizeOfEntries = recipeEntries.size();
        int pageIndex = (page - 1) * entryPerPage;
        for (int i=0 ; i< entryPerPage; ++i){
            if(pageContent[i] == null){
                int startX = (this.backgroundWidth - LABEL_WIDTH)/2;
                pageContent[i] = new ContentDelegateWidget(this.x+ startX ,this.y + 32 + LABEL_MIN_DISTANCE + (LABEL_HEIGHT + LABEL_MIN_DISTANCE)* i, LABEL_HEIGHT, LABEL_HEIGHT).addTo(this);
            }
            int index = pageIndex + i;
            if(index >= sizeOfEntries){
                pageContent[i].setContentDelegate(null);
            }else {
                pageContent[i].setContentDelegate(generateEntryContentDelegate(this.recipeEntries.get(index)));
            }
        }
    }

    public abstract DrawableWidget generateEntryContentDelegate(T entry);

    @Override
    protected List<Text> provideTitleTooltips(DrawableWidget widget) {
        return SlimefunTasks.TOOLTIPS_ITEM_RULE;
    }

    protected void init(){
        super.init();
        int availableRenderSpace = this.backgroundHeight - LABEL_OCCUPIED - LABEL_MIN_DISTANCE;
        int maxinum_entry = Math.max(1, availableRenderSpace/ (64 + 4));
        this.entryPerPage = maxinum_entry;
        this.pageSwitcher.updatePage(Math.max(1, 1+((recipeEntries.size() -1) / maxinum_entry)));
        initPageButton();
        pageContent = new ContentDelegateWidget[this.entryPerPage];
        int startX = (this.backgroundWidth - LABEL_WIDTH)/2;
        for (int i=0 ;i< entryPerPage; ++i){
            pageContent[i] = new ContentDelegateWidget(this.x+ startX ,this.y + 32 + LABEL_MIN_DISTANCE + (LABEL_HEIGHT + LABEL_MIN_DISTANCE)* i, LABEL_HEIGHT, LABEL_HEIGHT).addTo(this);
        }
        resetPage();
        //calculate the maxPage;
    }

    public static SlimefunEntryListScreen<SlimefunTasks.RecipeEntry> recipeEntry(List<SlimefunTasks.RecipeEntry> list){
        return new SlimefunEntryListScreen<SlimefunTasks.RecipeEntry>(list) {
            @Override
            public DrawableWidget generateEntryContentDelegate(SlimefunTasks.RecipeEntry entry) {
                return generateRecipeEntryContentDelegate(entry);
            }
        };
    }

    public static <T> SlimefunEntryListScreen<T> mapToWidget(List<T> list, Function<T, DrawableWidget> factory){
        return new SlimefunEntryListScreen<T>(list) {
            @Override
            public DrawableWidget generateEntryContentDelegate(T entry) {
                return factory.apply(entry);
            }
        };
    }




    public static DrawableWidget generateRecipeEntryContentDelegate(SlimefunTasks.RecipeEntry entry){
        return new SlimefunRecipeWidget(0,0, entry,SlimefunTasks::handleClickItemStack, SlimefunTasks::handleClickRecipeTypeIcon);
    }
    public static DrawableWidget generateRecipeEntryContent(SlimefunTasks.RecipeEntry entry, int x, int y){
        return new SlimefunRecipeWidget(x,y, entry,SlimefunTasks::handleClickItemStack, SlimefunTasks::handleClickRecipeTypeIcon);
    }


}
