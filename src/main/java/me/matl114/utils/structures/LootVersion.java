package me.matl114.utils.structures;

import me.matl114.managers.config.ConfigEnum;

/** Minecraft versions whose ancient city chest loot tables are known to this predictor. */
public enum LootVersion implements ConfigEnum {
    V1_21_1("1.21.1", true, false),
    V1_21_4("1.21.4", true, false),
    V1_21_11("1.21.11", true, true),
    V26_1("26.1", false, true);

    private final String gameVersion;
    private final boolean ancientCityHasNameTag;
    private final boolean ancientCityUsesLeatherInsteadOfSaddle;

    LootVersion(String gameVersion, boolean ancientCityHasNameTag, boolean ancientCityUsesLeatherInsteadOfSaddle) {
        this.gameVersion = gameVersion;
        this.ancientCityHasNameTag = ancientCityHasNameTag;
        this.ancientCityUsesLeatherInsteadOfSaddle = ancientCityUsesLeatherInsteadOfSaddle;
    }

    public String gameVersion() {
        return gameVersion;
    }

    public boolean ancientCityHasNameTag() {
        return ancientCityHasNameTag;
    }

    public boolean ancientCityUsesLeatherInsteadOfSaddle() {
        return ancientCityUsesLeatherInsteadOfSaddle;
    }

    @Override
    public String getConfigEnumType() {
        return "loot_version";
    }
}
