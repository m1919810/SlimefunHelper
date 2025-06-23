package me.matl114.gui.basic;

import lombok.Getter;

import java.util.function.UnaryOperator;

public class ExecutableWidget extends DrawableWidget  {
    @Getter
    protected MouseHandler handler;
    public static ExecutableWidget instance(int x, int y, int dx, int dy){
        return new ExecutableWidget(x,y, dx, dy);
    }
    public ExecutableWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }
    public <T extends ExecutableWidget> T setMouseHandler(MouseHandler handler){
        this.handler = handler;
        return (T)this;
    }

    public <T extends ExecutableWidget> T updateMouseHandler(UnaryOperator<MouseHandler> handlerUnaryOperator){
        this.handler = handlerUnaryOperator.apply(this.handler);
        return (T)this;
    }


    public <T extends ExecutableWidget> T setElementHandler(ElementHandler handler){
        this.handler = handler;
        setRenderHandler(handler);
        return (T)this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(handler != null && isMouseOver(mouseX, mouseY)){
            return handler.onAction(this, mouseX, mouseY, button, MouseHandler.Type.MOUSE_CLICK);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(handler != null && isMouseOver(mouseX, mouseY)){
            return handler.onAction(this, mouseX,mouseY, button, MouseHandler.Type.MOUSE_RELEASE);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if(handler != null ){
            //drag do not need MouseOver
            return this.handler.onAction(this, mouseX, mouseY, button, MouseHandler.Type.MOUSE_DRAG);
        }
        return false;
    }

}
