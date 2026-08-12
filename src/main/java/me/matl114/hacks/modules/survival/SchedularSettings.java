package me.matl114.hacks.modules.survival;

import java.util.function.Consumer;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Vec3;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.move.PathingSchedular;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.util.Formatting;

public class SchedularSettings extends BaseModule {
    public static SchedularSettings INSTANCE;

    public SchedularSettings() {
        super("Schedular");
        INSTANCE = this;
    }

    final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "schedular-settings");

    public final EnumRef<EngineType> type = builder(root.add("engin-type"), EngineType.class)
            .defaultValue(EngineType.BARITONE)
            .build();

    public final FlagRef enableAutoReplenish =
            builder(root.add("replenish"), Boolean.class).defaultValue(true).build();

    public final FlagRef enableAutoDischarge =
            builder(root.add("discharge"), Boolean.class).defaultValue(true).build();

    public final IntRef defaultInventoryActionPerTick =
            intBuilder(root.add("inv-speed-limit")).defaultValue(27).build();

    public final IntRef hotbarProtectRange =
            intBuilder(root.add("hotbar-protect")).defaultValue(0).build();

    public final NBTRef<Vec3> scannChestRange = builder(root.add("scann-chest-range"), Vec3.class)
            .defaultValue(new Vec3(20, 5, 20))
            .build();

    public final FlagRef enableAutoExpandShulker = builder(root.add("enable-auto-expand-shulker"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableAutoExpandChest =
            flagBuilder(root.add("enable-auto-expand-chest")).build();

    public final FlagRef containerSourceHotReload =
            flagBuilder(root.add("enable-container-hot-reload")).build();

    public final FlagRef warn =
            builder(root.add("warn"), Boolean.class).defaultValue(true).build();

    public final FlagRef detailed = flagBuilder(root.add("detailed-log")).build();

    public final FlagRef pause = flagBuilder(root.add("pause")).build();

    public final KeyBindRef pauseHotkey =
            hotkey(root.add("pause-hotkey"), new MultiKeyBind()).build();

    public final KeyBindRef holdReset =
            hotkey(root.add("hold-reset"), new MultiKeyBind()).build();

    public final FlagRef enableRender =
            builder(root.add("enable-render"), Boolean.class).defaultValue(true).build();

    public final NBTRef<WrapColor> colorReplenishment = builder(root.add("replenishment-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.BLUE)))
            .build();

    public final NBTRef<WrapColor> colorDischarge = builder(root.add("discharge-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.RED)))
            .build();

    public final NBTRef<WrapColor> colorShulkerSupport = builder(root.add("shulker-support-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.YELLOW)))
            .build();

    public final NBTRef<WrapColor> colorGoal = builder(root.add("goal-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.GREEN)))
            .build();

    public final NBTRef<WrapColor> colorLines = builder(root.add("color-lines"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.AQUA))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitleLabel("widget.interact.interact-block.use-argument", 0, dblank, dx, dy));
    }

    public PathingSchedular.PathingEngine createEngine() {
        switch (type.get()) {
            case BARITONE -> {
                if (BaritoneHooks.getInstance().isBaritoneAPISupported()) {
                    return new PathingSchedular.BaritonePathingEngine();
                } else {
                    return new PathingSchedular.NonePathingEngine();
                }
            }
            default -> {
                return new PathingSchedular.NonePathingEngine();
            }
        }
    }

    TimerExecutor timerNoContainer = new TimerExecutor();

    public void logNoContainer() {
        if (warn.get()) {
            timerNoContainer.run(
                    100, () -> logI18N("message.module.schedular-settings.logic-error.no-container-source"));
        }
    }

    TimerExecutor timerReloadContainer = new TimerExecutor();

    public void logNoSuitableContainer() {
        if (warn.get()) {
            timerNoContainer.run(
                    100, () -> logI18N("message.module.schedular-settings.logic-error.no-suitable-container-source"));
        }
    }

    public void logReloadContainer() {
        if (warn.get()) {
            timerReloadContainer.run(
                    100, () -> logI18N("message.module.schedular-settings.logic.hot-reload-container-source"));
        }
    }

    TimerExecutor holdUse = new TimerExecutor();

    public void logPause() {
        if (warn.get()) {
            holdUse.run(100, () -> logI18N("message.module.schedular-settings.logic.pause-progress"));
        }
    }

    public void logHoldReset() {
        if (warn.get()) {
            holdUse.run(100, () -> logI18N("message.module.schedular-settings.logic.reset-progress"));
        }
    }

    public void logExpandContainer() {
        if (warn.get()) {
            logI18N("message.module.schedular-settings.logic.expand-storage");
        }
    }

    public enum EngineType implements ConfigEnum {
        NONE,
        BARITONE;

        @Override
        public String getConfigEnumType() {
            return "schedular_engine_type";
        }
    }
}
