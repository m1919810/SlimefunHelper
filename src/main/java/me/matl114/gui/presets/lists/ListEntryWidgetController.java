package me.matl114.gui.presets.lists;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;

public interface ListEntryWidgetController {
    int size();

    int height();

    int width();

    boolean shiftUp(int index);

    boolean shiftDown(int index);

    boolean del(int index);

    boolean insert(int index);

    boolean update(int index);

    boolean dirty();

    boolean clear();

    void resync();

    void markDirty(boolean mark);

    public <T extends Element & Drawable & Selectable> T getEntryWidget(int index);

    public static <W, T extends Element & Drawable & Selectable> ListEntryWidgetController mutable(
            List<W> originData, Supplier<W> newData, Function<W, T> widgetFactory, int height, int width) {
        return new ListEntryWidgetController() {
            boolean dirty = false;
            final List<T> cachedWidget = new ArrayList<>();

            {
                resync();
            }

            @Override
            public int size() {
                return originData.size();
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public boolean shiftUp(int index) {
                if (index > 0 && index < size()) {
                    W val1 = originData.get(index - 1);
                    W val2 = originData.get(index);
                    originData.set(index - 1, val2);
                    originData.set(index, val1);
                    T val3 = cachedWidget.get(index - 1);
                    T val4 = cachedWidget.get(index);
                    cachedWidget.set(index - 1, val4);
                    cachedWidget.set(index, val3);
                    dirty = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean shiftDown(int index) {
                if (index >= 0 && index < size() - 1) {
                    W val1 = originData.get(index + 1);
                    W val2 = originData.get(index);
                    originData.set(index + 1, val2);
                    originData.set(index, val1);
                    T val3 = cachedWidget.get(index + 1);
                    T val4 = cachedWidget.get(index);
                    cachedWidget.set(index + 1, val4);
                    cachedWidget.set(index, val3);
                    dirty = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean del(int index) {
                if (index >= 0 && index < size()) {
                    originData.remove(index);
                    cachedWidget.remove(index);
                    dirty = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean insert(int index) {
                if (index >= 0 && index < size()) {
                    W newValue = newData.get();
                    originData.add(index + 1, newValue);
                    cachedWidget.add(index + 1, widgetFactory.apply(newValue));
                } else {
                    W newValue = newData.get();
                    originData.add(newValue);
                    cachedWidget.add(widgetFactory.apply(newValue));
                }
                dirty = true;
                return true;
            }

            @Override
            public boolean update(int index) {
                if (index >= 0 && index < size()) {
                    W val1 = originData.get(index);
                    if (index < cachedWidget.size()) {
                        cachedWidget.set(index, widgetFactory.apply(val1));
                    } else {
                        for (var i = cachedWidget.size(); i < size(); i++) {
                            cachedWidget.add(widgetFactory.apply(originData.get(i)));
                        }
                    }
                    dirty = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean dirty() {
                return dirty;
            }

            @Override
            public boolean clear() {
                if (size() > 0) {
                    originData.clear();
                    cachedWidget.clear();
                    dirty = true;
                    return true;
                }
                return false;
            }

            @Override
            public void resync() {
                for (var origini : originData) {
                    cachedWidget.add(widgetFactory.apply(origini));
                }
                dirty = true;
            }

            @Override
            public void markDirty(boolean ma) {
                this.dirty = ma;
            }

            @Override
            public <T extends Element & Drawable & Selectable> T getEntryWidget(int index) {
                return (T) cachedWidget.get(index);
            }
        };
    }

    public static <W, T extends Element & Drawable & Selectable> ListEntryWidgetController immutable(
            List<W> originData, Function<W, T> widgetFactory, int height, int width) {
        return new ListEntryWidgetController() {
            final List<T> cachedWidget = new ArrayList<>();

            {
                for (var origini : originData) {
                    cachedWidget.add(widgetFactory.apply(origini));
                }
            }

            @Override
            public int size() {
                return originData.size();
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public boolean shiftUp(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean shiftDown(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean del(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean insert(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean update(int index) {
                if (index >= 0 && index < size()) {

                    cachedWidget.set(index, widgetFactory.apply(originData.get(index)));
                    return true;
                }
                return false;
            }

            @Override
            public boolean dirty() {
                return false;
            }

            @Override
            public boolean clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void resync() {}

            @Override
            public void markDirty(boolean mark) {}

            @Override
            public <T extends Element & Drawable & Selectable> T getEntryWidget(int index) {
                return (T) cachedWidget.get(index);
            }
        };
    }
}
