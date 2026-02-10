package me.matl114.hacks.api;

import lombok.Getter;

@Getter
public class ModuleGroup extends ModuleManager {
    String name;

    public ModuleGroup(String name) {
        this.name = name;
    }
}
