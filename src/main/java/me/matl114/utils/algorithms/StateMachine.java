package me.matl114.utils.algorithms;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import me.matl114.utils.collections.IndexEntry;

public class StateMachine {
    @Getter
    int state;

    final int initState;
    boolean currentEnd = false;
    final StateAction[] actions;
    final StateUpdater updater;
    final List<IndexEntry<StateUpdateListener>> listeners = new ArrayList<>();

    public StateMachine(int initializeState, StateUpdater stateUpdater, StateAction... actions) {
        Preconditions.checkArgument(initializeState >= 0 && initializeState < actions.length);
        initState = initializeState;
        state = initializeState;
        this.updater = stateUpdater;
        this.actions = actions;
    }

    public void registerListener(int state, StateUpdateListener listener) {
        listeners.add(new IndexEntry<>(state, listener));
    }

    private void callUpdate(int from, int to) {

        if (from != to) {
            for (var entry : listeners) {
                if (entry.index() == from) {
                    entry.val().onUpdate(false);
                }
                if (entry.index() == to) {
                    entry.val().onUpdate(true);
                }
            }
        }
    }

    boolean stepping = false;

    public void setState(int state) {
        if (stepping) {
            throw new IllegalStateException("Set during state running");
        } else {
            setStateInternal(state);
        }
    }

    private void setStateInternal(int state) {
        int oldState = this.state;
        this.state = state;
        callUpdate(oldState, state);
    }

    public void markForEndState() {
        currentEnd = true;
    }

    public void step() {
        stepping = true;
        try {
            currentEnd = false;
            setStateInternal(updater.update(this, state));
            for (var i = 0; i < actions.length && !currentEnd; i++) {
                var action = actions[state];
                setStateInternal(action.step(this));
            }
        } finally {
            stepping = false;
        }
    }

    public static interface StateAction {
        public int step(StateMachine machine);
    }

    public static interface StateUpdater {
        public int update(StateMachine machine, int status);
    }

    public static interface StateUpdateListener {
        public void onUpdate(boolean isOn);
    }
}
