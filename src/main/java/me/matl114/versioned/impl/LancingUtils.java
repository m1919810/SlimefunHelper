package me.matl114.versioned.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class LancingUtils {
    // copied from ojng Lancing.class
    private static float method_75916(float f) {
        return 0.4F * (outQuart(lerpSpear(f, 1.0F, 3.0F)) - inOutSine(lerpSpear(f, 3.0F, 10.0F)));
    }

    public static <T extends BipedEntityRenderState> void positionArmForSpear(
            ModelPart arm, ModelPart head, boolean right, ItemStack itemStack, T state) {
        int i = right ? 1 : -1;
        arm.yaw = -0.1F * (float) i + head.yaw;
        arm.pitch = -1.5707964F + head.pitch + 0.8F;
        if (state.isGliding || state.leaningPitch > 0.0F) {
            arm.pitch -= 0.9599311F;
        }

        arm.yaw = 0.017453292F * Math.clamp(57.295776F * arm.yaw, -60.0F, 60.0F);
        arm.pitch = 0.017453292F * Math.clamp(57.295776F * arm.pitch, -120.0F, 30.0F);
        if (!(state.itemUseTime <= 0.0F)
                && (!state.isUsingItem || state.activeHand == (right ? Hand.MAIN_HAND : Hand.OFF_HAND))) {
            Kinetic kineticWeaponComponent = SWORD_KINETIC_MAP.get(itemStack.getItem());
            if (kineticWeaponComponent != null) {
                LancingContext lv = LancingContext.createContext(kineticWeaponComponent, state.itemUseTime);
                arm.yaw += (float) (-i) * lv.swayScaleFast() * 0.017453292F * lv.swayIntensity() * 1.0F;
                arm.roll += (float) (-i) * lv.swayScaleSlow() * 0.017453292F * lv.swayIntensity() * 0.5F;
                arm.pitch += 0.017453292F
                        * (-40.0F * lv.raiseProgressStart()
                                + 30.0F * lv.raiseProgressMiddle()
                                + -20.0F * lv.raiseProgressEnd()
                                + 20.0F * lv.lowerProgress()
                                + 10.0F * lv.raiseBackProgress()
                                + 0.6F * lv.swayScaleSlow() * lv.swayIntensity());
            }
        }
    }

    public static void applySpearKineticTransform(
            Item item, float f, MatrixStack matrixStack, float g, Arm arm, ItemStack itemStack) {
        if (SWORD_KINETIC_MAP.containsKey(item)) {
            LancingContext lv = LancingContext.createContext(item, g);
            int i = arm == Arm.RIGHT ? 1 : -1;
            matrixStack.translate(
                    (double) ((float) i
                            * (lv.raiseProgress() * 0.15F
                                    + lv.raiseProgressEnd() * -0.05F
                                    + lv.swayProgress() * -0.1F
                                    + lv.swayScaleSlow() * 0.005F)),
                    (double) (lv.raiseProgress() * -0.075F
                            + lv.raiseProgressMiddle() * 0.075F
                            + lv.swayScaleFast() * 0.01F),
                    (double) lv.raiseProgressStart() * 0.05
                            + (double) lv.raiseProgressEnd() * -0.05
                            + (double) (lv.swayScaleSlow() * 0.005F));
            matrixStack.multiply(
                    RotationAxis.POSITIVE_X.rotationDegrees(-65.0F * inOutBack(lv.raiseProgress())
                            - 35.0F * lv.lowerProgress()
                            + 100.0F * lv.raiseBackProgress()
                            + -0.5F * lv.swayScaleFast()),
                    0.0F,
                    0.1F,
                    0.0F);
            matrixStack.multiply(
                    RotationAxis.NEGATIVE_Y.rotationDegrees((float) i
                            * (-90.0F * lerpSpear(lv.raiseProgress(), 0.5F, 0.55F)
                                    + 90.0F * lv.swayProgress()
                                    + 2.0F * lv.swayScaleSlow())),
                    (float) i * 0.15F,
                    0.0F,
                    0.0F);
            matrixStack.translate(0.0F, -method_75916(f), 0.0F);
        }
    }

    public static void applyHeldItemFeatureArm(
            ArmedEntityRenderState armedEntityRenderState,
            MatrixStack matrixStack,
            float f,
            Arm arm,
            ItemStack itemStack) {
        Kinetic kineticWeaponComponent = SWORD_KINETIC_MAP.get(itemStack.getItem());
        if (kineticWeaponComponent != null && f != 0.0F) {
            float g = inQuad(lerpSpear(0, 0.05F, 0.2F));
            float h = inOutExpo(lerpSpear(0, 0.4F, 1.0F));
            LancingContext lv = LancingContext.createContext(kineticWeaponComponent, f);
            int i = arm == Arm.RIGHT ? 1 : -1;
            float j = 1.0F - outBack(1.0F - lv.raiseProgress());
            float k = 0.125F;
            float l = method_75916(0);
            matrixStack.translate(0.0, (double) (-l) * 0.4, (double)
                    (-kineticWeaponComponent.forwardMovement() * (j - lv.raiseBackProgress()) + l));
            matrixStack.multiply(
                    RotationAxis.NEGATIVE_X.rotationDegrees(
                            70.0F * (lv.raiseProgress() - lv.raiseBackProgress()) - 40.0F * (g - h)),
                    0.0F,
                    -0.03125F,
                    0.125F);
            matrixStack.multiply(
                    RotationAxis.POSITIVE_Y.rotationDegrees(
                            (float) (i * 90) * (lv.raiseProgress() - lv.swayProgress() + 3.0F * h + g)),
                    0.0F,
                    0.0F,
                    0.125F);
        }
    }

    public static float outBack(float t) {
        float f = 1.70158F;
        float g = 2.70158F;
        return 1.0F + 2.70158F * cube(t - 1.0F) + 1.70158F * MathHelper.square(t - 1.0F);
    }

    public static float inQuad(float t) {
        return t * t;
    }

    public static float inOutExpo(float t) {
        if (t < 0.5F) {
            return t == 0.0F ? 0.0F : (float) (Math.pow(2.0, 20.0 * (double) t - 10.0) / 2.0);
        } else {
            return t == 1.0F ? 1.0F : (float) ((2.0 - Math.pow(2.0, -20.0 * (double) t + 10.0)) / 2.0);
        }
    }

    public static float inOutBack(float t) {
        float f = 1.70158F;
        float g = 2.5949094F;
        if (t < 0.5F) {
            return 4.0F * t * t * (7.189819F * t - 2.5949094F) / 2.0F;
        } else {
            float h = 2.0F * t - 2.0F;
            return (h * h * (3.5949094F * h + 2.5949094F) + 2.0F) / 2.0F;
        }
    }

    public static float inOutSine(float t) {
        return -(MathHelper.cos((float) (3.1415927F * t)) - 1.0F) / 2.0F;
    }

    public static float outQuart(float t) {
        return 1.0F - MathHelper.square(MathHelper.square(1.0F - t));
    }

    static float lerpSpear(float f, float g, float h) {
        return MathHelper.clamp(MathHelper.getLerpProgress(f, g, h), 0.0F, 1.0F);
    }

    public static float cube(float n) {
        return n * n * n;
    }

    public static float outCubic(float t) {
        return 1.0F - cube(1.0F - t);
    }

    public static float outCirc(float t) {
        return (float) Math.sqrt((double) (1.0F - MathHelper.square(t - 1.0F)));
    }

    public static float inCirc(float f) {
        return (float) (-Math.sqrt((double) (1.0F - f * f))) + 1.0F;
    }

    public static float inOutElastic(float t) {
        float f = 1.3962635F;
        if (t == 0.0F) {
            return 0.0F;
        } else if (t == 1.0F) {
            return 1.0F;
        } else {
            double d = Math.sin((20.0 * (double) t - 11.125) * 1.3962634801864624);
            return t < 0.5F
                    ? (float) (-(Math.pow(2.0, 20.0 * (double) t - 10.0) * d) / 2.0)
                    : (float) (Math.pow(2.0, -20.0 * (double) t + 10.0) * d / 2.0 + 1.0);
        }
    }

    @Environment(EnvType.CLIENT)
    static record LancingContext(
            float raiseProgress,
            float raiseProgressStart,
            float raiseProgressMiddle,
            float raiseProgressEnd,
            float swayProgress,
            float lowerProgress,
            float raiseBackProgress,
            float swayIntensity,
            float swayScaleSlow,
            float swayScaleFast) {
        LancingContext(
                float raiseProgress,
                float raiseProgressStart,
                float raiseProgressMiddle,
                float raiseProgressEnd,
                float swayProgress,
                float lowerProgress,
                float raiseBackProgress,
                float swayIntensity,
                float swayScaleSlow,
                float swayScaleFast) {
            this.raiseProgress = raiseProgress;
            this.raiseProgressStart = raiseProgressStart;
            this.raiseProgressMiddle = raiseProgressMiddle;
            this.raiseProgressEnd = raiseProgressEnd;
            this.swayProgress = swayProgress;
            this.lowerProgress = lowerProgress;
            this.raiseBackProgress = raiseBackProgress;
            this.swayIntensity = swayIntensity;
            this.swayScaleSlow = swayScaleSlow;
            this.swayScaleFast = swayScaleFast;
        }

        public static LancingContext createContext(Item kinetic, float f) {
            var kineticWeaponComponent = SWORD_KINETIC_MAP.get(kinetic);
            return createContext(kineticWeaponComponent, f);
        }

        public static LancingContext createContext(Kinetic kineticWeaponComponent, float f) {
            int i = kineticWeaponComponent.delayTicks();
            int j = (Integer) kineticWeaponComponent
                            .dismountConditions()
                            .map(Condition::maxDurationTicks)
                            .orElse(0)
                    + i;
            int k = j - 20;
            int l = (Integer) kineticWeaponComponent
                            .knockbackConditions()
                            .map(Condition::maxDurationTicks)
                            .orElse(0)
                    + i;
            int m = l - 40;
            int n = (Integer) kineticWeaponComponent
                            .damageConditions()
                            .map(Condition::maxDurationTicks)
                            .orElse(0)
                    + i;
            float g = LancingUtils.lerpSpear(f, 0.0F, (float) i);
            float h = LancingUtils.lerpSpear(g, 0.0F, 0.5F);
            float o = LancingUtils.lerpSpear(g, 0.5F, 0.8F);
            float p = LancingUtils.lerpSpear(g, 0.8F, 1.0F);
            float q = LancingUtils.lerpSpear(f, (float) k, (float) m);
            float r = outCubic(inOutElastic(LancingUtils.lerpSpear(f - 20.0F, (float) m, (float) l)));
            float s = LancingUtils.lerpSpear(f, (float) (n - 5), (float) n);
            float t = 2.0F * outCirc(q) - 2.0F * inCirc(s);
            float u = MathHelper.sin((float) (f * 19.0F * 0.017453292F)) * t;
            float v = MathHelper.sin((float) (f * 30.0F * 0.017453292F)) * t;
            return new LancingContext(g, h, o, p, q, r, s, t, u, v);
        }

        public float raiseProgress() {
            return this.raiseProgress;
        }

        public float raiseProgressStart() {
            return this.raiseProgressStart;
        }

        public float raiseProgressMiddle() {
            return this.raiseProgressMiddle;
        }

        public float raiseProgressEnd() {
            return this.raiseProgressEnd;
        }

        public float swayProgress() {
            return this.swayProgress;
        }

        public float lowerProgress() {
            return this.lowerProgress;
        }

        public float raiseBackProgress() {
            return this.raiseBackProgress;
        }

        public float swayIntensity() {
            return this.swayIntensity;
        }

        public float swayScaleSlow() {
            return this.swayScaleSlow;
        }

        public float swayScaleFast() {
            return this.swayScaleFast;
        }
    }

    public static final Map<Item, Kinetic> SWORD_KINETIC_MAP = new HashMap<>();
    // 定义简易记录
    public static record Condition(int maxDurationTicks, float minSpeed, float minRelativeSpeed) {
        public static Condition ofMinSpeed(int maxDurationTicks, float minSpeed) {
            return new Condition(maxDurationTicks, minSpeed, 0.0f);
        }

        public static Condition ofMinRelativeSpeed(int maxDurationTicks, float minRelativeSpeed) {
            return new Condition(maxDurationTicks, 0.0f, minRelativeSpeed);
        }
    }

    public static record Kinetic(
            int contactCooldownTicks,
            int delayTicks,
            float forwardMovement,
            float damageMultiplier,
            Optional<Condition> dismountConditions,
            Optional<Condition> knockbackConditions,
            Optional<Condition> damageConditions) {}

    static {
        // 木剑 -> 木矛的参数
        SWORD_KINETIC_MAP.put(
                Items.WOODEN_SWORD,
                new Kinetic(
                        10, // contactCooldownTicks
                        (int) (0.75f * 20), // delayTicks = 15
                        0.38f, // forwardMovement
                        0.7f, // damageMultiplier
                        Optional.of(Condition.ofMinSpeed((int) (5.0f * 20), 14.0f)), // dismount
                        Optional.of(Condition.ofMinSpeed((int) (10.0f * 20), 5.1f)), // knockback
                        Optional.of(Condition.ofMinRelativeSpeed((int) (15.0f * 20), 4.6f)) // damage
                        ));
        // 石剑 -> 石矛的参数
        SWORD_KINETIC_MAP.put(
                Items.STONE_SWORD,
                new Kinetic(
                        10,
                        (int) (0.7f * 20), // 14
                        0.38f,
                        0.82f,
                        Optional.of(Condition.ofMinSpeed((int) (4.5f * 20), 10.0f)),
                        Optional.of(Condition.ofMinSpeed((int) (9.0f * 20), 5.1f)),
                        Optional.of(Condition.ofMinRelativeSpeed((int) (13.75f * 20), 4.6f))));
        // 铁剑 -> 铁矛的参数
        SWORD_KINETIC_MAP.put(
                Items.IRON_SWORD,
                new Kinetic(
                        10,
                        (int) (0.6f * 20), // 12
                        0.38f,
                        0.95f,
                        Optional.of(Condition.ofMinSpeed((int) (2.5f * 20), 8.0f)),
                        Optional.of(Condition.ofMinSpeed((int) (6.75f * 20), 5.1f)),
                        Optional.of(Condition.ofMinRelativeSpeed((int) (11.25f * 20), 4.6f))));
        // 金剑 -> 金矛的参数
        SWORD_KINETIC_MAP.put(
                Items.GOLDEN_SWORD,
                new Kinetic(
                        10,
                        (int) (0.7f * 20), // 14
                        0.38f,
                        0.7f,
                        Optional.of(Condition.ofMinSpeed((int) (3.5f * 20), 10.0f)),
                        Optional.of(Condition.ofMinSpeed((int) (8.5f * 20), 5.1f)),
                        Optional.of(Condition.ofMinRelativeSpeed((int) (13.75f * 20), 4.6f))));
        // 钻石剑 -> 钻石矛的参数
        SWORD_KINETIC_MAP.put(
                Items.DIAMOND_SWORD,
                new Kinetic(
                        10,
                        (int) (0.5f * 20), // 10
                        0.38f,
                        1.075f,
                        Optional.of(Condition.ofMinSpeed((int) (3.0f * 20), 7.5f)),
                        Optional.of(Condition.ofMinSpeed((int) (6.5f * 20), 5.1f)),
                        Optional.of(Condition.ofMinRelativeSpeed((int) (10.0f * 20), 4.6f))));
        // 下界合金剑 -> 下界合金矛的参数
        SWORD_KINETIC_MAP.put(
                Items.NETHERITE_SWORD,
                new Kinetic(
                        10,
                        (int) (0.4f * 20), // 8
                        0.38f,
                        1.2f,
                        Optional.of(Condition.ofMinSpeed((int) (2.5f * 20), 7.0f)),
                        Optional.of(Condition.ofMinSpeed((int) (5.5f * 20), 5.1f)),
                        Optional.of(Condition.ofMinRelativeSpeed((int) (8.75f * 20), 4.6f))));
    }
}
