package me.matl114.SlimefunUtils;


import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Debug {
    private static Logger logger= LoggerFactory.getLogger("SlimefunHelper");
    public static void info(String string){
        logger.info(string);
    }
    public static void chat(String string){
        MinecraftClient.getInstance().player.sendMessage(Text.of(string));
    }
    public static void info(Throwable throwable){
        throwable.printStackTrace();
    }
    public static void info(Object... objs){
        info(String.join(" ", Arrays.stream(objs).map(Object::toString).toArray(String[]::new)));
    }
    public static void info(Object object){
        logger.info(object.toString());
    }
}
