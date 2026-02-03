package me.matl114.utils.impl.config;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface PropertyTracker<OWNER, PROP> {
    public void valueChange(OWNER owner, PROP prop);
    //for write, not internal update , will trigger updateValue, be careful of recursive call

    static PropertyTracker EMPTY = new PropertyTracker() {
        @Override
        public void valueChange(Object object, Object object2) {

        }

    };
    static <T,W> PropertyTracker<T,W> empty(){
        return EMPTY;
    }
    static <T,W> PropertyTracker<T,W> event(Consumer<W> ob){
        return new PropertyTracker<T, W>() {
            @Override
            public void valueChange(T t, W w) {
                ob.accept(w);
            }


        };
    }
    static <T,W> PropertyTracker<T,W> event(BiConsumer<T,W> ob){
        return new PropertyTracker<T, W>() {
            @Override
            public void valueChange(T t, W w) {
                ob.accept(t,w);
            }


        };
    }


}
