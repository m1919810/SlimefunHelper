package me.matl114.hacks.modules.task;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.PrimitivePairList;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.*;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class BindCommand extends BaseModule implements IHotKey {
    private static final String HOTKEY_PREFIX = "bind-command";
    public static BindCommand INSTANCE;
    private final ModulePath root = makePath(Configs.MISC_CONFIG, HOTKEY_PREFIX);

    public final FlagRef enable =
            builder(root.addEnable(), Boolean.class).defaultValue(true).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();
    private final List<String> ARGUMENTS = List.of("player", "pos", "x", "y", "z", "world", "pitch", "yaw");
    public final NBTRef<PrimitivePairList<MultiKeyBind, StringFormat>> commands;

    {
        ModulePath path = root.add("commands");
        NBTRef<?> oldRef = null;
        if (path.getConfig().get(path.toPath()) instanceof NBTRef nbt) {
            oldRef = nbt;
        }
        commands = builder(path, PrimitivePairList.<MultiKeyBind, StringFormat>parameter())
                .defaultValue(new PrimitivePairList<>(
                        "widget.bind-command.hotkey",
                        "widget.bind-command.command",
                        NBTTypes.KEY_BIND_TYPE,
                        NBTTypes.STRING_FORMAT_TYPE,
                        Optional.empty(),
                        Optional.of(new StringFormat(ARGUMENTS, "")),
                        List.of(Pair.of(new MultiKeyBind(), new StringFormat(ARGUMENTS, "/!!help")))))
                .build();
        if (oldRef != null
                && oldRef.get() instanceof PrimitivePairList pairList
                && pairList.firstType() == NBTTypes.KEY_BIND_TYPE
                && pairList.secondType() == NBTTypes.STRING_TYPE) {
            commands.set(new PrimitivePairList<>(
                    "widget.bind-command.hotkey",
                    "widget.bind-command.command",
                    NBTTypes.KEY_BIND_TYPE,
                    NBTTypes.STRING_FORMAT_TYPE,
                    Optional.empty(),
                    Optional.of(new StringFormat(ARGUMENTS, "")),
                    ((PrimitivePairList<MultiKeyBind, String>) pairList)
                            .list().stream()
                                    .map(s -> Pair.of(s.getFirst(), new StringFormat(ARGUMENTS, s.getSecond())))
                                    .toList()));
        }
    }

    public BindCommand() {
        super("BindCommand");
        INSTANCE = this;
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        SimpleInputManager.getInstance().registerHotKeys(this);
        registerCommandBootstrap(this::registerBindCommandSetup);
    }

    public void unregisterAll() {
        super.unregisterAll();
        SimpleInputManager.getInstance().unregisterHotKeys(this);
    }

    public void registerBindCommandSetup(MainCommand command) {
        TreeSubCommand main = command.subMainBuilder().name("bindc").build();
        main.subBuilder(SubCommand.taskBuilder())
                .name("opengui")
                .helper("message.command.bindc.opengui.help")
                .post(e -> e.executor(CommandContext.run(this::openGui)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("list")
                .helper("message.command.bindc.list.help")
                .post(e -> e.executor(CommandContext.execute(this::listBindings)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("help")
                .helper("message.command.bindc.help.help")
                .post(e -> e.executor(CommandContext.execute(this::showBindCommandHelp)))
                .complete();
    }

    private boolean openGui() {
        return true;
    }

    private void listBindings(CommandExecution execution, ArgumentInputStream args) {
        List<Pair<MultiKeyBind, StringFormat>> list = commands.get().list();
        execution.sendMessage(Text.literal("bindc 当前绑定: " + list.size() + " 条").formatted(Formatting.GREEN));
        for (int i = 0; i < list.size(); ++i) {
            Pair<MultiKeyBind, StringFormat> binding = list.get(i);
            MultiKeyBind hotkey = binding.getFirst();
            String hotkeyText = hotkey == null || hotkey.isEmpty() ? "<empty>" : hotkey.asString();
            execution.sendMessage(Text.literal(
                    (i + 1) + ". " + hotkeyText + " -> " + binding.getSecond().formatString()));
        }
    }

    private void showBindCommandHelp(CommandExecution execution, ArgumentInputStream args) {
        execution.sendMessage(Text.literal("BindCommand 模块说明").formatted(Formatting.GREEN));
        execution.sendMessage(Text.literal("该模块用于把自定义快捷键绑定到聊天文本、服务端指令或客户端指令。"));
        execution.sendMessage(Text.literal("触发已配置的快捷键时，会自动发送对应内容。"));
    }

    @Override
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged, boolean isClicked) {
        boolean handled = false;
        if (isStateChanged && isClicked) {
            if (checkNull()) {
                return handled;
            }
            for (var lst : commands.get().list()) {
                var mul = lst.getFirst();
                if (!mul.isEmpty() && keyCode == mul.getLastKey() && mul.isAllPressed()) {
                    Event<IHotKey> hotKeyEvent = new Event<>(this, true, false, manager);
                    Listener.getHotKeyTriggeredListener().handleValue(hotKeyEvent);
                    if (hotKeyEvent.isCancelled()) {
                        continue;
                    }
                    if (HotKeyUtils.isValidState()) {
                        handleCommand(lst.getSecond());
                    }
                    if (mul.isToggleOnRelease()) {
                        Tasks.scheduleRepeatedPre(
                                () -> {
                                    if (!mul.isAllPressed()) {
                                        if (HotKeyUtils.isValidState()) {
                                            handleCommand(lst.getSecond());
                                        }
                                        return true;
                                    }
                                    return false;
                                },
                                1,
                                1);
                    }
                    handled |= !mul.isAllowVanilla();
                }
            }
        }
        return handled;
    }

    private void handleCommand(StringFormat string) {
        ChatTasks.sayMessage(
                string.format(Map.of(
                        "player", mc.player.getNameForScoreboard(),
                        "pos", "%.2f %.2f %.2f".formatted(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                        "x", "%.2f".formatted(mc.player.getX()),
                        "y", "%.2f".formatted(mc.player.getY()),
                        "z", "%.2f".formatted(mc.player.getZ()),
                        "pitch", "%.2f".formatted(mc.player.getPitch()),
                        "yaw", "%.2f".formatted(mc.player.getYaw()),
                        "world", mc.world.getRegistryKey().getValue().getPath())),
                false);
    }

    @Override
    public String getIdentifier() {
        return "custom.module.bind-command";
    }

    private static final IntList ALL_KEYCODES = new IntArrayList();

    static {
        ALL_KEYCODES.addAll(KeyCode.getKeyMap().values());
    }

    @Override
    public IntList getRelatedKeyCode() {
        return ALL_KEYCODES;
    }

    @Override
    public void addRegisteredManager(IInputManager manager) {}
}
