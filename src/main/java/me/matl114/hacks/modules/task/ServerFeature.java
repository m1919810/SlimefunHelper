package me.matl114.hacks.modules.task;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;

public class ServerFeature extends BaseModule {
    public static ServerFeature INSTANCE;

    public ServerFeature() {
        super("ServerFeature");
        INSTANCE = this;
    }

    public final ModulePath root = makePath(Configs.MISC_CONFIG, "server-features");

    public final FlagRef attributeSwap = flagBuilder(root.add("attribute-swap")).build();

    public final FlagRef trackClientAttrUpdate =
            flagBuilder(root.add("track-client-attr-update")).build();

    public final FlagRef attackStrengthUpdateWithHand =
            flagBuilder(root.add("attack-strength-update-with-hand")).build();
}
