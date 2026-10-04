package me.matl114.hacks.modules.extra;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoDick extends BaseModule {
    public AutoDick() {
        super("AutoDick");
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "other.auto-dick");

    public final KeyBindRef hotkey = hotkey(root.add("place-dick-at-target"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::markCenter))
            .build();

    public final IntRef delay = intBuilder(root.add("delay-ms")).defaultValue(6).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
    }

    BlockPos centerPos;
    Direction facingDirection;
    int startTick;

    private void markCenter() {
        if (checkNull()) return;
        if (mc.crosshairTarget != null
                && mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            centerPos = hit.getBlockPos().offset(hit.getSide());
            facingDirection = mc.player.getHorizontalFacing();
            startTick = 0;
        }
    }

    private void onPreInputEvent(Event<Void> event) {
        if (checkNull()) {
            return;
        }
        if (centerPos != null && facingDirection != null) {
            if (startTick == 0) {
                Direction hor = facingDirection.rotateClockwise(Direction.Axis.Y);
                BlockPos pos1 = centerPos.offset(hor);
                Interact.INSTANCE.placeBlock(pos1);
                BlockPos pos2 = centerPos.offset(hor.getOpposite());
                for (var i = 0; i < 9; ++i) {
                    Interact.INSTANCE.placeBlock(pos2);
                }
                Interact.INSTANCE.placeBlock(centerPos);
                Interact.INSTANCE.placeBlock(centerPos.up());
                startTick = Tasks.getTick();
                PlayerInputManager.INSTANCE.addJumpModifier(0, true, 1);
            } else if (startTick + 1 == Tasks.getTick()) {
                Interact.INSTANCE.placeBlock(centerPos.up().up());
            } else if (startTick + delay.get() < Tasks.getTick()) {
                BlockPos top = centerPos.up();
                Interact.INSTANCE.interactBlock(top.up());
                Interact.INSTANCE.interactBlock(top);
                centerPos = null;
                facingDirection = null;
                startTick = 0;
            }
        }
    }
}
