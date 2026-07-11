package me.matl114.hacks.modules.task;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.PrimitivePairList;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
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

    private final ModulePath root = makePath(Configs.MISC_CONFIG, HOTKEY_PREFIX);

    public final NBTRef<PrimitivePairList<MultiKeyBind, String>> commands = builder(
                    root.add("commands"), PrimitivePairList.<MultiKeyBind, String>parameter())
            .defaultValue(new PrimitivePairList<>(
                    "widget.bind-command.hotkey",
                    "widget.bind-command.command",
                    NBTTypes.KEY_BIND_TYPE,
                    NBTTypes.STRING_TYPE,
                    List.of(Pair.of(new MultiKeyBind(), "/!!help"))))
            .build();

    public BindCommand() {}

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
                .helper("打开快捷键指令配置界面")
                .post(e -> e.executor(CommandContext.run(this::openGui)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("list")
                .helper("列出当前快捷键指令绑定")
                .post(e -> e.executor(CommandContext.execute(this::listBindings)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("help")
                .helper("显示 bindc 指令帮助")
                .post(e -> e.executor(CommandContext.execute(this::showBindCommandHelp)))
                .complete();
    }

    private boolean openGui() {
        return true;
    }

    private void listBindings(CommandExecution execution, ArgumentInputStream args) {
        List<Pair<MultiKeyBind, String>> list = commands.get().list();
        execution.sendMessage(Text.literal("bindc 当前绑定: " + list.size() + " 条").formatted(Formatting.GREEN));
        for (int i = 0; i < list.size(); ++i) {
            Pair<MultiKeyBind, String> binding = list.get(i);
            MultiKeyBind hotkey = binding.getFirst();
            String hotkeyText = hotkey == null || hotkey.isEmpty() ? "<empty>" : hotkey.asString();
            execution.sendMessage(Text.literal((i + 1) + ". " + hotkeyText + " -> " + binding.getSecond()));
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
            for (var lst : commands.get().list()) {
                var mul = lst.getFirst();
                if (!mul.isEmpty() && keyCode == mul.getLastKey() && mul.isAllPressed()) {
                    Event<IHotKey> hotKeyEvent = new Event<>(this, true, false, manager);
                    Listener.getHotKeyTriggeredListener().handleValue(hotKeyEvent);
                    if (hotKeyEvent.isCancelled()) {
                        continue;
                    }
                    handleCommand(lst.getSecond());
                    if (mul.isToggleOnRelease()) {
                        Tasks.scheduleRepeatedPre(
                                () -> {
                                    if (!mul.isAllPressed()) {
                                        handleCommand(lst.getSecond());
                                        return true;
                                    }
                                    return false;
                                },
                                1,
                                1);
                    }
                    handled = true;
                }
            }
        }
        return handled;
    }

    private void handleCommand(String string) {
        ChatTasks.sayMessage(string, false);
    }

    @Override
    public String getIdentifier() {
        return "custom.module.bind-command";
    }

    private static final IntList ALL_KEYCODES = new IntArrayList();

    {
        ALL_KEYCODES.addAll(KeyCode.getKeyMap().values());
    }

    @Override
    public IntList getRelatedKeyCode() {
        return ALL_KEYCODES;
    }

    @Override
    public void addRegisteredManager(IInputManager manager) {}
}
