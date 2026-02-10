package me.matl114.mixins.access;

import me.matl114.accessors.hacks.KeyBindAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin implements KeyBindAccess {
    @Shadow
    private InputUtil.Key boundKey;

    @Unique
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public void resetKeyState() {
        long handle = mc.getWindow().getHandle();
        int code = boundKey.getCode();
        if (boundKey.getCategory() == InputUtil.Type.MOUSE) setPressed(GLFW.glfwGetMouseButton(handle, code) == 1);
        else setPressed(InputUtil.isKeyPressed(handle, code));
    }

    @Shadow
    public abstract void setPressed(boolean b);
}
