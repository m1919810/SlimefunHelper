package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class SwimControl extends BaseModule implements LegalMovementManager.MovementModifier {
    public static SwimControl INSTANCE;
    static LegalMovementManager.DelegateMovementModifier instance;

    public SwimControl() {
        super("SwimControl");
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final ModulePath moveSpeed = makePath(Configs.MOV_CONFIG, "move-speed");
    public final ModulePath swimControl = moveSpeed.add("swim-control");
    public final FlagRef enable = flagBuilder(swimControl.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(swimControl.addHotkey(), new MultiKeyBind(), swimControl.addEnable())
            .build();

    public final EnumRef<Mode> mode =
            builder(swimControl.add("mode"), Mode.class).defaultValue(Mode.GRIM).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (enable.get() && !player.isFallFlying() && player.isTouchingWater() && player.shouldSwimInFluids()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player);
            if (!input.hasMovementControl()) {
                if (mode.get().isIn(Mode.GRIM)) {
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    movementManagerEvent.cancel();
                } else if (mode.get().isIn(Mode.NONE)) {
                    movementManagerEvent.cancel();
                }
            }
        }
    }

    public static enum Mode implements ConfigEnum {
        NONE,
        GRIM;

        @Override
        public String getConfigEnumType() {
            return "swim_control_mode";
        }
    }
}
