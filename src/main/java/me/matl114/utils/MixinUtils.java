package me.matl114.utils;

import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class MixinUtils {
    public static final Field fieldTargetCancel;

    static {
        fieldTargetCancel = ReflectUtils.getField(CallbackInfo.class, "cancelled");
    }

    public static void resetCancel(CallbackInfo ci) {
        if (ci.isCancelled()) {
            try {
                if (fieldTargetCancel != null) {
                    fieldTargetCancel.set(ci, true);
                }
            } catch (Throwable e) {
            }
        }
    }
}
