package me.matl114.versioned.api;

import me.matl114.versioned.impl.HandFaceRenderer_v1_21_11;

/** Factory for the model-face renderer selected for the running game version. */
public final class ModelFaceFactory {
    private static final HandFaceRenderer_v1_21_11 INSTANCE = create();

    private ModelFaceFactory() {}

    public static HandFaceRenderer_v1_21_11 getInstance() {
        return INSTANCE;
    }

    public static HandFaceRenderer_v1_21_11 create() {
        return new HandFaceRenderer_v1_21_11();
    }
}
