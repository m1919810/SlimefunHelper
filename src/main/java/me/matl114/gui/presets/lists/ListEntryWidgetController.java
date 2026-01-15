package me.matl114.gui.presets.lists;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ListEntryWidgetController {
    int size();
    int height();
    int width();

    boolean shiftUp(int index);
    boolean shiftDown(int index);
    boolean del(int index);
    boolean insert(int index);

    public <T extends Element & Drawable & Selectable> T getEntryWidget(int index);

    public static  <W,T extends Element & Drawable & Selectable> ListEntryWidgetController mutable(List<W> originData, Supplier<W> newData, Function<W, T> widgetFactory, int height, int width){
        return new ListEntryWidgetController() {
            final List<T> cachedWidget = new ArrayList<>();
            {
                for (var origini: originData){
                    cachedWidget.add(widgetFactory.apply( origini));
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
                if(index > 0 && index < size()){
                    W val1 = originData.get(index - 1);
                    W val2 = originData.get(index);
                    originData.set(index - 1, val2);
                    originData.set(index, val1);
                    T val3 = cachedWidget.get(index - 1);
                    T val4 = cachedWidget.get(index);
                    cachedWidget.set(index - 1, val4);
                    cachedWidget.set(index , val3);
                    return true;
                }
                return false;
            }

            @Override
            public boolean shiftDown(int index) {
                if(index >= 0 && index < size() - 1){
                    W val1 = originData.get(index +1);
                    W val2 = originData.get(index);
                    originData.set(index + 1, val2);
                    originData.set(index, val1);
                    T val3 = cachedWidget.get(index + 1);
                    T val4 = cachedWidget.get(index);
                    cachedWidget.set(index + 1, val4);
                    cachedWidget.set(index , val3);
                    return true;
                }
                return false;
            }

            @Override
            public boolean del(int index) {
                if(index >= 0 && index < size()){
                    originData.remove(index);
                    cachedWidget.remove(index);
                    return true;
                }
                return false;
            }

            @Override
            public boolean insert(int index) {
                if(index >= 0 && index < size()){
                    W newValue = newData.get();
                    originData.add(index, newValue);
                    cachedWidget.add(index, widgetFactory.apply(newValue));
                }else {
                    W newValue = newData.get();
                    originData.add(newValue);
                    cachedWidget.add(widgetFactory.apply( newValue));
                }
                return true;
            }

            @Override
            public <T extends Element & Drawable & Selectable> T getEntryWidget(int index) {
                return (T) cachedWidget.get(index);
            }
        };
    }
    public static  <W,T extends Element & Drawable & Selectable> ListEntryWidgetController immutable(List<W> originData, Function<W, T> widgetFactory, int height, int width){
        return new ListEntryWidgetController() {
            final List<T> cachedWidget = new ArrayList<>();
            {
                for (var origini: originData){
                    cachedWidget.add(widgetFactory.apply( origini));
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
            public <T extends Element & Drawable & Selectable> T getEntryWidget(int index) {
                return (T) cachedWidget.get(index);
            }
        };
    }
}
