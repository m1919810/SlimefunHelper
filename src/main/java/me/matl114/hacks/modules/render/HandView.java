package me.matl114.hacks.modules.render;

import java.awt.Color;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render2D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Vec3;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.versioned.api.ModelFaceFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;

public class HandView extends BaseModule {
    public static HandView INSTANCE;

    public final ModulePath handView = makePath(Configs.RENDER_CONFIG, "hand-view");
    public final ModulePath mainHand = handView.add("main-hand");
    public final ModulePath mainHandEmpty = handView.add("main-hand-empty");
    public final ModulePath offHand = handView.add("off-hand");

    public HandView() {
        super("HandView");
        bindFlag(enable);
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRender2DEvent(), this::renderHandOutline);
    }

    private void renderHandOutline(Event<Render2D> event) {
        var renderer = ModelFaceFactory.getInstance();
        if (!isActive() || !glow.get() || event.context.hudHidden()) {
            renderer.clear();
            return;
        }
        renderer.render2D(boundaryColor.get().withAlpha(255));
    }

    public final FlagRef enable = flagBuilder(handView.addEnable()).build();

    public final FlagRef glow = flagBuilder(handView.add("glow")).build();

    public final NBTRef<WrapColor> glowColor = builder(handView.add("glow-color"), WrapColor.class)
            .defaultValue(new WrapColor(Color.CYAN))
            .build();

    public final NBTRef<WrapColor> boundaryColor = builder(handView.add("boundary-color"), WrapColor.class)
            .defaultValue(new WrapColor(Color.CYAN))
            .build();

    public final IntRef glowAlpha = builder(handView.add("glow-alpha"), IntRef.TYPE)
            .defaultValue(32)
            .validator(Configs.intRange(0, 255))
            .build();

    public final NBTRef<Vec3> mainHandScale = builder(mainHand.add("scale"), Vec3.class)
            .defaultValue(new Vec3(1, 1, 1))
            .build();

    public final NBTRef<Vec3> mainHandPosition = builder(mainHand.add("position"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> mainHandEmptyScale = builder(mainHandEmpty.add("scale"), Vec3.class)
            .defaultValue(new Vec3(1, 1, 1))
            .build();

    public final NBTRef<Vec3> mainHandEmptyPosition = builder(mainHandEmpty.add("position"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public final NBTRef<Vec3> offHandScale = builder(offHand.add("scale"), Vec3.class)
            .defaultValue(new Vec3(1, 1, 1))
            .build();

    public final NBTRef<Vec3> offHandPosition = builder(offHand.add("position"), Vec3.class)
            .defaultValue(new Vec3(0, 0, 0))
            .build();

    public void applyHandTransform(MatrixStack matrices, Hand hand, boolean handEmpty) {
        Vec3 scale = hand == Hand.MAIN_HAND
                ? (handEmpty ? mainHandEmptyScale.get() : mainHandScale.get())
                : offHandScale.get();
        Vec3 position = hand == Hand.MAIN_HAND
                ? (handEmpty ? mainHandEmptyPosition.get() : mainHandPosition.get())
                : offHandPosition.get();
        applyTransform(matrices, scale, position);
    }

    private static void applyTransform(MatrixStack matrices, Vec3 scale, Vec3 position) {
        matrices.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        matrices.translate((float) position.x(), (float) position.y(), (float) position.z());
    }
}
