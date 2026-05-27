package me.matl114.hacks.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ModuleManager extends AbstractManager<BaseModule> {
    public abstract String getName();

    public List<Consumer<ModuleManager>> registeringFunctions = new ArrayList<>();

    public void registerFactories(Consumer<ModuleManager> function) {
        registeringFunctions.add(function);
        function.accept(this);
    }

    public void unregisterFactories(Predicate<Consumer<ModuleManager>> function) {
        registeringFunctions.removeIf(function);
    }

    public void registerModule(BaseModule module) {
        super.registerModule(module);
        module.onCreate();
    }

    public void unregisterModule(BaseModule module) {
        super.unregisterModule(module);
        module.onRemove();
    }

    public void loadModules() {
        registeringFunctions.forEach(consumer -> consumer.accept(this));
    }

    public BaseModule getModule(String name) {
        return registered.stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElse(null);
    }

    public List<BaseModule> getModules() {
        return Collections.unmodifiableList(registered);
    }
}
