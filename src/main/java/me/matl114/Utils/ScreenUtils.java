package me.matl114.Utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import net.minecraft.util.Pair;

public class ScreenUtils {
    public  static Pair<Integer,Integer> getMouseCoord(MinecraftClient client) {
        return getMouseCoord(client,client.mouse);
    }
    public static Pair<Integer,Integer> getMouseCoord(MinecraftClient client, Mouse mouse) {
        Window window = client.getWindow();
        int mouseX = (int) (mouse.getX() * (double) window.getScaledWidth() / (double) window.getWidth());
        int mouseY = (int) (mouse.getY() * (double) window.getScaledHeight() / (double) window.getHeight());
        return new Pair<>(mouseX, mouseY);
    }
}
