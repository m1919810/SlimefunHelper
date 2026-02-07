package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

@ApiMethod
public class InputHelper {
    static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Keyboard getKeyboard(){
        return mc.keyboard;
    }

    public static Class<?> GLFW = org.lwjgl.glfw.GLFW.class;

    public static void keyAction(int key, int action){
        keyAction(key, action, ScreenUtils.getCurrentModifiers());
    }
    public static void keyAction(int key, int action, int modifiers){
        keyAction(key, org.lwjgl.glfw.GLFW.glfwGetKeyScancode(key), action, modifiers);
    }
    /**
     * 处理GLFW键盘按键事件的方法。
     * @param key       按键的GLFW常量键值。表示具体的按键标识符，如{@code org.lwjgl.glfw.GLFW.GLFW_KEY_A}、
     *                  {@code org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE}等。这些常量定义了键盘上每个按键的唯一标识。
     * @param scancode  按键的物理扫描码。这是由键盘硬件生成的原始值，表示按键在键盘上的物理位置。
     *                  与操作系统和键盘布局无关，同一物理按键在不同键盘布局下具有相同的扫描码。
     *                  例如，QWERTY键盘的"A"键和AZERTY键盘的"Q"键（物理位置相同）具有相同的扫描码。
     * @param action    按键动作类型。指示按键是被按下、释放还是重复触发。
     *                  可能的取值：
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_PRESS}   (1): 按键被按下
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_RELEASE} (0): 按键被释放
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_REPEAT}  (2): 按键被按住并重复触发
     *
     * @param modifiers 修饰键的状态标志位。表示事件发生时哪些修饰键被同时按住。
     *                  这是一个按位组合的标志，可以通过位运算检查各个修饰键的状态。
     *                  常用修饰键常量：
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT}:     Shift键被按住
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL}:   Ctrl键被按住
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_MOD_ALT}:       Alt/Option键被按住
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER}:     Windows/Command键被按住
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_MOD_CAPS_LOCK}: 大写锁定开启
     *                  - {@code org.lwjgl.glfw.GLFW.GLFW_MOD_NUM_LOCK}:  数字锁定开启
     *                  示例：检查Ctrl+Shift组合
     *                  {@code if ((modifiers & (GLFW_MOD_CONTROL | GLFW_MOD_SHIFT)) != 0)}
     *
     * @threadSafety 内部会自动切换到Minecraft主线程执行。
     */
    public static void keyAction(int key, int scancode, int action, int modifiers){
        mc.execute(()->{
            mc.keyboard.onKey(mc.getWindow().getHandle(), action, new KeyInput( key, scancode, modifiers));
        });
    }

    public static void charAction(String str){
        charAction(str);
    }

    public static void charAction(int str){
        charAction(str, ScreenUtils.getCurrentModifiers());
    }



    public static void charAction(String chr, int modifiers){
        charAction((int)chr.charAt(0), modifiers);
    }

    public static void charAction(int codePoint, int modifiers){
        mc.execute(()->{
            Element element = mc.currentScreen;
            if (element != null && mc.getOverlay() == null) {
                if (Character.charCount(codePoint) == 1) {
                    ScreenUtils.wrapScreenError(() -> {
                        element.charTyped(new CharInput(codePoint, modifiers));
                    }, "charTyped event handler", element.getClass().getCanonicalName());
                } else {
                    char[] var6 = Character.toChars(codePoint);
                    int var7 = var6.length;

                    for(int var8 = 0; var8 < var7; ++var8) {
                        char c = var6[var8];
                        ScreenUtils.wrapScreenError(() -> {
                            element.charTyped(new CharInput(c, modifiers));
                        }, "charTyped event handler", element.getClass().getCanonicalName());
                    }
                }

            }
        });
    }


}
