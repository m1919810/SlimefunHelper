package me.matl114.hacks.api;

public enum ModulePreset {
    HACKING,
    VANILLA,
    AC_COMMON,
    AC_GRIM,
    AC_GRIM_LEGACY,
    AC_MATRIX,
    AC_VULCAN,
    ENABLE_AIRPLACE,
    DISABLE_AIRPLACE,
    ENABLE_NOSWINGPLACE,
    DISABLE_NOSWINGPLACE,
    ENABLE_MULTITASK,
    DISABLE_MULTITASK;

    public boolean hasAC() {
        return !(this == VANILLA || this == AC_VULCAN);
    }

    public boolean getAirplace(boolean defaultValue) {
        return switch (this) {
            case ENABLE_AIRPLACE -> true;
            case DISABLE_AIRPLACE -> false;
            default -> defaultValue;
        };
    }

    public boolean isAirplacePreset() {
        return this == ENABLE_AIRPLACE || this == DISABLE_AIRPLACE;
    }

    public boolean getSwingPlace(boolean defaultValue) {
        return switch (this) {
            case ENABLE_NOSWINGPLACE -> false;
            case DISABLE_NOSWINGPLACE -> true;
            default -> defaultValue;
        };
    }

    public boolean isSwingPlacePreset() {
        return this == ENABLE_NOSWINGPLACE || this == DISABLE_NOSWINGPLACE;
    }

    public boolean getEatingAbort(boolean defaultValue) {
        return switch (this) {
            case ENABLE_MULTITASK -> false;
            case DISABLE_MULTITASK -> true;
            default -> defaultValue;
        };
    }

    public boolean isMultitaskPreset() {
        return this == ENABLE_MULTITASK || this == DISABLE_MULTITASK;
    }

    public boolean isOptionPreset() {
        return isAirplacePreset() || isSwingPlacePreset() || isMultitaskPreset();
    }
}
