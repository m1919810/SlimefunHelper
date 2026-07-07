package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.Mouse;

public class Zoom extends BaseModule {
    public Zoom() {
        bindFlag(enable);
    }

    public final ModulePath render = makePath(Configs.RENDER_CONFIG, "render");
    public final ModulePath zoom = makePath(Configs.RENDER_CONFIG, "zoom");
    public FlagRef enable =
            builder(zoom.addEnable(), Boolean.class).defaultValue(true).build();

    public KeyBindRef holdUse = hotkey(zoom.addHotkey(), new MultiKeyBind()).build();

    public FlagRef useScroll =
            builder(zoom.add("scroll-scale"), Boolean.class).defaultValue(true).build();

    public DoubleRef defaultZoom = doubleBuilder(zoom.add("default-zoom"))
            .defaultValue(3.0D)
            .validator(Configs.doubleRange(0, 114514))
            .build();

    public DoubleRef minZoom = doubleBuilder(zoom.add("min-zoom-scale"))
            .defaultValue(1.0D)
            .validator(Configs.doubleRange(0, 114514))
            .build();

    public NBTRef<OptionalPrimitive<Double>> overrideCommonZoom = builder(
                    zoom.add("override-common-fov"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 1.0D))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getFovGetListener(), this::tickFov);
    }

    static boolean currentHasScrollListener = false;
    Double currentScale = null;
    private Double defaultMouseSensitivity = null;

    public void tryRegisterScrollListener() {
        if (!currentHasScrollListener && useScroll.get()) {
            currentHasScrollListener = true;
            Listener.getMouseScroll().registerHandler((mouseEvent) -> {
                if (useScroll.get() && holdUse.get().isAllPressed()) {
                    onScroll(mouseEvent);
                    return true;
                } else {
                    currentScale = null;
                    currentHasScrollListener = false;
                    return false;
                }
            });
        }
    }

    private void onScroll(Event<Mouse> eventMouseScroll) {
        if (currentScale == null) {
            currentScale = defaultZoom.get();
        }
        double vertical = eventMouseScroll.getArgs(1);
        if (vertical > 0) {
            currentScale *= 1.1;
        } else if (vertical < 0) {
            currentScale *= 0.9;
        }
        currentScale = Math.max(minZoom.get(), currentScale);
        eventMouseScroll.cancel();
    }

    public void tickFov(Event<Float> eventFov) {
        if (holdUse.get().isAllPressed() && mc.currentScreen == null) {
            if (currentScale == null) {
                currentScale = defaultZoom.get();
            }
            if (defaultMouseSensitivity == null) {
                defaultMouseSensitivity = mc.options.getMouseSensitivity().getValue();
            }
            mc.options.getMouseSensitivity().setValue(defaultMouseSensitivity / currentScale);
            tryRegisterScrollListener();
            eventFov.context((float) (eventFov.context() / currentScale));
        } else {
            currentScale = null;
            if (defaultMouseSensitivity != null) {
                mc.options.getMouseSensitivity().setValue(defaultMouseSensitivity);
                defaultMouseSensitivity = null;
            }
        }
    }
}
