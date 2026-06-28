package me.matl114.hacks.modules.move;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;

public class AntiLiquid extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public AntiLiquid() {
        bindFlag(enable);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
    public final ModulePath antiLiquid = moveSafety.add("anti-liquid");

    public final FlagRef enable = flagBuilder(antiLiquid.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    antiLiquid.addHotkey(), new MultiKeyBind(), antiLiquid.addEnable(), moduleMeta(() -> this.mode))
            .build();

    public final EnumRef<Mode> mode =
            builder(antiLiquid.add("mode"), Mode.class).defaultValue(Mode.NONE).build();

    public final FlagRef enableWhenNotFly =
            flagBuilder(antiLiquid.add("enable-not-fly")).build();

    public final FlagRef enableWhenFly =
            flagBuilder(antiLiquid.add("enable-fly")).build();

    public final FlagRef autoArmorFlyControl =
            flagBuilder(antiLiquid.add("auto-armor-fly-control")).build();

    public final IntRef switchElytraGt = intBuilder(antiLiquid.add("auto-armor-fly-switch-gt"))
            .defaultValue(5)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    boolean currentArmorGlidingSaveState;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (currentArmorGlidingSaveState) {
            if (ElytraExtra.INSTANCE.nextPacketResetFallFlying > 0) {
                if (Tasks.getTick() >= switchElytraGt.get() + taskLast) {
                    ElytraExtra.INSTANCE.nextPacketResetFallFlying = 0;
                } else {
                    movementManagerEvent.cancel();
                    movementManagerEvent.context.markForResetPos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    return;
                }
            }
        }
        if (enable.get() && mode.get() != Mode.NONE) {
            // check condition
            var box = mc.player.getBoundingBox();
            var blocks = MathUtils.getOccupiedBlockPositions(box);
            for (var block : blocks) {
                BlockState state = mc.world.getBlockState(block);
                if (state.isLiquid()) {
                    Fluid fluid = state.getFluidState().getFluid();
                    boolean isWater = fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
                    boolean isLava = fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
                    Mode mode = this.mode.get();
                    boolean lava = (mode.isAntiLava() && isLava);
                    boolean water = (mode.isAntiWater() && isWater);
                    if (lava || water) {
                        handleMayFlyIntoFluid(movementManagerEvent, lava, water);
                        return;
                    }
                }
            }
            handleOutOfWater();
        }
    }

    int taskLast = 0;

    private void handleMayFlyIntoFluid(Event<LegalMovementManager> eventMove, boolean isLava, boolean isWater) {
        boolean lastNotInWater = !PlayerStateManager.INSTANCE.lastInWater;
        boolean lastNotInLava = !PlayerStateManager.INSTANCE.lastInLava;
        if ((isWater && lastNotInWater) || (isLava && lastNotInLava)) {
            if (mc.player.isFallFlying()) {
                if (isWater && autoArmorFlyControl.get() && ElytraExtra.INSTANCE.armorFly.get()) {
                    if (ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                        ElytraExtra.INSTANCE.endArmorFlyTransaction(true);
                        eventMove.cancel();
                        eventMove.context.markForResetPos();
                        currentArmorGlidingSaveState = true;
                        taskLast = Tasks.getTick();
                        FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                        return;
                    }
                }
                if (enableWhenFly.get()) {
                    eventMove.cancel();
                    eventMove.context.markForResetPos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                }
            } else {
                if (enableWhenNotFly.get()) {
                    eventMove.cancel();
                    eventMove.context.markForResetPos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                }
            }
        }
    }

    private void handleOutOfWater() {
        if (currentArmorGlidingSaveState && taskLast + 4 * switchElytraGt.get() < Tasks.getTick()) {
            currentArmorGlidingSaveState = false;
            if (mc.player.isFallFlying()
                    && ElytraExtra.INSTANCE.armorFly.get()
                    && !ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public static enum Mode implements ConfigEnum {
        NONE(false, false),
        ANTI_WATER(true, false),
        ANTI_LAVA(false, true),
        ALL(true, true);
        final boolean antiWater;
        final boolean antiLava;

        @Override
        public String getConfigEnumType() {
            return "anti_liquid_anti_liquid_mode";
        }
    }
}
