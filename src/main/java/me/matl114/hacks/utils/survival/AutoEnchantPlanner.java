package me.matl114.hacks.utils.survival;

import java.util.*;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;

/** Plans only book/book and target/book operations; two tools are never merged. */
public final class AutoEnchantPlanner {
    private static final int MAX_SEARCH_BOOKS = 12;

    private final ItemStack desired;
    private final Map<String, Integer> desiredEnchantments;
    private final Map<String, Plan> memo = new HashMap<>();

    private AutoEnchantPlanner(ItemStack desired) {
        this.desired = desired.copy();
        this.desiredEnchantments = enchantments(ItemStackUtils.getItemEnchant(desired));
    }

    public static Plan plan(
            ItemStack target, List<ItemStack> books, ItemStack desired, int availableLevels, boolean creative) {
        if (target == null || target.isEmpty() || target.getCount() != 1 || desired == null || desired.isEmpty()) {
            return null;
        }
        ToolCategory targetCategory = ToolCategory.of(target);
        if (targetCategory == null || targetCategory != ToolCategory.of(desired)) {
            return null;
        }
        AutoEnchantPlanner planner = new AutoEnchantPlanner(desired);
        if (planner.matchesTarget(target)) {
            return Plan.empty();
        }
        List<ItemStack> relevant = books.stream()
                .filter(stack -> stack != null && stack.isOf(Items.ENCHANTED_BOOK))
                .filter(stack -> stack.getCount() == 1)
                .filter(stack -> planner.containsDesiredEnchantment(stack))
                .map(stack -> stack.copy())
                .limit(MAX_SEARCH_BOOKS)
                .toList();
        return planner.search(target.copy(), relevant, new HashSet<>(), availableLevels, creative);
    }

    private Plan search(
            ItemStack target, List<ItemStack> books, Set<String> active, int availableLevels, boolean creative) {
        if (matchesTarget(target)) {
            return Plan.empty();
        }
        String key = stateKey(target, books) + '#' + availableLevels;
        Plan cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        if (!active.add(key)) {
            return null;
        }
        Plan best = null;

        for (int index = 0; index < books.size(); index++) {
            Merge merge = merge(target, books.get(index), creative);
            if (merge != null && !sameStack(target, merge.output())) {
                List<ItemStack> remaining = remove(books, index);
                if (creative || merge.cost() < 40) {
                    int nextLevels = creative ? availableLevels : Math.max(0, availableLevels - merge.cost());
                    best = choose(
                            best,
                            merge,
                            search(merge.output(), remaining, active, nextLevels, creative),
                            availableLevels,
                            creative);
                }
            }
        }

        for (int left = 0; left < books.size(); left++) {
            for (int right = left + 1; right < books.size(); right++) {
                Merge merge = merge(books.get(left), books.get(right), creative);
                if (merge == null) {
                    continue;
                }
                List<ItemStack> remaining = remove(books, right, left);
                remaining = new ArrayList<>(remaining);
                remaining.add(merge.output());
                if (creative || merge.cost() < 40) {
                    int nextLevels = creative ? availableLevels : Math.max(0, availableLevels - merge.cost());
                    best = choose(
                            best,
                            merge,
                            search(target, remaining, active, nextLevels, creative),
                            availableLevels,
                            creative);
                }
            }
        }
        active.remove(key);
        if (best != null) {
            memo.put(key, best);
        }
        return best;
    }

    private Plan choose(Plan current, Merge merge, Plan tail, int availableLevels, boolean creative) {
        if (tail == null) {
            return current;
        }
        Plan candidate = tail.prepend(new Step(merge.first(), merge.second(), merge.cost()));
        if (current == null || better(candidate, current, availableLevels, creative)) {
            return candidate;
        }
        return current;
    }

    private boolean better(Plan candidate, Plan current, int availableLevels, boolean creative) {
        int candidateSteps = candidate.affordableSteps(availableLevels, creative);
        int currentSteps = current.affordableSteps(availableLevels, creative);
        boolean candidateComplete = candidateSteps == candidate.steps.size();
        boolean currentComplete = currentSteps == current.steps.size();
        if (candidateComplete != currentComplete) {
            return candidateComplete;
        }
        if (candidateComplete && candidate.totalCost() != current.totalCost()) {
            return candidate.totalCost() < current.totalCost();
        }
        if (candidateSteps != currentSteps) {
            return candidateSteps > currentSteps;
        }
        if (candidate.firstStep().cost() != current.firstStep().cost()) {
            return candidate.firstStep().cost() < current.firstStep().cost();
        }
        return candidate.totalCost() < current.totalCost();
    }

    private Merge merge(ItemStack first, ItemStack second, boolean creative) {
        if (first.isEmpty() || second.isEmpty() || first.getCount() != 1 || second.getCount() != 1) {
            return null;
        }
        boolean firstBook = first.isOf(Items.ENCHANTED_BOOK);
        boolean secondBook = second.isOf(Items.ENCHANTED_BOOK);
        if (!secondBook) {
            return null;
        }

        ItemEnchantmentsComponent firstEnchantments =
                firstBook ? ItemStackUtils.getStoredEnchantment(first) : ItemStackUtils.getItemEnchant(first);
        ItemEnchantmentsComponent secondEnchantments = ItemStackUtils.getStoredEnchantment(second);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(firstEnchantments);
        long cost = (long) repairCost(first) + repairCost(second);
        boolean applied = false;

        for (var entry : secondEnchantments.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            int incomingLevel = entry.getIntValue();
            int existingLevel = builder.getLevel(enchantment);
            boolean compatible = creative || firstBook || enchantment.value().isAcceptableItem(first);
            for (var existing : builder.getEnchantments()) {
                if (!existing.equals(enchantment) && !Enchantment.canBeCombined(enchantment, existing)) {
                    compatible = false;
                    cost++;
                }
            }
            if (!compatible) {
                continue;
            }
            int resultLevel =
                    existingLevel == incomingLevel ? incomingLevel + 1 : Math.max(existingLevel, incomingLevel);
            resultLevel = Math.min(resultLevel, enchantment.value().getMaxLevel());
            builder.set(enchantment, resultLevel);
            int enchantmentCost = Math.max(1, enchantment.value().getAnvilCost() / 2);
            cost += (long) enchantmentCost * resultLevel;
            applied = true;
        }
        if (!applied) {
            return null;
        }
        ItemStack output = first.copy();
        output.setCount(1);
        if (firstBook) {
            ItemStackUtils.setStoredEnchantment(output, builder.build());
        } else {
            ItemStackUtils.applyItemEnchant(output, builder.build());
        }
        output.set(DataComponentTypes.REPAIR_COST, nextRepairCost(first, second));
        return new Merge(first.copy(), second.copy(), output, (int) Math.min(Math.max(1, cost), Integer.MAX_VALUE));
    }

    private boolean matchesTarget(ItemStack target) {
        if (target.isEmpty() || ToolCategory.of(target) != ToolCategory.of(desired)) {
            return false;
        }
        Map<String, Integer> current = enchantments(ItemStackUtils.getItemEnchant(target));
        return desiredEnchantments.entrySet().stream()
                .allMatch(entry -> current.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private boolean containsDesiredEnchantment(ItemStack book) {
        if (desiredEnchantments.isEmpty()) {
            return false;
        }
        return enchantments(ItemStackUtils.getStoredEnchantment(book)).keySet().stream()
                .anyMatch(desiredEnchantments::containsKey);
    }

    private static Map<String, Integer> enchantments(ItemEnchantmentsComponent component) {
        Map<String, Integer> result = new HashMap<>();
        for (var entry : component.getEnchantmentEntries()) {
            result.put(ItemStackUtils.solveDynamic(entry.getKey()).toString(), entry.getIntValue());
        }
        return result;
    }

    private static int repairCost(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.REPAIR_COST, 0);
    }

    private static int nextRepairCost(ItemStack first, ItemStack second) {
        return (int) Math.min((long) Math.max(repairCost(first), repairCost(second)) * 2 + 1, Integer.MAX_VALUE);
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return ItemStack.areItemsAndComponentsEqual(first, second) && first.getCount() == second.getCount();
    }

    private static List<ItemStack> remove(List<ItemStack> source, int... indexes) {
        Set<Integer> removed = new HashSet<>();
        for (int index : indexes) {
            removed.add(index);
        }
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            if (!removed.contains(index)) {
                result.add(source.get(index));
            }
        }
        return result;
    }

    private static String stateKey(ItemStack target, List<ItemStack> books) {
        List<String> bookKeys =
                books.stream().map(AutoEnchantPlanner::stackKey).sorted().toList();
        return stackKey(target) + "|" + String.join(",", bookKeys);
    }

    private static String stackKey(ItemStack stack) {
        return stack.getItem() + ":" + stack.getComponents().toString();
    }

    public record Step(ItemStack first, ItemStack second, int cost) {}

    public static final class Plan {
        private final List<Step> steps;
        private final long totalCost;

        private Plan(List<Step> steps, long totalCost) {
            this.steps = steps;
            this.totalCost = totalCost;
        }

        static Plan empty() {
            return new Plan(List.of(), 0);
        }

        Plan prepend(Step step) {
            List<Step> result = new ArrayList<>();
            result.add(step);
            result.addAll(steps);
            return new Plan(List.copyOf(result), totalCost + step.cost());
        }

        public Step firstStep() {
            return steps.isEmpty() ? null : steps.get(0);
        }

        long totalCost() {
            return totalCost;
        }

        int affordableSteps(int availableLevels, boolean creative) {
            if (creative) {
                return steps.size();
            }
            int remaining = availableLevels;
            int count = 0;
            for (Step step : steps) {
                if (step.cost() > remaining) {
                    break;
                }
                remaining -= step.cost();
                count++;
            }
            return count;
        }
    }

    private record Merge(ItemStack first, ItemStack second, ItemStack output, int cost) {}
}
