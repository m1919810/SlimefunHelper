package me.matl114.utils.UtilClass;

import java.util.function.BiPredicate;

public class ArgumentCancellablePoint<W extends Object> extends EntryPoint<BiPredicate<W,Object[]>,W> {
    @Override
    public boolean handleValue(W express, Object... arguments) {
        for (var re : handlers){
            if(!re.test(express, arguments)){
                return false;
            }
        }
        return  true;
    }
}
