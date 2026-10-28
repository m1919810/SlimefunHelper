package me.matl114.hacks.modules.survival;

import java.util.*;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.ItemStackTemplate;
import me.matl114.hacks.utils.survival.AutoEnchantPlanner;
import me.matl114.hacks.utils.survival.ToolCategory;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.RegistryUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class AutoEnchant extends BaseModule {
    private static final int MAX_PENDING_FAILURES = 3;
    private static final int MAX_MOVE_WAIT_TICKS = 20;
    private static final int MAX_OUTPUT_WAIT_TICKS = 20;

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "survival-inv-utils.auto-enchant");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final NBTRef<ItemStackTemplate> swordTemplate = builder(root.add("sword-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_SWORD))
            .build();

    public final NBTRef<ItemStackTemplate> axeTemplate = builder(root.add("axe-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_AXE))
            .build();

    public final NBTRef<ItemStackTemplate> maceTemplate = builder(root.add("mace-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.MACE))
            .build();

    public final NBTRef<ItemStackTemplate> helmetTemplate = builder(
                    root.add("helmet-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_HELMET))
            .build();

    public final NBTRef<ItemStackTemplate> chestplateTemplate = builder(
                    root.add("chestplate-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_CHESTPLATE))
            .build();

    public final NBTRef<ItemStackTemplate> leggingsTemplate = builder(
                    root.add("leggings-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_LEGGINGS))
            .build();

    public final NBTRef<ItemStackTemplate> bootsTemplate = builder(root.add("boots-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_BOOTS))
            .build();

    public final NBTRef<ItemStackTemplate> toolTemplate = builder(root.add("tool-template"), ItemStackTemplate.class)
            .defaultValue(new ItemStackTemplate(Items.NETHERITE_PICKAXE))
            .build();

    private ItemStack pendingFirst = ItemStack.EMPTY;
    private ItemStack pendingSecond = ItemStack.EMPTY;
    private int pendingFailures;
    private int pendingMoveSource = -1;
    private int pendingMoveRevision = -1;
    private int pendingMoveDestination = -1;
    private int pendingMoveWaitTicks;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private int pendingOutputRevision = -1;
    private int pendingOutputWaitTicks;

    public AutoEnchant() {
        super("AutoEnchant");
        bindFlag(enable);
    }

    @Override
    public void addCustomWidgets(
            Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank, ElementFactory factory) {
        super.addCustomWidgets(acceptor, dx, dy, dblank, factory);
        acceptor.accept(factory.createExecuteButton(
                () -> Text.translatableWithFallback("widget.auto-enchant.set-best-enchantments", "一键设置最佳附魔"),
                List::of,
                ButtonAction.run(this::setBestEnchantments),
                () -> mc.getNetworkHandler() != null,
                0,
                dblank,
                dx,
                dy));
    }

    private void setBestEnchantments() {
        if (mc.getNetworkHandler() == null) {
            return;
        }
        setTemplate(
                swordTemplate,
                Items.NETHERITE_SWORD,
                enchantment(Enchantments.SHARPNESS, 5),
                enchantment(Enchantments.UNBREAKING, 3),
                enchantment(Enchantments.MENDING, 1),
                enchantment(Enchantments.KNOCKBACK, 2),
                enchantment(Enchantments.FIRE_ASPECT, 2),
                enchantment(Enchantments.SWEEPING_EDGE, 3));
        setTemplate(
                axeTemplate,
                Items.NETHERITE_AXE,
                enchantment(Enchantments.SHARPNESS, 5),
                enchantment(Enchantments.EFFICIENCY, 5),
                enchantment(Enchantments.UNBREAKING, 3),
                enchantment(Enchantments.MENDING, 1));
        setTemplate(
                maceTemplate,
                Items.MACE,
                enchantment(Enchantments.DENSITY, 5),
                enchantment(Enchantments.WIND_BURST, 3),
                enchantment(Enchantments.UNBREAKING, 3),
                enchantment(Enchantments.MENDING, 1));
        setTemplate(
                helmetTemplate,
                Items.NETHERITE_HELMET,
                armorEnchantments(
                        enchantment(Enchantments.RESPIRATION, 3), enchantment(Enchantments.AQUA_AFFINITY, 1)));
        setTemplate(chestplateTemplate, Items.NETHERITE_CHESTPLATE, armorEnchantments());
        setTemplate(leggingsTemplate, Items.NETHERITE_LEGGINGS, armorEnchantments());
        setTemplate(
                bootsTemplate,
                Items.NETHERITE_BOOTS,
                armorEnchantments(
                        enchantment(Enchantments.FEATHER_FALLING, 4), enchantment(Enchantments.DEPTH_STRIDER, 3)));
        setTemplate(
                toolTemplate,
                Items.NETHERITE_PICKAXE,
                enchantment(Enchantments.EFFICIENCY, 5),
                enchantment(Enchantments.FORTUNE, 3),
                enchantment(Enchantments.UNBREAKING, 3),
                enchantment(Enchantments.MENDING, 1));
    }

    private static EnchantmentLevel[] armorEnchantments(EnchantmentLevel... additional) {
        EnchantmentLevel[] result = new EnchantmentLevel[additional.length + 3];
        result[0] = enchantment(Enchantments.PROTECTION, 4);
        result[1] = enchantment(Enchantments.UNBREAKING, 3);
        result[2] = enchantment(Enchantments.MENDING, 1);
        System.arraycopy(additional, 0, result, 3, additional.length);
        return result;
    }

    private static EnchantmentLevel enchantment(RegistryKey<Enchantment> enchantment, int level) {
        return new EnchantmentLevel(enchantment, level);
    }

    private static void setTemplate(NBTRef<ItemStackTemplate> ref, Item item, EnchantmentLevel... enchantments) {
        ItemStack stack = new ItemStack(item);
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (EnchantmentLevel enchantment : enchantments) {
            RegistryEntry<Enchantment> entry =
                    RegistryUtils.getRegistryEntry(ItemStackUtils.registry(), enchantment.key());
            if (entry != null) {
                builder.add(entry, enchantment.level());
            }
        }
        ItemStackUtils.applyItemEnchant(stack, builder.build());
        ref.set(new ItemStackTemplate(VItem.getInstance().toNbt(stack, ItemStackUtils.registry())));
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
    }

    private void onTick(Event<net.minecraft.client.network.ClientPlayerEntity> event) {
        if (!enable.get()
                || checkNull()
                || !(mc.currentScreen instanceof AnvilScreen)
                || !(mc.player.currentScreenHandler instanceof AnvilScreenHandler handler)) {
            resetPending();
            return;
        }
        List<Integer> inventorySlots = inventorySlotIndexes(handler);
        if (!pendingFirst.isEmpty() && !pendingSecond.isEmpty()) {
            handlePending(handler, inventorySlots);
            return;
        }
        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        if (!handler.getSlot(0).getStack().isEmpty()
                || !handler.getSlot(1).getStack().isEmpty()) {
            return;
        }
        List<ItemStack> books = new ArrayList<>();
        for (int slotIndex : inventorySlots) {
            ItemStack stack = handler.getSlot(slotIndex).getStack();
            if (stack.isOf(Items.ENCHANTED_BOOK) && stack.getCount() == 1) {
                books.add(stack.copy());
            }
        }
        PlanChoice choice = findPlan(inventorySlots, handler, books);
        if (choice == null || choice.plan().firstStep() == null) {
            return;
        }
        AutoEnchantPlanner.Step step = choice.plan().firstStep();
        if (step.first().getCount() != 1 || step.second().getCount() != 1) {
            return;
        }
        pendingFirst = step.first().copy();
        pendingSecond = step.second().copy();
        pendingFailures = 0;
        if (!moveMatchingStack(handler, pendingFirst, 0, inventorySlots)) {
            recordPendingFailure();
        }
    }

    private void handlePending(AnvilScreenHandler handler, List<Integer> inventorySlots) {
        if (!pendingOutput.isEmpty()) {
            ItemStack cursor = handler.getCursorStack();
            if (!cursor.isEmpty() && !sameStack(cursor, pendingOutput)) {
                resetPending();
                return;
            }
            if (handler.getRevision() == pendingOutputRevision) {
                if (++pendingOutputWaitTicks >= MAX_OUTPUT_WAIT_TICKS) {
                    resetPending();
                }
                return;
            }
            boolean outputTaken = sameStack(cursor, pendingOutput)
                    && handler.getSlot(0).getStack().isEmpty()
                    && handler.getSlot(1).getStack().isEmpty()
                    && handler.getSlot(2).getStack().isEmpty();
            if (outputTaken) {
                resetPending();
            } else {
                recordPendingFailure();
            }
            return;
        }

        if (pendingMoveDestination >= 0) {
            ItemStack expected = pendingMoveDestination == 0 ? pendingFirst : pendingSecond;
            if (!handler.getCursorStack().isEmpty()) {
                resetPending();
                return;
            }
            if (handler.getRevision() == pendingMoveRevision) {
                if (++pendingMoveWaitTicks >= MAX_MOVE_WAIT_TICKS) {
                    resetPending();
                }
                return;
            }
            boolean moveConfirmed =
                    sameStack(handler.getSlot(pendingMoveDestination).getStack(), expected)
                            && handler.getSlot(pendingMoveSource).getStack().isEmpty();
            clearPendingMove();
            if (moveConfirmed) {
                pendingFailures = 0;
            } else {
                recordPendingFailure();
                return;
            }
        }

        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        ItemStack first = handler.getSlot(0).getStack();
        ItemStack second = handler.getSlot(1).getStack();
        if ((!first.isEmpty() && !sameStack(first, pendingFirst))
                || (!second.isEmpty() && !sameStack(second, pendingSecond))) {
            resetPending();
            return;
        }
        if (first.isEmpty()) {
            if (!moveMatchingStack(handler, pendingFirst, 0, inventorySlots)) {
                recordPendingFailure();
            }
            return;
        }
        if (second.isEmpty()) {
            if (!moveMatchingStack(handler, pendingSecond, 1, inventorySlots)) {
                recordPendingFailure();
            }
            return;
        }
        if (!handler.getSlot(2).getStack().isEmpty()) {
            ItemStack output = handler.getSlot(2).getStack();
            if (output.getCount() == 1) {
                takeAffordableOutput(handler);
            } else {
                recordPendingFailure();
            }
        }
    }

    private void takeAffordableOutput(AnvilScreenHandler handler) {
        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        int cost = handler.getLevelCost();
        boolean creative = mc.interactionManager.getCurrentGameMode().isCreative();
        if (cost <= 0 || (!creative && cost >= 40)) {
            recordPendingFailure();
            return;
        }
        if (!creative && mc.player.experienceLevel < cost) {
            return;
        }
        pendingOutput = handler.getSlot(2).getStack().copy();
        pendingOutputRevision = handler.getRevision();
        pendingOutputWaitTicks = 0;
        mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.PICKUP, mc.player);
    }

    private void recordPendingFailure() {
        if (++pendingFailures >= MAX_PENDING_FAILURES) {
            resetPending();
        }
    }

    private void resetPending() {
        pendingFirst = ItemStack.EMPTY;
        pendingSecond = ItemStack.EMPTY;
        pendingFailures = 0;
        pendingMoveSource = -1;
        pendingMoveRevision = -1;
        pendingMoveDestination = -1;
        pendingMoveWaitTicks = 0;
        pendingOutput = ItemStack.EMPTY;
        pendingOutputRevision = -1;
        pendingOutputWaitTicks = 0;
    }

    private void clearPendingMove() {
        pendingMoveSource = -1;
        pendingMoveRevision = -1;
        pendingMoveDestination = -1;
        pendingMoveWaitTicks = 0;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return ItemStack.areItemsAndComponentsEqual(first, second) && first.getCount() == second.getCount();
    }

    private PlanChoice findPlan(List<Integer> inventorySlots, ScreenHandler handler, List<ItemStack> books) {
        for (NBTRef<ItemStackTemplate> ref : List.of(
                swordTemplate,
                axeTemplate,
                maceTemplate,
                helmetTemplate,
                chestplateTemplate,
                leggingsTemplate,
                bootsTemplate,
                toolTemplate)) {
            ItemStack desired = ref.get().itemStack();
            if (desired.isEmpty() || desired.isOf(Items.ENCHANTED_BOOK) || desired.isOf(Items.AIR)) {
                continue;
            }
            ToolCategory desiredCategory = ToolCategory.of(desired);
            if (desiredCategory == null) {
                continue;
            }
            for (int slotIndex : inventorySlots) {
                ItemStack target = handler.getSlot(slotIndex).getStack();
                if (!target.isEmpty()
                        && target.getCount() == 1
                        && !target.isOf(Items.ENCHANTED_BOOK)
                        && ToolCategory.of(target) == desiredCategory) {
                    AutoEnchantPlanner.Plan plan = AutoEnchantPlanner.plan(
                            target,
                            books,
                            desired,
                            mc.player.experienceLevel,
                            mc.interactionManager.getCurrentGameMode().isCreative());
                    if (plan != null && plan.firstStep() != null) {
                        return new PlanChoice(plan);
                    }
                }
            }
        }
        return null;
    }

    private List<Integer> inventorySlotIndexes(ScreenHandler handler) {
        List<Integer> result = new ArrayList<>();
        for (int inventoryIndex = 0; inventoryIndex < mc.player.getInventory().size(); inventoryIndex++) {
            handler.getSlotIndex(mc.player.getInventory(), inventoryIndex).ifPresent(result::add);
        }
        return result;
    }

    private boolean moveMatchingStack(
            ScreenHandler handler, ItemStack expected, int destination, List<Integer> inventorySlots) {
        if (!handler.getCursorStack().isEmpty()
                || !handler.getSlot(destination).getStack().isEmpty()) {
            return false;
        }
        for (int source : inventorySlots) {
            ItemStack stack = handler.getSlot(source).getStack();
            if (sameStack(stack, expected)) {
                pendingMoveSource = source;
                pendingMoveRevision = handler.getRevision();
                pendingMoveDestination = destination;
                pendingMoveWaitTicks = 0;
                mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private record PlanChoice(AutoEnchantPlanner.Plan plan) {}

    private record EnchantmentLevel(RegistryKey<Enchantment> key, int level) {}
}
