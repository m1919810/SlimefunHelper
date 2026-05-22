package me.matl114.hacks.api;

import me.matl114.gui.basic.TextProvider;
import me.matl114.managers.config.Config;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public class MetaDataModuleEntry extends ModuleEntry{
    Supplier<Text> metaData;
    public MetaDataModuleEntry(Config config, String[] path, String[] hotkeyPath, Supplier<Text> provider) {
        super(config, path, hotkeyPath);
        metaData = provider;
    }

    @Override
    public MutableText getMetaData() {
        return (MutableText) metaData.get();
    }
}
