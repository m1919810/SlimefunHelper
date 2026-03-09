package me.matl114.hacks;

import com.google.common.base.Preconditions;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.slimefun.SlimefunChoiceScreen;
import me.matl114.gui.slimefun.SlimefunEntryListScreen;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.slimefun.*;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.hacks.utils.recipes.RecipeIngredient;
import me.matl114.managers.Tasks;
import me.matl114.utils.*;
import me.matl114.utils.commands.*;
import me.matl114.versioned.api.VItem;
import me.matl114.versioned.api.VNbt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class SlimefunTasks {
    public static void init() {}

    // todo: 历史记录显示
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static String generateId(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "minecraft:air";
        }
        String optional = ItemStackUtils.getSfId(item);
        return optional != null
                ? optional
                : Registries.ITEM.getId(item.getItem()).toString();
    }

    public static String getIdOrNull(ItemStack item) {
        return InvTasks.getCustomItemDatabase().getItemIdOrNull(item);
    }

    public static String getSfIdOrNull(ItemStack item) {
        String sfid = ItemStackUtils.getSfId(item);
        return sfid == null ? getIdOrNull(item) : sfid;
    }

    public static ItemStack byId(String id) {
        return InvTasks.getCustomItemDatabase().getFromCodecId(id);
    }

    private static ItemStack getSupportVanillaIcon(String rid) {
        if (RecipeTasks.isVanillaRecipeType(rid)) {
            return RecipeTasks.getVanillaRecipeTypeIcon(rid);
        }
        return null;
    }

    public static ItemStack getRecipeTypeIcon(String rid) {
        ItemStack rt;
        if ((rt = getSupportVanillaIcon(rid)) == null) {
            rt = getSlimefunRecipeTypeIcon(rid);
        }
        return rt;
    }

    public static ItemStack getSlimefunRecipeTypeIcon(String rid) {
        return getRecipeDatabase()
                .getId2CraftType()
                .getOrDefault(rid, RecipeDatabase.CraftingType.EMPTY)
                .icon()
                .getAsItemStack();
    }

    public static Optional<RecipeDatabase.CraftingType> getOptionalCraftingType(RecipeDatabase.MultiBlockEntry entry) {
        return entry.id() == null
                ? Optional.empty()
                : getRecipeDatabase().getId2CraftType().values().stream()
                        .filter(ct -> entry.id()
                                .equals(ItemStackUtils.getSfId(ct.icon().getAsPrototype())))
                        .findFirst();
    }

    public static Collection<MultiBlockHelper.MultiBlockWithLocation> getOptionalMultiBlocks(
            World world, BlockPos dispensor) {
        Collection<MultiBlockHelper.MultiBlockWithLocation> ans = new HashSet<>();
        for (var multi : getRecipeDatabase().getMultiBlockRegistry().values()) {
            var op = multi.lookup().lookup(world, dispensor);
            if (op != null && !op.isEmpty()) {
                for (var ml : op) {
                    ans.add(new MultiBlockHelper.MultiBlockWithLocation(multi, ml));
                }
            }
        }
        return ans;
    }

    public static Collection<RecipeDatabase.MultiBlockEntry> getOptionalMultiBlockTypes(
            World world, BlockPos dispensor) {
        Collection<RecipeDatabase.MultiBlockEntry> ans = new HashSet<>();
        for (var multi : getRecipeDatabase().getMultiBlockRegistry().values()) {
            var op = multi.lookup().lookup(world, dispensor);
            if (op != null && !op.isEmpty()) {
                ans.add(multi);
            }
        }
        return ans;
    }

    public static ItemStack GUIDE_ICON;
    public static ItemStack RTYPE_ICON;
    public static ItemStack VTYPE_ICON;
    public static ItemStack SAVED_ICON;

    private static void initIcon() {
        ItemStack ICON;
        try {
            ICON = VItem.getInstance()
                    .fromNbt(
                            (NbtCompound)
                                    VNbt.getInstance()
                                            .readNbt(
                                                    "{Count:1b,id:\"minecraft:enchanted_book\",tag:{CustomModelData:2200001,PublicBukkitValues:{\"slimefun:slimefun_guide_mode\":\"SURVIVAL_MODE\"},display:{Lore:['{\"text\":\"\"}','{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"yellow\",\"text\":\"右键 \"},{\"italic\":false,\"color\":\"dark_gray\",\"text\":\"⇨ \"},{\"italic\":false,\"color\":\"gray\",\"text\":\"浏览物品\"}],\"text\":\"\"}','{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"yellow\",\"text\":\"Shift + 右键 \"},{\"italic\":false,\"color\":\"dark_gray\",\"text\":\"⇨ \"},{\"italic\":false,\"color\":\"gray\",\"text\":\"打开 设置 / 关于\"}],\"text\":\"\"}'],Name:'{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"green\",\"text\":\"Slimefun 指南 \"},{\"italic\":false,\"color\":\"gray\",\"text\":\"(箱子界面)\"}],\"text\":\"\"}'}}}"));
        } catch (Throwable e) {
            Debug.info("Icon deserialize failure");
            ICON = new ItemStack(Items.ENCHANTED_BOOK);
        }
        GUIDE_ICON = ICON;
        RTYPE_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);
        VTYPE_ICON = new ItemStack(Items.CRAFTING_TABLE);
        SAVED_ICON = new ItemStack(Items.CHAIN_COMMAND_BLOCK);
    }

    // vanilla typed screen
    // optimize vanilla type display

    // 加入了 switch功能 重写跳转方向
    // todo: 把这个gui界面挪到别的utils里
    public static void openOrSwitch(Screen sf) {
        ScreenAccess access = ScreenAccess.of(sf);
        if (mc.currentScreen instanceof SlimefunEntryListScreen<?> sf2) {
            // 当前正在预览配方;,如果要切换到其他配方,使用水平切换
            if (sf instanceof SlimefunEntryListScreen<?>) {
                // 同级之间水平切换
                access.switchFromCurrent();
            } else if (sf instanceof SlimefunChoiceScreen<?> choosing) {
                // 退出到上级,
                sf2.close();
                openOrSwitch(sf);
            } else {
                access.openFromCurrent();
            }
        } else if (mc.currentScreen instanceof SlimefunChoiceScreen<?> sf3) {
            if (sf instanceof SlimefunChoiceScreen<?>) {
                // 同级之间切换
                access.switchFromCurrent();
            } else {
                access.openFromCurrent();
            }
        } else {
            access.openFromCurrent();
        }
    }

    // guide icon

    public static Map<String, RecipeEntry> getAllRecipes() {
        // immutable
        return (Map) getRecipeDatabase().getId2Recipe();
    }

    public static Stream<RecipeEntry> getAllSlimefunRecipeEntry() {
        return getRecipeDatabase().getId2Recipe().values().stream().map(RecipeEntry.class::cast);
    }
    // todo: can not search from vanilla items
    public static List<RecipeEntry> getInventoryRelativeRecipes(Screen inventory, boolean hard) {
        if (!(inventory instanceof HandledScreen<?> handled)) return List.of();
        var handler = handled.getScreenHandler();
        var slots = handler.slots;
        Set<String> relatedIds = new HashSet<>();
        int size = slots.size();

        for (int i = 0; i < size; ++i) {
            ItemStack item = slots.get(i).getStack();
            if (item != null && !item.isEmpty()) {
                String optionalItemId = getSfIdOrNull(item);
                if (optionalItemId != null) {
                    relatedIds.add(optionalItemId);
                }
            }
        }

        List<RecipeEntry> results = new ArrayList<>();
        // hard, may be obfuscated when at via data or when jeg shit occurs, but it may not influence the final result
        loop:
        for (var iter : SlimefunTasks.getRecipeDatabase().getId2Recipe().values()) {
            ItemStack[] ingredients = iter.inputs();

            for (var ingre : ingredients) {
                if (!ingre.isEmpty()) {
                    String id = getSfIdOrNull(ingre);
                    if (id != null && relatedIds.contains(id)) {
                        // soft accept
                        if (!hard) {
                            results.add(iter);
                            break;
                        }
                    } else {
                        // 非空但id不在
                        if (hard) {
                            // 只有严格匹配才会直接跳过
                            continue loop;
                        }
                    }
                }
            }
            // ingre全部通过了id hard才接受
            if (hard) {
                results.add(iter);
            }
        }
        return results;
    }

    @ApiMethod
    public static InvTasks.SlotMatchingResult getItemStackMatchingSlot(
            ScreenHandler screen, ItemStack stack, boolean weakMatch, int... slots) {
        if (stack.isEmpty()) {
            return InvTasks.getEmptySlots(screen, slots);
        }
        if (!weakMatch) {
            return InvTasks.getItemStackMatchingSlot(screen, stack, slots);
        }
        var result = new InvTasks.SlotMatchingResult();
        ItemStack realStack = null;
        String sampleId = getSfIdOrNull(stack);
        var allSlots = screen.slots;
        for (int i : slots) {
            Slot slot = allSlots.get(i);
            if (slot != null
                    && slot.inventory instanceof PlayerInventory
                    && !slot.getStack().isEmpty()) {
                if (realStack != null) {
                    if (ItemStack.areItemsAndComponentsEqual(slot.getStack(), realStack)) {
                        // all match
                        result.addMatchingSlot(i, slot);
                    }
                } else {
                    // the first match itemStack will be the realStack template
                    if (Objects.equals(sampleId, getSfIdOrNull(slot.getStack()))) {
                        realStack = slot.getStack();
                        result.setItemSample(realStack);
                        result.addMatchingSlot(i, slot);
                    }
                }
            }
        }
        return result;
    }

    @ApiMethod
    public static void moveSlimefunRecipePatternToContainer(
            RecipeEntry entry, ScreenHandler screen, int amount, boolean removeOrigin, int... acceptSlots) {
        Preconditions.checkArgument(acceptSlots.length == 9);
        ItemStack[] ingredients = new ItemStack[9];
        RecipeIngredient[] ingre = entry.ingredient();
        Preconditions.checkArgument(ingre.length <= 9);
        // try clear all items first;
        //        var handler = screen.getScreenHandler();
        //        DefaultedList<Slot> allSlots = handler.slots;
        //        for (var i : acceptSlots){
        //            Slot slot = allSlots.get(i);
        //            if(!slot.getStack().isEmpty()){
        //                InvTasks.quickMoveSlot(handler, i, true);
        //            }
        //        }
        for (var re = 0; re < ingre.length; ++re) {

            RecipeIngredient var = ingre[re];
            if (var.isEmpty()) {
                ingredients[re] = ItemStack.EMPTY;
            } else {
                ingredients[re] = var.matchingStack()[0];
            }
        }
        for (var re = ingre.length; re < 9; ++re) {
            ingredients[re] = ItemStack.EMPTY;
        }
        int[] playerInv = InvTasks.getPlayerInventorySlots(screen).toIntArray();
        InvTasks.moveRecipePatternToContainer(
                screen,
                ingredients,
                acceptSlots,
                amount,
                removeOrigin,
                ((screen1, itemStack) -> getItemStackMatchingSlot(screen1, itemStack, true, playerInv)));
    }

    static {
        Tasks.scheduleDelayed(
                () -> {
                    // post init tasks
                    Debug.info("Running Slimefun Post Setup Tasks");
                    initIcon();
                },
                1);
    }

    public static class SlimefunCommands extends AbstractMainCommand {

        public TreeSubCommand main = mainBuilder().name("sf").build();

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("give")
                    .helper("<id> <amount:default 1> 获取粘液物品(以指令形式)")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("id")
                            .tabSupplier(() -> SlimefunTasks.getAllRecipes().keySet().stream())
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("amount")
                            .intValue(1)
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onGive)))
                    .complete();
        }

        public void onGive(ArgumentInputStream re) {
            String id = re.nextNonnull();
            if (SlimefunTasks.getAllRecipes().containsKey(id)) {
                RecipeEntry entry = SlimefunTasks.getAllRecipes().get(id);
                ItemStack itemStack = entry.output().copyWithCount(re.nextInt());
                String giveCommand = InvTasks.createGiveCommand(itemStack);
                ChatTasks.sayMessage(giveCommand, true);
            } else {
                Debug.chat("不存在的id: ", id);
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("view")
                    .helper("<id> 打开对应物品的配方展示页面")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("id")
                            .tabSupplier(() -> SlimefunTasks.getAllRecipes().keySet().stream())
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onView)))
                    .complete();
        }

        public void onView(ArgumentInputStream re) {
            String id = re.nextNonnull();
            if (SlimefunTasks.getAllRecipes().containsKey(id)) {
                RecipeEntry entry = SlimefunTasks.getAllRecipes().get(id);
                if (entry != null) {
                    getSlimefunGuide().openRecipeEntryMenu(entry);
                } else Debug.chat("未知错误!");
            } else {
                Debug.chat("不存在的id: ", id);
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("banlist")
                    .helper("查询服务器禁用物品列表")
                    .post(e -> e.executor(CommandContext.run(this::onBanlist)))
                    .complete();
        }

        public void onBanlist(ArgumentInputStream re) {
            String command = "/sf unbanitem ";
            ClientUtils.getServerCommandTabResult(command).thenAccept(s -> {
                Debug.chat("禁用粘液物品列表");
                Map<String, RecipeEntry> records = getAllRecipes();
                for (var i : s) {
                    RecipeEntry entry = records.get(i);
                    Text name = null;
                    if (entry != null) {
                        name = entry.output().getName();
                    }
                    if (name != null) {
                        Debug.chat(i, " (", name, ")");
                    } else {
                        Debug.chat(i);
                    }
                }
                Debug.chat("注:当前列表可能不全,如果服务器禁用物品过多");
            });
        }
    }

    static {
        ChatTasks.registerSubCommands("sf", SlimefunCommands::new);
    }

    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Slimfun");

    @Getter
    public static SlimefunExtra slimefunExtra;

    @Getter
    public static RecipeDatabase recipeDatabase;

    @Getter
    public static MultiBlockHelper multiBlockHelper;

    @Getter
    public static SlimefunGuide slimefunGuide;

    @Getter
    public static CopyId copyId;

    @Getter
    public static ShowIdTooltips showIdTooltips;

    private static void initModule(ModuleManager m) {
        slimefunExtra = new SlimefunExtra().register(m);
        recipeDatabase = new RecipeDatabase().register(m);
        multiBlockHelper = new MultiBlockHelper().register(m);
        slimefunGuide = new SlimefunGuide().register(m);
        copyId = new CopyId().register(m);
        showIdTooltips = new ShowIdTooltips().register(m);
    }

    static {
        moduleManager.registerFactories(SlimefunTasks::initModule);
        HackModules.registerModuleGroup(moduleManager);
    }
}
