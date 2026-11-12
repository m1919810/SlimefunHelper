package me.matl114.hacks.modules.inv;

import java.util.Set;
import lombok.Getter;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.accessors.access.MerchantScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.SlotElement;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.RecipeTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.gui.TradeInformationSubScreen;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Unique;

public class FastCraft extends BaseModule {
    public final ModulePath fastCraft = makePath(Configs.INV_CONFIG, "fast-craft");
    public static FastCraft INSTANCE;

    public FastCraft() {
        super("FastCraft");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final FlagRef enable =
            flagBuilder(fastCraft.add("enable-fastcraft-buttons")).build();

    public final FlagRef dropCraft = flagBuilder(fastCraft.add("drop-craft")).build();

    public final KeyBindRef craftHotkey = hotkey(fastCraft.add("craft-hotkey"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asNonInputHandler(this::craftCurrentScreen))
            .build();

    public final KeyBindRef tradeHotkey = hotkey(fastCraft.add("trade-hotkey"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asNonInputHandler(this::tradeCurrentScreen))
            .build();

    public final FlagRef autoSelect = flagBuilder(fastCraft.add("auto-select")).build();

    public final NBTRef<EntrySet<Item>> autoSelectTrades = builder(
                    fastCraft.add("auto-select-trades"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, Set.of()))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPostInitializeScreen().getChannel(HandledScreen.class), this::onCraftScreenInitialize);
        registerListener(Listener.getClickCraftingRecipe(), this::onRecipeClicked);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(SetTradeOffersS2CPacket.class),
                this::onTradeOffersUpdated);
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "drop-craft", dropCraft);
    }

    public void onCraftScreenInitialize(Event<Screen> event) {
        if (checkNull()) return;
        if (enable.get()) {
            if (event.context instanceof CraftingScreen craftingScreen) {
                addCraftingInventoryButton(craftingScreen);
            } else if (event.context instanceof InventoryScreen inventoryScreen) {
                if (!mc.interactionManager.getCurrentGameMode().isCreative()) {
                    addInventoryButton(inventoryScreen);
                }
            } else if (event.context instanceof MerchantScreen merchantScreen) {
                addMerchantInformation(merchantScreen);
            }
        }
    }

    public void onRecipeClicked(Event<NetworkRecipeId> event) {
        if (!isLock()) {
            lastCrafted = event.context();
        }
    }

    @Getter
    private NetworkRecipeId lastCrafted;

    private boolean craftCurrentScreen() {
        if (checkNull() || !enable.get()) return false;

        if (mc.currentScreen instanceof CraftingScreen craftingScreen) {
            placeLastCraftingRecipe(craftingScreen, true);
            return true;
        }
        if (mc.currentScreen instanceof InventoryScreen inventoryScreen
                && !mc.interactionManager.getCurrentGameMode().isCreative()) {
            placeLastCraftingRecipe(inventoryScreen, true);
            return true;
        }
        return false;
    }

    private boolean tradeCurrentScreen() {
        if (checkNull() || !enable.get()) return false;

        if (mc.currentScreen instanceof MerchantScreen merchantScreen) {
            craftSelectedTrade(merchantScreen);
            return true;
        }
        return false;
    }

    public void craftSelectedTrade(MerchantScreen screen) {
        if (checkNull()) return;
        var access = MerchantScreenAccess.of(screen);
        int index = access.getSelectedIndex();
        var offers = screen.getScreenHandler().getRecipes();
        if (index < 0 || index >= offers.size()) return;
        access.setSelectedIndex(index);
        TradeOffer offer = offers.get(index);
        ItemStack output = offer.getSellItem();
        if (!output.isEmpty()) {
            int maxCraft = (int) Math.ceil((float) output.getMaxCount() / (float) output.getCount());
            maxCraft = Math.min(maxCraft, offer.getMaxUses() - offer.getUses());
            craftAtSlotIndex(screen, output, maxCraft, 2);
        }
    }

    private void onTradeOffersUpdated(Event<SetTradeOffersS2CPacket> event) {
        if (checkNull()
                || !(mc.currentScreen instanceof MerchantScreen screen)
                || screen.getScreenHandler().syncId != event.context().getSyncId()) return;
        if (autoSelect.get()) {
            selectMatchingTrade(screen);
        }
    }

    private void selectMatchingTrade(MerchantScreen screen) {
        var items = autoSelectTrades.get();
        if (items.set().isEmpty()) return;
        var offers = screen.getScreenHandler().getRecipes();
        for (int index = 0; index < offers.size(); index++) {
            TradeOffer offer = offers.get(index);
            if (items.test(offer.getDisplayedFirstBuyItem().getItem())
                    || items.test(offer.getDisplayedSecondBuyItem().getItem())
                    || items.test(offer.getSellItem().getItem())) {
                MerchantScreenAccess.of(screen).selectIndexLocally(index);
                return;
            }
        }
    }

    public void placeLastCraftingRecipe(
            HandledScreen<? extends AbstractCraftingScreenHandler> craftingScreen, boolean doCraft) {
        // var recipeBook = craftingScreen.getRecipeBookWidget();
        NetworkRecipeId last = lastCrafted;
        if (last != null) {
            mc.interactionManager.clickRecipe(craftingScreen.getScreenHandler().syncId, last, true);
            if (doCraft) {
                int maxCraft = 64;
                for (Ingredient material : RecipeTasks.getIngredients(last)) {
                    for (ItemStack val :
                            RecipeTasks.streamIngredientOptions(material).toList()) {
                        maxCraft = Math.min(maxCraft, val.getMaxCount());
                    }
                }
                int slot = craftingScreen.getScreenHandler().getOutputSlot().getIndex();
                craftAtSlotIndex(craftingScreen, getDisplayItemStack(), maxCraft, slot);
            }
        } else {
            Debug.chat("Crafting History Is Empty");
        }
    }

    public void craftAtSlotIndex(HandledScreen<?> screen, ItemStack resultItem, int maxCraft, int slot) {
        // Debug.info("What's wrong?",doCraft);

        boolean dropCraft = this.dropCraft.get();
        // Debug.info("Drop craft?",dropCraft);
        var handler = screen.getScreenHandler();
        if (dropCraft) {
            // do not use this
            InvTasks.getClickExecutor().execute(() -> {
                if (mc.interactionManager == null || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                    return false;
                }
                InvTasks.clickSlotAsync(slot, 1, SlotActionType.THROW);
                return true;
            });
        } else {
            InvTasks.getClickExecutor().execute(() -> {
                if (mc.interactionManager == null || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                    return false;
                }
                InvTasks.clickSlotAsync(slot, 1, SlotActionType.QUICK_MOVE);
                return true;
            });
        }
    }

    @Getter
    boolean lock;

    private ItemStack getDisplayItemStack() {
        if (lastCrafted != null) {
            return RecipeTasks.getRecipeResult(lastCrafted);
        } else {
            return new ItemStack(Items.BARRIER);
        }
    }

    private ItemStack getLockItem() {
        if (lock) {
            return new ItemStack(Items.BARRIER);
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Unique
    private void addCraftingInventoryButton(CraftingScreen screen) {
        HandledScreenAccess access = HandledScreenAccess.of(screen);
        DynamicSubScreenWidget recipeSubScreen =
                new DynamicSubScreenWidget(ValueAccessor.ofIgnore(access::getScreenX), ValueAccessor.ofIgnore(0));

        ELEMENT_FACTORY
                .createExecuteButton(
                        "widget.fast-craft.craft",
                        ButtonAction.run(() -> placeLastCraftingRecipe(screen, ScreenUtils.hasShiftDown())),
                        120,
                        screen.height / 2 - 25,
                        24,
                        12)
                .addToSub(recipeSubScreen);

        ELEMENT_FACTORY
                .createToggleButton(
                        "widget.fast-craft.lock",
                        ValueAccessor.of(() -> lock, (bl) -> lock = (boolean) bl),
                        95,
                        screen.height / 2 - 25,
                        24,
                        12)
                .addToSub(recipeSubScreen);
        Runnable toggle = HotKeyUtils.wrapFlagAsToggle("fast-craft.drop-craft", dropCraft);
        ELEMENT_FACTORY
                .createToggleButton(
                        "widget.fast-craft.toggle-drop",
                        ValueAccessor.of(dropCraft::get, (bl) -> {
                            if (bl != dropCraft.get()) {
                                toggle.run();
                            }
                        }),
                        120,
                        screen.height / 2 - 72,
                        24,
                        12)
                .addToSub(recipeSubScreen);

        ELEMENT_FACTORY
                .createElement(
                        new SlotElement(this::getDisplayItemStack).setSlotFrame(false),
                        null,
                        null,
                        150,
                        access.getScreenY() + 56,
                        18,
                        18)
                .addToSub(recipeSubScreen);

        ELEMENT_FACTORY
                .createElement(
                        new SlotElement(this::getLockItem)
                                .setInSlot(false)
                                .setSlotFrame(false)
                                .withRenderCondition(v -> isLock()),
                        null,
                        null,
                        150 + 10,
                        access.getScreenY() + 56 + 10,
                        7,
                        7)
                .setRenderHandler(new SlotElement(this::getLockItem)
                        .setInSlot(false)
                        .setSlotFrame(false)
                        .withRenderCondition(v -> isLock()))
                .addToSub(recipeSubScreen);

        access.addDrawableChildTo(recipeSubScreen);
    }

    @Unique
    private void addInventoryButton(InventoryScreen screen) {
        HandledScreenAccess access = HandledScreenAccess.of(screen);
        DynamicSubScreenWidget recipeSubScreen =
                new DynamicSubScreenWidget(ValueAccessor.ofIgnore(access::getScreenX), ValueAccessor.ofIgnore(0));

        ELEMENT_FACTORY
                .createExecuteButton(
                        "widget.fast-craft.craft",
                        ButtonAction.run(() -> placeLastCraftingRecipe(screen, ScreenUtils.hasShiftDown())),
                        150,
                        screen.height / 2 - 38,
                        24,
                        12)
                .addToSub(recipeSubScreen);

        ELEMENT_FACTORY
                .createToggleButton(
                        "widget.fast-craft.lock",
                        ValueAccessor.of(() -> lock, (bl) -> lock = (boolean) bl),
                        150,
                        screen.height / 2 - 25,
                        24,
                        12)
                .addToSub(recipeSubScreen);
        Runnable toggle = HotKeyUtils.wrapFlagAsToggle("fast-craft.drop-craft", dropCraft);
        ELEMENT_FACTORY
                .createToggleButton(
                        "widget.fast-craft.toggle-drop",
                        ValueAccessor.of(dropCraft::get, (bl) -> {
                            if (bl != dropCraft.get()) {
                                toggle.run();
                            }
                        }),
                        150,
                        screen.height / 2 - 72,
                        24,
                        12)
                .addToSub(recipeSubScreen);

        ELEMENT_FACTORY
                .createElement(
                        new SlotElement(this::getDisplayItemStack)
                                .setSlotFrame(false)
                                .setInSlot(false),
                        null,
                        null,
                        132,
                        access.getScreenY() + 55,
                        18,
                        18)
                .addToSub(recipeSubScreen);

        ELEMENT_FACTORY
                .createElement(
                        new SlotElement(this::getLockItem)
                                .setInSlot(false)
                                .setSlotFrame(false)
                                .withRenderCondition(v -> isLock()),
                        null,
                        null,
                        132 + 10,
                        access.getScreenY() + 55 + 10,
                        7,
                        7)
                .setRenderHandler(new SlotElement(this::getLockItem)
                        .setInSlot(false)
                        .setSlotFrame(false)
                        .withRenderCondition(v -> isLock()))
                .addToSub(recipeSubScreen);

        access.addDrawableChildTo(recipeSubScreen);
    }

    private void addMerchantInformation(MerchantScreen merchantScreen) {
        var access = HandledScreenAccess.of(merchantScreen);
        access.addDrawableChildTo(new TradeInformationSubScreen(
                access.getScreenX(), access.getScreenY(), (MerchantScreen) merchantScreen));
    }
}
