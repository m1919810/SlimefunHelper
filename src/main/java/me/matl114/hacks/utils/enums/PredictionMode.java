package me.matl114.hacks.utils.enums;

import me.matl114.managers.config.ConfigEnum;
import org.jetbrains.annotations.ApiStatus;

public enum PredictionMode implements ConfigEnum {
    NO_PREDICT,
    LINEAR,
    QUADRATIC,
    PREDICTOR_NV,
    @ApiStatus.Experimental
    PREDICTOR_ROTATION,
    @ApiStatus.Experimental
    PREDICTOR_ACCELERATE,
    PREDICTOR_FLY_ACC;

    @Override
    public String getConfigEnumType() {
        return "predict_mode";
    }
}
