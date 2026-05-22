package me.matl114.hacks.api;

import java.util.stream.Stream;

public interface ModuleListProvider {
    Stream<ModuleEntry> getModuleEntries();
    default Stream<ModuleEntry> getActiveModules() {
        return getModuleEntries().filter(ModuleEntry::getActiveState);
    }
}
