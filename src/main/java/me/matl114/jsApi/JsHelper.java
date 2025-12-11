package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;

import java.lang.reflect.Method;

@ApiMethod
public class JsHelper {
    public static <T> T unwrap(Object what, Class<T> type){
        if(type.isInstance(what)){
            return type.cast(what);
        }else {
            try{
                Method method = what.getClass().getDeclaredMethod("getRaw");
                method.setAccessible(true);
                return (T) method.invoke(what);
            }catch (Throwable e){
                return (T)what;
            }
        }
    }
}
