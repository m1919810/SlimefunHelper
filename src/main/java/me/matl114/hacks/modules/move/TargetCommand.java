package me.matl114.hacks.modules.move;

import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.Map;
import me.matl114.commands.MainCommand;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.types.ExecutePos;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class TargetCommand extends BaseModule {
    public TargetCommand() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapTargetCommand);
    }

    Map<String, Pair<Vec3d, Float>> lastCachedPosition = new HashMap<>();

    public void bootStrapTargetCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.mainBuilder().name("target").build();
        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("calculate")
                    .post(m -> m.subBuilder(SubCommand.taskBuilder())
                            .name("waypoint")
                            .helper("<waypoint> 计算路径点坐标")
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("waypoint")
                                    .tabSupplier(WorldUtils::getWaypointNames)
                                    .build())
                            .post(s -> s.executor(CommandContext.execute(this::onCalculateWaypoint)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("pos")
                            .helper("<坐标点> 计算位置相关数据")
                            .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                                    .name("target")
                                    .build())
                            .post(s -> s.executor(CommandContext.execute(this::onCalculatePosition)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("chunk")
                            .helper("<x> <z> 计算区块坐标")
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("z")
                                    .intValue()
                                    .build())
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("x")
                                    .intValue()
                                    .build())
                            .post(s -> s.executor(CommandContext.execute(this::onCalculateChunk)))
                            .complete())
                    .complete();
        }
        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("target")
                    .post(m -> m.subBuilder(SubCommand.taskBuilder())
                            .name("waypoint")
                            .helper("<waypoint> 转动视角朝向该路径点")
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("waypoint")
                                    .tabSupplier(WorldUtils::getWaypointNames)
                                    .build())
                            .post(s -> s.executor(CommandContext.execute(this::onTargetWaypoint)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("pos")
                            .helper("<坐标点> 转动视角朝向坐标点")
                            .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                                    .name("target")
                                    .build())
                            .post(s -> s.executor(CommandContext.execute(this::onTargetPosition)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("chunk")
                            .helper("<x> <z> 计算区块坐标")
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("z")
                                    .intValue()
                                    .build())
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("x")
                                    .intValue()
                                    .build())
                            .post(s -> s.executor(CommandContext.execute(this::onTargetChunk)))
                            .complete())
                    .complete();
        }
    }

    public void onCalculateWaypoint(CommandExecution p, ArgumentInputStream re) {
        String waypoint = re.nextNonnullString();
        WorldUtils.Waypoint waypointIns = WorldUtils.getWaypoint(waypoint);
        if (waypointIns == null) {
            p.sendMessage(Text.literal("找不到这个坐标点").formatted(Formatting.RED));
        } else {
            WorldUtils.WaypointData waypointData = waypointIns.getData();
            if (waypointData instanceof WorldUtils.WaypointData.Direction dir) {
                if (lastCachedPosition.containsKey(waypointIns.getDisplayName())) {
                    Pair<Vec3d, Float> position = lastCachedPosition.remove(waypointIns.getDisplayName());
                    Vec3d pos = position.getFirst();
                    float direction = position.getSecond();
                    Vec3d currentPos = mc.player.getPos();
                    float currentDirection = dir.azimuth();
                    Vec2f vec2f = intersectRays(pos, direction, currentPos, currentDirection);
                    if (vec2f != null) {
                        p.sendMessage(Text.literal("计算当前坐标点位置大致位于: ")
                                .append(ChatUtils.getDisplayedLocation(new Vec3d(vec2f.x, 64, vec2f.y))));
                    } else {
                        p.sendMessage(Text.literal("当前位置无法正确推断,请重新选去两点"));
                    }
                } else {
                    lastCachedPosition.put(waypointIns.getDisplayName(), Pair.of(mc.player.getPos(), dir.azimuth()));
                    p.sendMessage(Text.literal("记录当前测算位置中,请移动若干位置后重新输入指令").formatted(Formatting.GREEN));
                }
            } else {
                p.sendMessage(Text.literal("当前坐标点已有确定坐标").formatted(Formatting.GREEN));
                if (waypointData instanceof WorldUtils.WaypointData.Pos pos) {
                    p.sendMessage(Text.literal("Pos: ").append(ChatUtils.getDisplayedLocation(pos.pos())));
                } else if (waypointData instanceof WorldUtils.WaypointData.Chunk chunk) {
                    ChunkPos chunkPos = chunk.pos();
                    Vec3d pos = new Vec3d(chunkPos.x << 4, 64, chunkPos.z << 4);
                    p.sendMessage(Text.literal("Pos: ").append(ChatUtils.getDisplayedLocation(pos)));
                }
            }
        }
    }

    public void onCalculatePosition(CommandExecution p, ArgumentInputStream re) {
        ExecutePos pos = re.nextArg();
        if (pos != null) {
            Vector3d vector3d = pos.getPosition(p);
            Vec3d vec3d = (new Vec3d(vector3d.x, vector3d.y, vector3d.z));
            p.sendMessage(Text.literal("Pos: ").append(ChatUtils.getDisplayedLocation(vec3d)));
            p.sendMessage(
                    Text.literal("NetherPos: ").append(ChatUtils.getDisplayedLocation(vec3d.multiply((double) 1 / 8))));
            p.sendMessage(Text.literal("WorldPos: ").append(ChatUtils.getDisplayedLocation(vec3d.multiply(8))));
            BlockPos blockPos = BlockPos.ofFloored(vec3d);
            p.sendMessage(Text.literal("ChunkPos: ")
                    .append(ChatUtils.getDisplayedLocation(blockPos.getX() >> 4, blockPos.getZ() >> 4)));
        } else {
            p.sendMessage("输入了无效坐标!");
        }
    }

    public void onCalculateChunk(CommandExecution p, ArgumentInputStream re) {
        int x = re.nextInt();
        int z = re.nextInt();
        p.sendMessage(Text.literal("ChunkPos: %d %d".formatted(x, z)));
        p.sendMessage(Text.literal("Pos: ").append(ChatUtils.getDisplayedLocation(x << 4, z << 4)));
    }

    public void onTargetWaypoint(CommandExecution p, ArgumentInputStream re) {
        String waypoint = re.nextNonnullString();
        WorldUtils.Waypoint waypointIns = WorldUtils.getWaypoint(waypoint);
        if (waypointIns == null) {
            p.sendMessage(Text.literal("找不到这个坐标点").formatted(Formatting.RED));
        } else {
            var waypointData = waypointIns.getData();
            if (waypointData instanceof WorldUtils.WaypointData.Pos pos) {
                Vec3d rotate = pos.pos().subtract(mc.player.getEyePos()).normalize();
                EntityUtils.setEntityRotationSafe(mc.player, rotate);
            } else if (waypointData instanceof WorldUtils.WaypointData.Chunk chunk) {
                ChunkPos chunkPos = chunk.pos();
                Vec2f rotate = new Vec2f(
                        (float) ((chunkPos.x << 4) - mc.player.getX()), (float) ((chunkPos.z << 4) - mc.player.getZ()));
                EntityUtils.setEntityYawSafe(mc.player, rotate);
            } else if (waypointData instanceof WorldUtils.WaypointData.Direction direction) {
                EntityUtils.setEntityYawSafe(mc.player, (float) direction.azimuth() * 57.29578f);
            }
        }
    }

    public void onTargetPosition(CommandExecution p, ArgumentInputStream re) {
        ExecutePos pos = re.nextArg();
        if (pos != null) {
            Vector3d vector3d = pos.getPosition(p);
            Vec3d target = new Vec3d(
                            vector3d.x - mc.player.getX(), vector3d.y - mc.player.getY(), vector3d.z - mc.player.getZ())
                    .normalize();
            EntityUtils.setEntityRotationSafe(mc.player, target);
        } else {
            p.sendMessage("输入了无效坐标!");
        }
    }

    public void onTargetChunk(CommandExecution p, ArgumentInputStream re) {
        int x = re.nextInt();
        int z = re.nextInt();
        Vec2f rotate = new Vec2f((float) ((x << 4) - mc.player.getX()), (float) ((z << 4) - mc.player.getZ()));
        EntityUtils.setEntityYawSafe(mc.player, rotate);
    }

    public static Vec2f intersectRays(Vec3d posA, float azimuthA, Vec3d posB, float azimuthB) {
        double x1 = posA.x, z1 = posA.z;
        double x2 = posB.x, z2 = posB.z;
        double sinA = Math.sin(azimuthA);
        double cosA = Math.cos(azimuthA);
        double sinB = Math.sin(azimuthB);
        double cosB = Math.cos(azimuthB);

        double d1x = sinA;
        double d1z = -cosA;
        double d2x = sinB;
        double d2z = -cosB;

        double dx = x2 - x1;
        double dz = z2 - z1;

        double det = d1x * d2z - d1z * d2x;
        if (det == 0) {
            return null; // 平行，无交点
        }

        double t = (dx * d2z - dz * d2x) / det;

        double ix = x1 + t * d1x;
        double iz = z1 + t * d1z;
        return new Vec2f((float) ix, (float) iz);
    }
}
