package me.matl114.hacks.api;

import lombok.Getter;
import me.matl114.managers.config.Config;
import net.minecraft.text.Text;

public class ModulePath {
    @Getter
    Config config;

    String[] splits;

    public ModulePath(Config config, String[] splits) {
        this.config = config;
        this.splits = splits;
    }

    public ModulePath add(String path) {
        String[] newSplits = new String[splits.length + 1];
        System.arraycopy(splits, 0, newSplits, 0, splits.length);
        newSplits[splits.length] = path;
        return new ModulePath(this.config, newSplits);
    }

    public ModulePath addEnable() {
        return add("enable");
    }

    public ModulePath addHotkey() {
        return add("hotkey");
    }

    public String[] toPath() {
        return splits;
    }

    public String asString() {
        return String.join(".", splits);
    }

    public Text toTranslationKey() {
        return Text.translatable(asString());
    }
}
