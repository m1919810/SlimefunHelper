package me.matl114.hackUtils.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ModuleManager {
    public List<Consumer<ModuleManager>> registeringFunctions = new ArrayList<>();

    public List<BaseModule> registered = new ArrayList<>();

    public void registerFactories(Consumer<ModuleManager> function) {
        registeringFunctions.add(function);
        function.accept(this);
    }

    public void unregisterFactories(Predicate<Consumer<ModuleManager>> function) {
        registeringFunctions.removeIf(function);
    }

    public void registerModule(BaseModule module) {
        registered.add(module);
        module.onCreate();
    }

    public void unregisterModule(BaseModule module) {
        registered.remove(module);
        module.onRemove();
    }

    public void loadModules() {
        registeringFunctions.forEach(consumer -> consumer.accept(this));
    }

    public void unloadModules() {
        List<BaseModule> toRemove = new ArrayList<>(registered);
        registered.clear();
        toRemove.forEach(this::unregisterModule);
    }

    public void reloadModules(){
        unloadModules();
        loadModules();
    }
    //todo antikb
    
}
