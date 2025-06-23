package me.matl114.utils.UtilClass;

import java.util.function.Predicate;

public class CancellableEntryPoint<W extends Object> extends EntryPoint<Predicate<W>,W>{
    @Override
    public boolean handleValue(W express, Object... arguments) {
        for (var re : this.handlers){
            if(!re.test(express)){
                return false;
            }
        }
        return true;
    }
}
