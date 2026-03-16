package me.matl114.hacks;

import java.util.*;
import lombok.Getter;
import me.matl114.commands.MainCommand;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.mine.*;
import me.matl114.managers.config.Config;
import me.matl114.utils.*;
import me.matl114.utils.commands.commandGroup.AbstractMainCommand;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
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
        return new Box(pos1).squaredMagnitude(playerPos) > MathUtils.s2(mineExtra.getReachDistance() + 1.0);
    }

    @Getter
    @ApiMethod
    public static final ModuleGroup moduleGroup = new ModuleGroup("Mine");

    @Getter
    public static MineExtra mineExtra;

    @Getter
    public static MineBot mineBot;

    @Getter
    public static PacketMine packetMine;

    @Getter
    public static SeedOre seedOre;

    @Getter
    public static MineArua mineArua;

    @Getter
    public static AntiAXray antiAXray;

    private static void initModules(ModuleManager m) {
        mineExtra = new MineExtra().register(m);
        mineBot = new MineBot().register(m);
        packetMine = new PacketMine().register(m);

        mineArua = new MineArua().register(m);
        seedOre = new SeedOre().register(m);
        antiAXray = new AntiAXray().register(m);
    }

    public static class SeedCommand extends AbstractMainCommand {
        TreeSubCommand main = mainBuilder().name("mine").build();

        {
            setMainName("mine");
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("seedore")
                    .helper("<toggle> 切换是否启动seed ore simulation")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("toggle")
                            .bool()
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onSeedOre)))
                    .complete();
        }

        public void onSeedOre(ArgumentInputStream re) {
            boolean var = re.nextBoolean();

            if (var) {
                if (!seedOre.isActive()) {
                    seedOre.enable.set(true);
                }
            } else {
                if (seedOre.isActive()) {
                    seedOre.enable.set(false);
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("seed")
                    .helper("<operation> <seed> 进行seed操作")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("operation")
                            .select(List.of("set", "remove", "validate", "list"))
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("seed")
                            .select(List.of("0", "114514"))
                            .tabSupplier(() -> seedOre.seedMap.keySet().stream())
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onSeed)))
                    .complete();
        }

        public void onSeed(ArgumentInputStream re) {
            String op = re.nextNonnull();
            switch (op) {
                case "set" -> {
                    String na = re.nextNonnull();
                    long val;
                    if (seedOre.seedMap.containsKey(na)) {
                        val = seedOre.seedMap.getLong(na);
                    } else {
                        val = Long.parseLong(na);
                    }
                    seedOre.setWorldSeed(val);
                    me.matl114.utils.Debug.chat("[世界种子] 设置", CommonUtils.getWorldName(), "的种子为", val);
                }
                case "remove" -> {
                    String key = re.nextNonnull();
                    seedOre.removeSeed(key);
                }
                case "list" -> {
                    Debug.chat("[世界种子] 列表");
                    for (var entry : seedOre.seedMap.object2LongEntrySet()) {
                        Debug.chat(entry.getKey(), ":", ChatUtils.getDisplayedLong(entry.getLongValue()));
                    }
                }
                case "validate" -> {
                    seedOre.validateCurrentSeed();
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("orerender")
                    .helper("<toggle> 切换是否渲染sim ore")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("toggle")
                            .bool()
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onOreRender)))
                    .complete();
        }

        public void onOreRender(ArgumentInputStream re) {
            boolean val = re.nextBoolean();
            seedOre.enableRender.set(val);
            Debug.chat("[种子矿透] 切换渲染:", val);
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("fakeore")
                    .helper("<operation> 进行假矿渲染切换")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("operation")
                            .select(List.of("on", "off", "reload"))
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onFakeOre)))
                    .complete();
        }

        public void onFakeOre(ArgumentInputStream re) {
            String val = re.nextNonnull();
            switch (val) {
                case "on" -> {
                    Debug.chat("[种子矿透] 切换假矿: true");
                    seedOre.enableFakeOres.set(true);
                }
                case "off" -> {
                    Debug.chat("[种子矿透] 切换假矿: false");
                    seedOre.enableFakeOres.set(false);
                }
                case "reload" -> {
                    Debug.chat("[种子矿透] 重载可视距离内的假矿");
                    seedOre.onReloadFakeOre();
                }
            }
            Config.launchSaveTasks();
        }
    }

    static {
        moduleGroup.registerFactories(MineTasks::initModules);
        HackModules.registerModuleGroup(moduleGroup);
        MainCommand.registerSubCommands("mine", SeedCommand::new);
    }
}
