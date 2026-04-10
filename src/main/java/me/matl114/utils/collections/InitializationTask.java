package me.matl114.utils.collections;

public class InitializationTask {
    private InitializationTask() {}

    public static InitializationTask INSTANCE = new InitializationTask();

    public static InitializationTask of(Runnable runnable) {
        runnable.run();
        return INSTANCE;
    }
}
