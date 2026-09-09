package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.mine.*;
import me.matl114.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;

@ApiMethod
public class MineTasks {
    public static void init() {}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean distanceOutOfReach(BlockPos pos1, Vec3d playerPos) {
        if (pos1 == null || playerPos == null) {
            return true;
        }
        return new Box(pos1).squaredMagnitude(playerPos) > MathUtils.s2(InteractExtra.INSTANCE.getBlockReachDistance());
    }

    @Getter
    @ApiMethod
    public static final ModuleGroup moduleGroup = new ModuleGroup("Mine");

    @Getter
    private static MineExtra mineExtra;

    @Getter
    private static MiningProgressManager miningProgressManager;

    @Getter
    private static FakeBlockManager fakeBlockManager;

    @Getter
    private static MineBot mineBot;

    @Getter
    private static QueueMine queueMine;

    @Getter
    private static PacketMine packetMine;

    @Getter
    private static MineArua mineArua;

    private static void initModules(ModuleManager m) {
        mineExtra = new MineExtra().register(m);
        miningProgressManager = new MiningProgressManager().register(m);
        fakeBlockManager = new FakeBlockManager().register(m);
        mineBot = new MineBot().register(m);
        queueMine = new QueueMine().register(m);
        packetMine = new PacketMine().register(m);
        mineArua = new MineArua().register(m);
    }

    static {
        moduleGroup.registerFactories(MineTasks::initModules);
        HackModules.registerModuleGroup(moduleGroup);
    }
}
