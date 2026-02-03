package me.matl114.hacks.modules.mine;

import me.matl114.events.Listener;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import me.matl114.events.Event;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class AntiAXray extends BaseModule {
    //todo: left for test
    public void onAntiXrayDemoTest(BlockPos testPos){
        Debug.chat("starting test on block ", testPos);
//        mc.getNetworkHandler().sendPacket(new );
    }

    public AntiAXray() {
        bindFlag(enable);
    }

    public static final String[] XRAY_ENABLE_SIMPLE = {"aaxray", "enable-simple"};
    public static final String[] XRAY_SIMPLE_LIMITATION = {"aaxray", "simple-detect-packet-limit"};
    public final FlagRef enable = builder(Configs.MINE_CONFIG, Boolean.class)
        .path(XRAY_ENABLE_SIMPLE)
        .defaultValue(false)
        .build();

    public final IntRef limitation = builder(Configs.MINE_CONFIG, Integer.class)
        .path(XRAY_SIMPLE_LIMITATION)
        .defaultValue(30)
        .validator(Configs.INT_POSITIVE)
        .build()

    ;
    private static final Set<BlockPos> simpleDetection = new HashSet<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getGameTick(), this::onTick);
    }

    public void onTick(Event<ClientPlayerEntity> player){
        if(enable.get()) {
            int tick = Tasks.getTick();

            if (tick % 20 == 0) {
                doSimpleDetection();
                if (tick % (20 * 60) == 0) {
                    clearSimpleDetectionCache();
                }
            }
        }
    }

    public void onWorldSwitch(Event<World> event){
        clearSimpleDetectionCache();
    }

    public void clearSimpleDetectionCache(){
        simpleDetection.clear();
    }

    @ApiMethod
    public void doSimpleDetection(){

        BlockPos currentPlayer = mc.player.getSteppingPos().add(0, 1,0);
        int limitation = 0;
        for(var posDelta : MineTasks.getMineExtra().getBlocksAround()){
            BlockPos testPos = currentPlayer.add(posDelta);
            if(!MineTasks.distanceOutOfReach(testPos, mc.player.getEyePos())){
                //TODO 不要增加暴露判定
                if(!simpleDetection.contains(testPos)){
                    simpleDetection.add(testPos);
                    //TODO: add packet number limitation here
                    mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                        Vec3d shouldFacing = testPos.toCenterPos().subtract(mc.player.getEyePos());
                        Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                        // use real direction
                        return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, testPos, dir, sequence);
                    });
                    if(++limitation >= this.limitation.get()){
                        break;
                    }
                }
            }
        }
    }




    //todo add minearua
}
