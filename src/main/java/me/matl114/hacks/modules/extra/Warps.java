package me.matl114.hacks.modules.extra;

import com.mojang.serialization.JavaOps;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.FileManager;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.TabResult;
import me.matl114.utils.commands.params.types.ExecutePos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.Vector3d;

public class Warps extends BaseModule {
    private static final String SPLITTER = "|";
    private static final String SPLITTER_REGEX = "\\|";
    private static final String SAVE_KEY = "warp-entries";
    private final FileStorage saveMap = FileManager.getInstance().getInternalStorage("warp-saves.nbt");

    // todo: add render settings, add auto create settings
    public final List<String> cachedString = new ArrayList<>();
    public final Map<String, Map<String, Map<String, Vec3d>>> parseVec3ds = new LinkedHashMap<>();

    {
        cachedString.clear();
        parseVec3ds.clear();
        Map<String, List<String>> mp = saveMap.asReadOnly(JavaOps.INSTANCE);
        // copy to avoid cmd
        for (String path : List.copyOf(mp.getOrDefault(SAVE_KEY, List.of()))) {
            String[] splits = path.split(SPLITTER_REGEX);
            Vec3d pos;
            String serverName;
            String worldName;
            String warpName;
            try {
                String posStr = splits[splits.length - 1];
                long longValue = Long.parseLong(posStr);
                BlockPos blockPos = BlockPos.fromLong(longValue);
                pos = new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                serverName = splits[0];
                worldName = splits[1];
                String[] remainName = new String[splits.length - 3];
                System.arraycopy(splits, 2, remainName, 0, remainName.length);
                warpName = String.join("|", remainName);
            } catch (Throwable e) {
                continue;
            }
            // build index
            putInternal(serverName, worldName, warpName, pos);
        }
    }

    private void onCacheUpdate() {
        Map<String, List<String>> map = Map.of(SAVE_KEY, cachedString);
        this.saveMap.write(map, JavaOps.INSTANCE);
    }

    private boolean putInternal(String serverName, String worldName, String warpName, Vec3d pos) {
        if (isInRange(pos)) {
            removeInternal(serverName, worldName, warpName);
            parseVec3ds
                    .computeIfAbsent(serverName, k -> new LinkedHashMap<>())
                    .computeIfAbsent(worldName, k -> new LinkedHashMap<>())
                    .put(warpName, pos);
            cachedString.add(String.join(SPLITTER, serverName, worldName, warpName, Long.toString(toRangedVec(pos))));
            onCacheUpdate();
            return true;
        }
        return false;
    }

    private boolean removeInternal(String serverName, String worldName, String warpName) {
        Object val = computeEmpty(parseVec3ds, new String[] {serverName, worldName, warpName}, 0);
        if (val != null) {
            String name = String.join(SPLITTER, serverName, worldName, warpName) + SPLITTER;
            if (val instanceof Vec3d vc3d) {
                name += Long.toString(toRangedVec(vc3d));
            }
            boolean var = cachedString.remove(name);
            onCacheUpdate();
            return var;
        }
        return false;
    }

    private Object computeEmpty(Map map, String[] k, int idx) {
        if (idx == k.length - 1) {
            return map.remove(k[idx]);
        } else {
            MutableObject ref = new MutableObject();
            map.compute(k[idx], (ks, v) -> {
                if (v instanceof Map mp && !mp.isEmpty()) {
                    ref.setValue(computeEmpty(mp, k, idx + 1));
                    if (!mp.isEmpty()) {
                        return mp;
                    }
                }
                return null;
            });
            return ref.getValue();
        }
    }

    public Warps() {
        super("Warps");
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getCustomListener().getChannel(MovTasks.TpaCommandEvent.class), this::onTpaToWarp);
        registerCommandBootstrap(this::warpCommandBootStrap);
    }

    public boolean isValidWarpName(String name) {
        return !name.contains(" ") && !name.contains(SPLITTER);
    }

    public static Optional<String> getCurrentWorldName() {
        if (mc.world != null) {
            return Optional.of(mc.world.getRegistryKey().getValue().toString());
        }
        return Optional.empty();
    }

    public boolean registerWarp(String world, String name, Vec3d pos) {
        if (isValidWarpName(name)) {
            String serverName = CommonUtils.getServerName();
            return putInternal(serverName, world, name, pos);
        }
        return false;
    }

    public boolean unregisterWarp(String world, String name) {
        String serverName = CommonUtils.getServerName();
        return removeInternal(serverName, world, name);
    }

    @Nonnull
    public Map<String, Vec3d> getWorldWarps(String world) {
        String serverName = CommonUtils.getServerName();
        return parseVec3ds.getOrDefault(serverName, Map.of()).getOrDefault(world, Map.of());
    }

    @Nonnull
    public Map<String, Map<String, Vec3d>> getServerWarps(String serverName) {
        return parseVec3ds.getOrDefault(serverName, Map.of());
    }

    public Optional<Map<String, Vec3d>> getCurrentWorldWarps() {
        return getCurrentWorldName().map(this::getWorldWarps);
    }

    public Stream<String> getCurrentWorldWarpName() {
        return getCurrentWorldWarps().map(Map::keySet).stream().flatMap(Collection::stream);
    }

    public boolean isInRange(Vec3d pos) {
        BlockPos blockpos = BlockPos.ofFloored(pos);
        return Math.abs(blockpos.getX()) < (1 << 25)
                && Math.abs(blockpos.getY()) < (1 << 11)
                && Math.abs(blockpos.getZ()) < (1 << 25);
    }

    public long toRangedVec(Vec3d vec3d) {
        BlockPos pos = BlockPos.ofFloored(vec3d);
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }
    // all display and used name should be append a $
    public static final String PREFIX_WARP = "$";

    private final ArgumentType<String> warpArgumentType = SimpleCommandArgs.argumentBuilder()
            .name("warp_name")
            .tabSupplier(() -> getCurrentWorldWarps().map(Map::keySet).stream()
                    .flatMap(Collection::stream)
                    .map(s -> PREFIX_WARP + s))
            .build();

    public void onTpaToWarp(Event<EventContainer<MovTasks.TpaCommandEvent>> tpaRequest) {
        MovTasks.TpaCommandEvent event = tpaRequest.context.getValue();
        switch (event.mode) {
            case TAB -> {
                var tabResult = warpArgumentType.getTab(event.player, event.inputs);
                if (tabResult != null) {
                    tabResult.forEach(((MovTasks.TpaTabCompletor) event).tab::add);
                }
            }
            case RESOLVE -> {
                MovTasks.TpaPositionResolver resolver = (MovTasks.TpaPositionResolver) event;
                if (resolver.hasResolved()) return;
                ArgumentReader reader = resolver.arguments;
                if (reader.hasNext()) {
                    String type = reader.peek();
                    if (type.startsWith(PREFIX_WARP)) {
                        // consume this argument
                        reader.step();
                        String val = type.substring(1);
                        Vec3d lookup = getCurrentWorldWarps().get().get(val);
                        if (lookup != null) {
                            resolver.errMsg.accept(
                                    Text.literal("使用传送点 " + type + " ").append(ChatUtils.getDisplayedLocation(lookup)));
                            resolver.resolve = Optional.of(lookup);
                        } else {
                            resolver.errMsg.accept(
                                    Text.literal("不存在这样的传送点: " + type).formatted(Formatting.RED));
                            resolver.resolve = Optional.empty();
                        }
                    }
                }
            }
        }
    }

    public void warpCommandBootStrap(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.mainBuilder().name("warp_command").build();
        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("warp")
                    .post(m -> m.subBuilder(SubCommand.taskBuilder())
                            .name("list")
                            .helper("message.command.warp_command.warp.list.help")
                            .arg(me.matl114.utils.commands.params.SimpleCommandArgs.argumentBuilder()
                                    .name("world")
                                    .tabSupplier(() -> mc.getNetworkHandler().getWorldKeys().stream()
                                            .map(RegistryKey::getValue)
                                            .map(Identifier::toString))
                                    .build())
                            .post(e -> e.executor(CommandContext.run(this::onList)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("listall")
                            .helper("message.command.warp_command.warp.listall.help")
                            .arg(me.matl114.utils.commands.params.SimpleCommandArgs.argumentBuilder()
                                    .name("server")
                                    .tabSupplier(() -> parseVec3ds.keySet().stream())
                                    .build())
                            .post(e -> e.executor(CommandContext.run(this::onListAll)))
                            .complete())
                    .complete();
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("setwarp")
                    .helper("message.command.warp_command.setwarp.help")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("warpname")
                            .select("<输入自定义名称>")
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                            .name("warppos")
                            .build())
                    .post(e -> e.executor(this::onSet))
                    .complete();
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("delwarp")
                    .helper("message.command.warp_command.delwarp.help")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("warpname")
                            .tabCompletor(TabResult.ofStreamSupplier(this::getCurrentWorldWarpName))
                            .build())
                    .post(e -> e.executor(this::onRemove))
                    .complete();
        }
    }

    private boolean onSet(CommandExecution context, ArgumentInputStream re, ArgumentReader rest) {
        String warpName = re.nextNonnull();
        ExecutePos warpPos = re.nextArg();
        Vector3d vector3d;
        if (warpPos == null) {
            vector3d = context.getExecutePos();
        } else {
            vector3d = warpPos.getPosition(context);
        }
        Vec3d vec3d = new Vec3d(vector3d.x, vector3d.y, vector3d.z);

        if (mc.world != null && registerWarp(getCurrentWorldName().get(), warpName, vec3d)) {
            context.sendMessage("&a注册传送点 " + warpName + " 成功");
            context.sendMessage(
                    Text.literal("位置: ").formatted(Formatting.GREEN).append(ChatUtils.getDisplayedLocation(vec3d)));
        } else {
            context.sendMessage("&c注册传送点失败!");
        }

        return true;
    }

    private boolean onRemove(CommandExecution context, ArgumentInputStream re, ArgumentReader rest) {
        String warpName = re.nextNonnull();
        if (mc.world != null && unregisterWarp(getCurrentWorldName().get(), warpName)) {
            context.sendMessage("&a移除传送点 " + warpName + " 成功");
        } else {
            context.sendMessage("&c移除传送点失败");
        }
        return true;
    }

    private void onList(ArgumentInputStream re) {
        String worldName = re.nextArgOrDefault(getCurrentWorldName()::get);
        Debug.chat(Text.literal("==".repeat(10)).formatted(Formatting.GREEN));
        Debug.chat(Text.literal("== 当前世界" + worldName + "传送点列表 ==").formatted(Formatting.GREEN));
        var s = getWorldWarps(worldName);
        int i = 0;
        for (var entry : s.entrySet()) {
            ++i;
            Debug.chat(
                    Text.literal(i + ":").formatted(Formatting.YELLOW),
                    ChatUtils.stringToText(PREFIX_WARP + entry.getKey()),
                    " ",
                    ChatUtils.getDisplayedLocation(entry.getValue()));
        }
        Debug.chat();
        Debug.chat(Text.literal("==".repeat(10)).formatted(Formatting.GREEN));
    }

    private void onListAll(ArgumentInputStream re) {
        String serverName = re.nextArgOrDefault(CommonUtils::getServerName);
        Debug.chat(Text.literal("==".repeat(10)).formatted(Formatting.GREEN));
        Debug.chat(Text.literal("== 当前服务器" + serverName + "传送点列表 ==").formatted(Formatting.GREEN));
        var s = getServerWarps(serverName);
        int i = 0;
        for (var entry : s.entrySet()) {
            for (var entry2 : entry.getValue().entrySet()) {
                ++i;
                Debug.chat(
                        Text.literal(i + ":").formatted(Formatting.YELLOW),
                        entry.getKey(),
                        ChatUtils.stringToText(PREFIX_WARP + entry2.getKey()),
                        " ",
                        ChatUtils.getDisplayedLocation(entry2.getValue()));
            }
        }
        Debug.chat();
        Debug.chat(Text.literal("==".repeat(10)).formatted(Formatting.GREEN));
    }
}
