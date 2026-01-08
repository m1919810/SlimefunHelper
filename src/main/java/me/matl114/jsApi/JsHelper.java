package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@ApiMethod
public class JsHelper {
    public static <T> T unwrap(Object what, Class<T> type){
        if(type.isInstance(what)){
            return type.cast(what);
        }else {
            try{
                Method method = what.getClass().getMethod("getRaw");
                method.setAccessible(true);
                return (T) method.invoke(what);
            }catch (Throwable e){
                return (T)what;
            }
        }
    }

    public static void runOnMainThread(Runnable runnable){
        MinecraftClient.getInstance().execute(runnable);
    }


}
