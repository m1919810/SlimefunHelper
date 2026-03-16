package me.matl114.hacks.modules.move;

import java.awt.*;
import java.util.List;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.commands.MainCommand;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import me.matl114.managers.task.RepeatTask;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.impl.DispatchArgumentType;
import me.matl114.utils.commands.params.impl.PosArgumentType;
import me.matl114.utils.commands.params.types.ExecutePos;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class TpaCommand extends BaseModule {
    public TpaCommand() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapTpaCommand);
    }

    public void bootStrapTpaCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.mainBuilder().name("tpa").build();
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("tp")
                    .helper("<x> <y> <z> [-far] 执行模拟tp行为")
                    .arg(SimpleCommandArgs.argumentBuilder(PosArgumentType::new)
                            .name("position")
                            .build())
                    .post(e -> e.executor(this::onTp))
                    .complete();
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("mark")
                    .helper("<type> [extra] 标注一个位置为临时缓存位置")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("type")
                            .select(List.of("player", "camera", "this", "pos", "target", "cross", "clear"), "camera")
                            .build())
                    .arg(new DispatchArgumentType<Object>("extra")
                            .registerArgumentDispatcher(
                                    0,
                                    "pos",
                                    SimpleCommandArgs.argumentBuilder(PosArgumentType::new)
                                            .name("dispatch_pos")
                                            .build())
                            .registerArgumentDispatcher(
                                    0,
                                    "target",
                                    SimpleCommandArgs.argumentBuilder(MovTasks.TpaArgumentType::new)
                                            .name("dispatch_tpa")
                                            .build())
                            .registerArgumentDispatcher(
                                    0,
                                    "player",
                                    SimpleCommandArgs.argumentBuilder()
                                            .name("dispatch_player")
                                            .tabSupplier(() -> EntityUtils.getWorldPlayerNames(false))
                                            .build())
                            .registerDispatcher(
                                    (p, args) -> true,
                                    SimpleCommandArgs.argumentBuilder()
                                            .name("dispatch_default")
                                            .build()))
                    .post(e -> e.executor(this::onMark))
                    .complete();
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("tpa")
                    .helper("<target> 传送到特殊目标位置")
                    .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaArgumentType::new)
                            .name("tpa_target")
                            .build())
                    .post(e -> e.executor(this::onTpa))
                    .complete();
        }
        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("travel")
                    .post(s -> s.subBuilder(SubCommand.taskBuilder())
                            .name("to")
                            .helper("<coord> 自动传送旅行")
                            .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                                    .name("target")
                                    .build())
                            .post(e -> e.executor(this::onTravelTo))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("cancel")
                            .helper("中断传送旅行")
                            .post(e -> e.executor(CommandContext.run(this::onTravelCancel)))
                            .complete())
                    .complete();
        }
    }

    public boolean onTp(CommandExecution p, ArgumentInputStream re, ArgumentReader reader) {
        ExecutePos executePos = re.nextArg();
        if (executePos != null) {
            Vector3d vector3d = executePos.getPosition(p);
            onTpa(new Vec3d(vector3d.x, vector3d.y, vector3d.z));
        } else {
            p.sendMessage("输入了无效坐标!");
        }
        return true;
    }

    public boolean onTpa(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
        ExecutePos pos = streamArgs.nextArg();
        if (pos != null) {
            Vector3d vector3d = pos.getPosition(var1);
            onTpa(new Vec3d(vector3d.x, vector3d.y, vector3d.z));
        } else {
            var1.sendMessage("输入了无效目标位置!");
        }
        return true;
    }

    public void onTpa(Vec3d pos) {
        MovTasks.executeTp(pos, 320, true, true);
    }

    public boolean onTravelTo(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
        ExecutePos pos = streamArgs.nextArg();
        if (pos != null) {
            Vector3d vector3d = pos.getPosition(var1);
            onTravel(var1.getExecutor(), new Vec3d(vector3d.x, vector3d.y, vector3d.z));
        }
        return true;
    }

    public void onTravel(PlayerEntity var1, Vec3d parsedCoord) {
        if (travelTask == null) {
            if (parsedCoord == null) return;
            travelTask = new RepeatTask(20, 2) {
                Vec3d pos0 = parsedCoord;
                final ClientPlayerEntity currentPlayer = mc.player;
                final long startingTime = System.currentTimeMillis();
                final Vec3d startPos = mc.player.getPos();

                public void cancel() {
                    super.cancel();
                    MovTasks.doingTp = false;
                }

                private boolean finish() {
                    if (travelTask != this
                            || mc.player != currentPlayer
                            || mc.player.getPos().subtract(pos0).horizontalLengthSquared() < 900) {
                        Debug.chat("当前travel task已完成或者终止");
                        long usedSec = (System.currentTimeMillis() - startingTime) / 1000L;
                        Debug.info("using time", usedSec);
                        if (mc.player != null) {
                            double len = mc.player.getPos().distanceTo(startPos);
                            Debug.chat("时间开销:", usedSec, "s, 运行距离: ", len, ", 平均速度: ", len / usedSec, "m/s");
                            // send signal to reset distance
                            mc.player.setOnGround(false);

                            ClientPlayerAccess.of(mc.player)
                                    .setForceNoFall(true); // .fallDistance = MovTasks.FORCE_RESET_DISTANCE;
                        }

                        travelTask = null;
                        cancel();
                        return true;
                    } else {
                        return false;
                    }
                }

                private boolean move(Vec3d delta) {

                    if (delta.length() == 0) {
                        MovTasks.moveToWithPackets(mc.player.getPos(), null);
                        return false;
                    } else {
                        MovTasks.moveToWithPackets(mc.player.getPos().add(delta), Boolean.TRUE);
                        return finish();
                    }
                }

                int tickCNT = 0;
                long lastTick;
                //                                Vec3d vec3d = Vec3d.ZERO;
                @Override
                public boolean runTask() {
                    if (mc.player == null) return false;
                    MovTasks.doingTp = false;
                    mc.player.setOnGround(false);
                    tickCNT += 1;
                    //                                    Debug.info("distance ", vec3d, mc.player.getPos());
                    if (mc.player.getY() < mc.world.getBottomY() + mc.world.getHeight() + 64) {
                        MovTasks.farawayMove(new Vec3d(0, 128, 0), true);
                    } else {
                        // fixme error in boat, desync boat position
                        Vec3d towards = pos0.subtract(mc.player.getPos());

                        Vec3d towardsHorizontal = new Vec3d(towards.x, 0, towards.z).normalize();
                        //                                            if(move(Vec3d.ZERO)){
                        //                                                return true;
                        //                                            }
                        if (move(towardsHorizontal.multiply(9.9).add(0, -0.3, 0))) {
                            return true;
                        }
                        if (move(towardsHorizontal.multiply(9.9).add(0, -0.3, 0))) {
                            return true;
                        }
                        if (tickCNT % 3 == 0) {
                            if (move(towardsHorizontal.multiply(9.9).add(0, -0.3, 0))) {
                                return true;
                            }
                        }
                    }
                    MovTasks.doingTp = true;
                    //                                    this.vec3d = mc.player.getPos();
                    return false;
                }
            };
            Tasks.scheduleTask(travelTask);
        } else {
            Debug.chat("上一个travel task仍旧在执行,使用travel cancel取消");
        }
    }

    public void onTravelCancel() {
        if (travelTask != null) {
            travelTask.cancel();
            travelTask = null;
        }
    }

    public static RepeatTask travelTask;

    public boolean onMark(CommandExecution var1, ArgumentInputStream re, ArgumentReader reader) {
        String type = re.nextNonnull();
        Vec3d pos;
        PlayerEntity sender = var1.getExecutorPlayer();
        switch (type) {
            case "this" -> pos = sender.getPos();
            case "camera" -> pos = RenderUtils.getCameraEntityPos();
            case "cross" -> pos = mc.crosshairTarget.getPos();
            case "player" -> {
                String var = re.nextNonnull();
                Entity player = EntityUtils.getPlayerByName(var);
                if (player != null) {
                    pos = player.getPos();
                } else {
                    var1.sendMessage(Text.literal("找不到实体或者玩家: " + var).formatted(Formatting.RED));
                    return true;
                }
            }
            case "pos" -> {
                ExecutePos executePos = re.nextArg();
                if (executePos != null) {
                    var vcd3 = executePos.getPosition(var1);
                    pos = new Vec3d(vcd3.x, vcd3.y, vcd3.z);
                } else {
                    var1.sendMessage(Text.literal("无效的坐标").formatted(Formatting.RED));
                    return true;
                }
            }
            case "target" -> {
                ExecutePos executePos = re.nextArg();
                if (executePos != null) {
                    var vcd3 = executePos.getPosition(var1);
                    pos = new Vec3d(vcd3.x, vcd3.y, vcd3.z);
                } else {
                    var1.sendMessage(Text.literal("无效的特殊位置").formatted(Formatting.RED));
                    return true;
                }
            }
            case "clear" -> {
                MovTasks.MARK = null;
                return true;
            }
            default -> {
                var1.sendMessage(Text.literal("不存在的mark类型: " + type).formatted(Formatting.RED));
                return true;
            }
        }
        MovTasks.MARK = pos;
        Debug.chat("标记成功: ", ChatUtils.getDisplayedLocationDouble(pos));
        RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                        new RenderTasks.BoxObject(sender.dimensions.getBoxAt(MovTasks.MARK), Color.GREEN))
                .setAutoStop(() -> MovTasks.MARK != pos));
        return true;
    }
}
