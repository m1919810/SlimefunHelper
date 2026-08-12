package me.matl114.hacks.modules.extra;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.text.Text;

public class IQBoost extends BaseModule {
    public IQBoost() {
        super("IQBoost");
        bindFlag(enable);
    }

    ModulePath path = makePath(Configs.MISC_CONFIG, "iq-boost");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    path.addHotkey(),
                    new MultiKeyBind(),
                    path.addEnable(),
                    () -> Text.literal(String.valueOf(this.value.get())))
            .build();

    public final IntRef value =
            builder(path.add("boost-value"), IntRef.TYPE).defaultValue(114514).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }
}
