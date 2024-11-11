package me.matl114.HotKeyUtils;

import org.lwjgl.glfw.GLFW;

public class InputState {
    int status=-1;
    public synchronized void setPressed(boolean pressed){
        status=pressed? GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE;
    }
    public synchronized boolean isPressed(){
        return status==GLFW.GLFW_PRESS;
    }
}
