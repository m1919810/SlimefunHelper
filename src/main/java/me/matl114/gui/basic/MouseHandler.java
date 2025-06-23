package me.matl114.gui.basic;

import lombok.AllArgsConstructor;
import net.minecraft.client.Mouse;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface MouseHandler {
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button);

    default boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type){
        if(type == Type.MOUSE_RELEASE){
            return false;
        }
        return onClick(element, mouseX, mouseY, button);
    }


    default MouseHandler withClickCondition(Predicate<MouseHandler> condition){
        MouseHandler ob = this;
        return new MouseHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                return  condition.test(ob) && ob.onAction(element, mouseX, mouseY, button, type);
            }
        };
    }

    static MouseHandler ofButton(Predicate<Boolean> isLeft){
        return ((element, mouseX, mouseY, button) -> {
            if(button == 0){
                return isLeft.test(true);
            }else if(button == 1){
                return isLeft.test(false);
            }else return false;
        });
    }

    static MouseHandler isLeft(Consumer<Boolean> isLeft){
        return ((element, mouseX, mouseY, button) -> {
            if(button == 0){
                isLeft.accept(true);return true;
            }else if(button == 1){
                isLeft.accept(false);return true;
            }else return false;
        });
    }
    @AllArgsConstructor
    static class TypedMouseHandler implements MouseHandler {
        MouseHandler handler;
        Type type;
        @Override
        public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
            throw new IllegalStateException();
        }

        @Override
        public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
            if(type == this.type){
                return handler.onClick(element, mouseX, mouseY, button);
            }
            return false;
        }
    }
    static MouseHandler clickRun(Runnable task){
        return new TypedMouseHandler(MouseHandler.run(task), Type.MOUSE_CLICK);
    }
    static MouseHandler run(Runnable task){
        return (((element, mouseX, mouseY, button) -> {
            task.run();
            return true;
        }));
    }
    static MouseHandler result(BooleanSupplier task){
        return ((element, mouseX, mouseY, button) -> task.getAsBoolean());
    }
    static MouseHandler dragRun(Runnable task){
        return new TypedMouseHandler(MouseHandler.run(task), Type.MOUSE_DRAG);
    }

    public static enum Type {
        MOUSE_CLICK,
        MOUSE_RELEASE,
        MOUSE_DRAG
        ;
    }
}
