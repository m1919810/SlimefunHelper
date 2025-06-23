package me.matl114.gui.basic;

public interface ButtonAction{
    static ButtonAction run(Runnable task){
        return ((element, widget, mouseButton) -> {
            task.run();
            return true;
        });
    }
    static ButtonAction EMPTY = ((element, widget, mouseButton) -> true);
    static ButtonAction empty(){
        return EMPTY;
    }
    public boolean onClick(AbstractElement element, ExecutableWidget widget, int mouseButton);
}