package me.matl114.SlimefunUtils;


import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Debug {
    private static Logger logger= LoggerFactory.getLogger("SlimefunHelper");
    public static void info(String string){
        logger.info(string);
    }
    public static void chat(Text... string){
        if(MinecraftClient.getInstance().player!=null){
            MutableText text=Text.literal("");
            for (var tx:string){
                text.append(tx);
            }
            MinecraftClient.getInstance().player.sendMessage(text);
        }
    }
    public static void chat(Object... values){
        if(MinecraftClient.getInstance().player!=null){
            MutableText text=Text.literal("");
            boolean f=true;
            for (var tx:values){
                if(f){
                    f=false;
                }else {
                    text.append(Text.of(" "));
                }

                if(tx instanceof Text){
                    text.append(((Text)tx));
                }else {
                    text.append(Text.literal(tx.toString()));
                }
            }
            MinecraftClient.getInstance().player.sendMessage(text);
        }
    }
//    public static void chat(String... string){
//        if(MinecraftClient.getInstance().player!=null){
//            MinecraftClient.getInstance().player.sendMessage(Text.of(String.join(" ", string)));
//        }
//    }
    public static void info(Throwable throwable){
        throwable.printStackTrace();
    }
    public static void info(Object... objs){
        info(String.join(" ", Arrays.stream(objs).map(o->o==null?"null":o.toString()).toArray(String[]::new)));
    }
    public static void info(Object object){
        logger.info(object!=null? object.toString():"null");
    }
    public static void stackTrace(){
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for(StackTraceElement stackTraceElement : stackTraceElements) {
            Debug.info(stackTraceElement.toString());
        }
    }
}
