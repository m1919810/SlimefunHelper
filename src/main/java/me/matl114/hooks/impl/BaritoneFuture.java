package me.matl114.hooks.impl;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class BaritoneFuture {
    final List<Runnable> onCompleteFutures = new ArrayList<>();
    final List<Runnable> onCancelFutures = new ArrayList<>();

    public void onComplete() {
        onCompleteFutures.forEach(Runnable::run);
    }

    public void onCancel() {
        onCancelFutures.forEach(Runnable::run);
    }
}
