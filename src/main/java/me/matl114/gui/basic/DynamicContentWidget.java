package me.matl114.gui.basic;

import java.util.function.Supplier;
import lombok.Setter;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import org.jetbrains.annotations.Nullable;

public class DynamicContentWidget<W extends Element & Drawable & Selectable> extends ContentDelegateWidget<W> {
    @Setter
    Supplier<W> contentSupplier;

    ValueAccessor<Integer> xSupplier;
    ValueAccessor<Integer> ySupplier;

    public DynamicContentWidget(@Nullable Supplier<W> supplier, int x, @Nullable ValueAccessor<Integer> ySupplier) {
        this(supplier, null, ySupplier, x, 0);
    }

    public DynamicContentWidget(@Nullable Supplier<W> supplier, @Nullable ValueAccessor<Integer> xSupplier, int y) {
        this(supplier, xSupplier, null, 0, y);
    }

    public DynamicContentWidget(
            @Nullable Supplier<W> supplier,
            @Nullable ValueAccessor<Integer> xSupplier,
            @Nullable ValueAccessor<Integer> ySupplier) {
        this(supplier, xSupplier, ySupplier, 0, 0);
    }

    public DynamicContentWidget(@Nullable Supplier<W> supplier, int x, int y) {
        this(supplier, null, null, x, y);
    }

    public DynamicContentWidget(
            @Nullable Supplier<W> supplier,
            @Nullable ValueAccessor<Integer> xSupplier,
            @Nullable ValueAccessor<Integer> ySupplier,
            int x,
            int y) {
        super(x, y, 0, 0);
        this.contentSupplier = supplier != null ? supplier : super::getDelegate;
        this.xSupplier = xSupplier != null ? xSupplier : ValueAccessor.of(super::x, super::x);
        this.ySupplier = ySupplier != null ? ySupplier : ValueAccessor.of(super::y, super::y);
    }

    @Override
    public int x() {
        return xSupplier.getValue();
    }

    @Override
    public int y() {
        return ySupplier.getValue();
    }

    @Override
    public void x(int x) {
        xSupplier.setValue(x);
    }

    @Override
    public void y(int y) {
        ySupplier.setValue(y);
    }

    public W getDelegate() {
        return contentSupplier.get();
    }
}
