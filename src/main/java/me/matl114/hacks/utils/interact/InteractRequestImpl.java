package me.matl114.hacks.utils.interact;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;

/** Adds item selection and a success callback around an interaction. */
public record InteractRequestImpl(
        Predicate<ItemStack> itemPredicate,
        @Nonnull InteractRequest request,
        Consumer<InteractionQueue> successCallback)
        implements InteractRequest {
    private static final Predicate<ItemStack> ANY_ITEM = itemStack -> true;
    private static final Consumer<InteractionQueue> NO_SUCCESS_CALLBACK = queue -> {};

    public InteractRequestImpl {
        itemPredicate = Objects.requireNonNullElse(itemPredicate, ANY_ITEM);
        request = Objects.requireNonNull(request, "request");
        successCallback = Objects.requireNonNullElse(successCallback, NO_SUCCESS_CALLBACK);
    }

    public InteractRequestImpl(InteractRequest task) {
        this(ANY_ITEM, task, NO_SUCCESS_CALLBACK);
    }

    public InteractRequestImpl(Predicate<ItemStack> itemPredicate, InteractRequest task) {
        this(itemPredicate, task, NO_SUCCESS_CALLBACK);
    }

    public InteractRequestImpl(InteractRequest task, Consumer<InteractionQueue> successCallback) {
        this(ANY_ITEM, task, successCallback);
    }

    @Override
    public TaskAction tickTask(InteractionQueue queue, boolean itemMatched) {
        return request.tickTask(queue, itemMatched);
    }

    @Override
    public void successCallback(InteractionQueue queue) {
        if (successCallback != null) {
            successCallback.accept(queue);
        }
    }

    @Override
    public Double itemScore(InteractionQueue queue, ItemStack itemStack) {
        return itemPredicate.test(itemStack) ? 1.0 : null;
    }
}
