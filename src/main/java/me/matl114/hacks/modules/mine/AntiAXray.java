package me.matl114.hacks.modules.mine;

import java.util.HashSet;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AntiAXray extends BaseModule {
    public AntiAXray() {
        bindFlag(enable);
    }

    public final ModulePath simple = makePath(Configs.MINE_CONFIG, "aaxray.simple");

    public final FlagRef enable =
            flagBuilder(simple.add("enable")).defaultValue(false).build();

    public final KeyBindRef hotkey = moduleEntry(simple.add("hotkey"), new MultiKeyBind(), simple.add("enable"))
            .build();

    public final IntRef limitation = builder(simple.add("packet-limit"), IntRef.TYPE)
            .defaultValue(30)
            .validator(Configs.INT_POSITIVE)
            .build();

    private static final Set<BlockPos> simpleDetection = new HashSet<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    public void onTick(Event<ClientPlayerEntity> player) {
        if (enable.get()) {
            int tick = Tasks.getTick();

            if (tick % 20 == 0) {
                doSimpleDetection();
                if (tick % (20 * 60) == 0) {
                    clearSimpleDetectionCache();
                }
            }
        }
    }

    public void onWorldSwitch(Event<World> event) {
        clearSimpleDetectionCache();
    }

    public void clearSimpleDetectionCache() {
        simpleDetection.clear();
    }

    @ApiMethod
    public void doSimpleDetection() {

        BlockPos currentPlayer = mc.player.getSteppingPos().add(0, 1, 0);
        int limitation = 0;
        for (var posDelta : MineExtra.INSTANCE.getBlocksAround()) {
            BlockPos testPos = currentPlayer.add(posDelta);
            if (!MineTasks.distanceOutOfReach(testPos, mc.player.getEyePos())) {
                if (!simpleDetection.contains(testPos)) {
                    simpleDetection.add(testPos);
                    mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                        Vec3d shouldFacing = testPos.toCenterPos().subtract(mc.player.getEyePos());
                        Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                        // use real direction
                        return new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, testPos, dir, sequence);
                    });
                    if (++limitation >= this.limitation.get()) {
                        break;
                    }
                }
            }
        }
    }
}
