package me.matl114.utils;

public class RuntimeAbort extends RuntimeException {
    public RuntimeAbort() {
        super();
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
