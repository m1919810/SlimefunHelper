package me.matl114.utils.UtilClass;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class ListenerPoint<W> extends EntryPoint<W>{
    @Override
    public boolean handleValue(W express, Object... arguments) {
        var iter = this.handlers.iterator();
        while(iter.hasNext()) {
            var entry = iter.next();
            if(!entry.test(express)){
                iter.remove();
            }
        }
        return true;
    }

}
