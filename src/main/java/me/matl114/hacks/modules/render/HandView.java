package me.matl114.hacks.modules.render;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Vec3;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;

public class HandView extends BaseModule {
    public static HandView INSTANCE;

    public final ModulePath handView = makePath(Configs.RENDER_CONFIG, "hand-view");
    public final ModulePath mainHand = handView.add("main-hand");
    public final ModulePath offHand = handView.add("off-hand");
    public final ModulePath arm = handView.add("arm");

    public HandView() {
        super("HandView");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final FlagRef enable = flagBuilder(handView.addEnable()).build();

    public final NBTRef<Vec3> mainHandScale = builder(mainHand.add("scale"), Vec3.class)
            .defaultValue(new Vec3(1, 1, 1))
            .build();

    public final NBTRef<Vec3> mainHandPosition = builder(mainHand.add("position"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> mainHandRotation = builder(mainHand.add("rotation"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> offHandScale = builder(offHand.add("scale"), Vec3.class)
            .defaultValue(new Vec3(1, 1, 1))
            .build();

    public final NBTRef<Vec3> offHandPosition = builder(offHand.add("position"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> offHandRotation = builder(offHand.add("rotation"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> armScale = builder(arm.add("scale"), Vec3.class)
            .defaultValue(new Vec3(1, 1, 1))
            .build();

    public final NBTRef<Vec3> armPosition = builder(arm.add("position"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> armRotation = builder(arm.add("rotation"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final FlagRef armTint = flagBuilder(arm.add("tint")).build();

    public final NBTRef<WrapColor> armColor = builder(arm.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.WHITE))
            .build();

    public final FlagRef armGlint = flagBuilder(arm.add("glint")).build();

    public void applyHandTransform(MatrixStack matrices, Hand hand) {
        Vec3 rotation = hand == Hand.MAIN_HAND ? mainHandRotation.get() : offHandRotation.get();
        Vec3 scale = hand == Hand.MAIN_HAND ? mainHandScale.get() : offHandScale.get();
        Vec3 position = hand == Hand.MAIN_HAND ? mainHandPosition.get() : offHandPosition.get();
        applyTransform(matrices, rotation, scale, position);
    }

    public void applyArmTransform(MatrixStack matrices) {
        applyTransform(matrices, armRotation.get(), armScale.get(), armPosition.get());
    }

    public boolean useArmEffect() {
        return isActive() && (armTint.get() || armGlint.get());
    }

    public int armTintColor() {
        return armColor.get().withAlpha(255);
    }

    public boolean armHasGlint() {
        return armGlint.get();
    }

    private static void applyTransform(MatrixStack matrices, Vec3 rotation, Vec3 scale, Vec3 position) {
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z()));
        matrices.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        matrices.translate((float) position.x(), (float) position.y(), (float) position.z());
    }
}
