package me.matl114.gui.basic;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;

public interface InputHandler {
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button);

    default boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
        if (type != Type.MOUSE_CLICK) {
            return false;
        }
        return onClick(element, mouseX, mouseY, button);
    }

    default boolean onScroll(
            ExecutableWidget widget, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    default boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
        return false;
    }

    default boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
        return false;
    }

    default InputHandler withInputCondition(Predicate<InputHandler> condition) {
        InputHandler ob = this;
        return new InputHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                return condition.test(ob) && ob.onAction(element, mouseX, mouseY, button, type);
            }

            public boolean onScroll(
                    ExecutableWidget widget,
                    double mouseX,
                    double mouseY,
                    double horizontalAmount,
                    double verticalAmount) {
                return condition.test(ob) && ob.onScroll(widget, mouseX, mouseY, horizontalAmount, verticalAmount);
            }

            public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
                return condition.test(ob) && ob.onKey(widget, keyCode, scanCode, modifiers, isPress);
            }

            public boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
                return condition.test(ob) && ob.onTyped(widget, chr, modifiers);
            }
        };
    }

    static InputHandler ofButton(Predicate<Boolean> isLeft) {
        return ((element, mouseX, mouseY, button) -> {
            if (button == 0) {
                return isLeft.test(true);
            } else if (button == 1) {
                return isLeft.test(false);
            } else return false;
        });
    }

    static InputHandler isLeft(Consumer<Boolean> isLeft) {
        return ((element, mouseX, mouseY, button) -> {
            if (button == 0) {
                isLeft.accept(true);
                return true;
            } else if (button == 1) {
                isLeft.accept(false);
                return true;
            } else return false;
        });
    }

    @AllArgsConstructor
    static class TypedMouseHandler implements InputHandler {
        InputHandler handler;
        Type type;

        @Override
        public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
            throw new IllegalStateException();
        }

        @Override
        public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
            if (type == this.type) {
                return handler.onClick(element, mouseX, mouseY, button);
            }
            return false;
        }
    }

    static interface KeyboardHandler extends InputHandler {
        @Override
        boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress);

        @Override
        default boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        default boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
            return false;
        }

        default boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
            return false;
        }
    }

    static interface KeyboardPressHandler extends KeyboardHandler {
        public boolean onPress(ExecutableWidget el, int keycode);

        default boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
            return isPress && onPress(widget, keyCode);
        }
    }

    static interface ScrollerHandler extends InputHandler {
        public boolean scroll(ExecutableWidget widget, double amount);

        default boolean onScroll(
                ExecutableWidget widget, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            return widget.isMouseOver(mouseX, mouseY) && scroll(widget, verticalAmount);
        }

        @Override
        default boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        default boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
            return false;
        }
    }

    static interface AdvancedInputHandler extends InputHandler {
        default boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
            throw new IllegalStateException();
        }

        public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type);
    }

    static InputHandler keyboard(KeyboardHandler handler) {
        return handler;
    }

    static InputHandler keyPress(KeyboardPressHandler handler) {
        return handler;
    }

    static InputHandler scroller(ScrollerHandler scrollerHandler) {
        return scrollerHandler;
    }

    static InputHandler advanced(AdvancedInputHandler handler) {
        return handler;
    }

    static InputHandler clickRun(Runnable task) {
        return new TypedMouseHandler(InputHandler.run(task), Type.MOUSE_CLICK);
    }

    static InputHandler run(Runnable task) {
        return (((element, mouseX, mouseY, button) -> {
            task.run();
            return true;
        }));
    }

    static InputHandler result(BooleanSupplier task) {
        return ((element, mouseX, mouseY, button) -> task.getAsBoolean());
    }

    static InputHandler dragRun(Runnable task) {
        return new TypedMouseHandler(InputHandler.run(task), Type.MOUSE_DRAG);
    }

    public static enum Type {
        MOUSE_CLICK,
        MOUSE_RELEASE,
        MOUSE_START_DRAG,
        MOUSE_DRAG,
        MOUSE_RELEASE_DRAG;
    }
}
