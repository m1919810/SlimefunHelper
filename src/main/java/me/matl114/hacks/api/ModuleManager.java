package me.matl114.hacks.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ModuleManager extends AbstractGuiManager<BaseModule> {
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



    //todo antikb
    
}
