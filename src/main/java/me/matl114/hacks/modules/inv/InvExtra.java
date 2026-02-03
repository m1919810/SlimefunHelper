package me.matl114.hacks.modules.inv;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.IntRef;

public class InvExtra extends BaseModule {
    public static final String[] INV_CLICK_LIMIT={"inventory","packet-limit"};

    public InvExtra() {

    }

    public final IntRef inventoryClickLimit = builder(Configs.INV_CONFIG, IntRef.TYPE)
        .path(INV_CLICK_LIMIT)
        .defaultValue(40)
        .validator(Configs.INT_POSITIVE)
        .build();
}
