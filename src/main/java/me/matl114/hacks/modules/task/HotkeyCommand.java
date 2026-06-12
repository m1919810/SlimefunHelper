package me.matl114.hacks.modules.task;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.matl114.commands.MainCommand;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.PrimitivePairList;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.IHotKey;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.managers.input.SimpleHotKey;
import me.matl114.managers.input.SimpleInputManager;
import me.matl114.utils.Debug;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

public class HotkeyCommand extends BaseModule {
    private static final String HOTKEY_PREFIX = "hotkey-command";

    private final Set<IHotKey> commandHotkeys = new LinkedHashSet<>();
    private final ModulePath root = makePath(Configs.MISC_CONFIG, HOTKEY_PREFIX);

    public final FlagRef enable = flagBuilder(root.addEnable()).defaultValue(true).build();

    public final NBTRef<PrimitivePairList<String, String>> commands = builder(
                    root.add("commands"), PrimitivePairList.<String, String>parameter())
            .defaultValue(new PrimitivePairList<>(NBTTypes.STRING_TYPE, NBTTypes.STRING_TYPE, List.of()))
            .updateListener(this::reloadBindings)
            .build();

    public HotkeyCommand() {
        super("HotkeyCommand");
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapHotkeyCommand);
        reloadBindings();
    }

    @Override
    @MustBeInvokedByOverriders
    public void onEnableModule() {
        super.onEnableModule();
        reloadBindings();
    }

    @Override
    @MustBeInvokedByOverriders
    public void onDisableModule() {
        super.onDisableModule();
        clearCommandHotkeys();
    }

    @Override
    @MustBeInvokedByOverriders
    public void onRemove() {
        clearCommandHotkeys();
        super.onRemove();
    }

    public void bootStrapHotkeyCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.subMainBuilder().name(HOTKEY_PREFIX).build();
        main.subBuilder(SubCommand.taskBuilder())
                .name("list")
                .helper("列出快捷键指令绑定")
                .post(e -> e.executor(CommandContext.execute(this::onList)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("add")
                .helper("<hotkey> <command...> 添加快捷键指令绑定")
                .arg(SimpleCommandArgs.argumentBuilder().name("hotkey").build())
                .post(e -> e.executor(this::onAdd))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("remove")
                .helper("<index> 删除快捷键指令绑定")
                .arg(SimpleCommandArgs.argumentBuilder().name("index").intValue().build())
                .post(e -> e.executor(CommandContext.execute(this::onRemoveBinding)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("run")
                .helper("<index> 立即执行一条快捷键指令")
                .arg(SimpleCommandArgs.argumentBuilder().name("index").intValue().build())
                .post(e -> e.executor(CommandContext.execute(this::onRun)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("clear")
                .helper("清空快捷键指令绑定")
                .post(e -> e.executor(CommandContext.run(this::onClear)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("reload")
                .helper("重载快捷键指令绑定")
                .post(e -> e.executor(CommandContext.run(this::onReload)))
                .complete();
    }

    private void reloadBindings(PrimitivePairList<String, String> ignored) {
        reloadBindings();
    }

    private void reloadBindings() {
        clearCommandHotkeys();
        if (enable == null || commands == null || !enable.get()) {
            return;
        }
        int index = 0;
        for (Pair<String, String> binding : commands.get().list()) {
            ++index;
            registerCommandHotkey(index, binding.getFirst(), binding.getSecond());
        }
    }

    private void registerCommandHotkey(int index, String rawCommand, String rawHotkey) {
        String command = normalize(rawCommand);
        String hotkey = normalize(rawHotkey);
        if (hotkey.isEmpty() || command.isEmpty()) {
            return;
        }
        try {
            MultiKeyBind keyBind = new MultiKeyBind(hotkey);
            if (keyBind.isEmpty()) {
                return;
            }
            SimpleHotKey simpleHotKey = new SimpleHotKey(new String[] {HOTKEY_PREFIX, String.valueOf(index)}, keyBind);
            simpleHotKey.setInputHandler(HotKeyUtils.wrapAsHandler(() -> runConfiguredCommand(command)));
            SimpleInputManager.getInstance().registerHotKeys(simpleHotKey);
            commandHotkeys.add(simpleHotKey);
        } catch (Throwable e) {
            Debug.info("Invalid hotkey-command binding", index, hotkey, command, e.getMessage());
        }
    }

    private void clearCommandHotkeys() {
        for (IHotKey hotkey : commandHotkeys) {
            SimpleInputManager.getInstance().unregisterHotKeys(hotkey);
        }
        commandHotkeys.clear();
    }

    private boolean runConfiguredCommand(String command) {
        String normalized = normalize(command);
        if (normalized.isEmpty()) {
            return false;
        }
        try {
            if (normalized.startsWith("/!!")) {
                MainCommand.dispatchClientCommand(normalized.substring(3));
            } else if (normalized.startsWith("!!")) {
                MainCommand.dispatchClientCommand(normalized.substring(2));
            } else {
                ChatTasks.sayMessage(normalized, false);
            }
            return true;
        } catch (Throwable e) {
            Debug.chat(Text.literal("快捷键指令执行失败: " + e.getMessage()).formatted(Formatting.RED));
            Debug.info(e);
            return false;
        }
    }

    private void onList(CommandExecution execution, ArgumentInputStream args) {
        List<Pair<String, String>> list = commands.get().list();
        execution.sendMessage(Text.literal("快捷键指令绑定: " + list.size() + " 条，已注册 " + commandHotkeys.size() + " 条")
                .formatted(Formatting.GREEN));
        for (int i = 0; i < list.size(); ++i) {
            Pair<String, String> binding = list.get(i);
            execution.sendMessage(Text.literal((i + 1) + ". " + normalize(binding.getFirst()) + " -> "
                    + normalize(binding.getSecond())));
        }
    }

    private boolean onAdd(CommandExecution execution, ArgumentInputStream args, ArgumentReader reader) {
        String hotkey = normalize(args.nextNonnullString());
        String command = normalize(reader.getRemainingArgStr());
        if (hotkey.isEmpty() || command.isEmpty()) {
            execution.sendMessage(Text.literal("快捷键和指令都不能为空").formatted(Formatting.RED));
            return true;
        }
        try {
            if (new MultiKeyBind(hotkey).isEmpty()) {
                execution.sendMessage(Text.literal("快捷键不能为空").formatted(Formatting.RED));
                return true;
            }
        } catch (Throwable e) {
            execution.sendMessage(Text.literal("无效快捷键: " + hotkey).formatted(Formatting.RED));
            return true;
        }
        List<Pair<String, String>> list = new ArrayList<>(commands.get().list());
        list.add(Pair.of(command, hotkey));
        setBindings(list);
        execution.sendMessage(Text.literal("已添加快捷键指令绑定: " + command + " -> " + hotkey).formatted(Formatting.GREEN));
        return true;
    }

    private void onRemoveBinding(CommandExecution execution, ArgumentInputStream args) {
        List<Pair<String, String>> list = new ArrayList<>(commands.get().list());
        int index = args.nextInt();
        if (index < 1 || index > list.size()) {
            execution.sendMessage(Text.literal("绑定序号不存在: " + index).formatted(Formatting.RED));
            return;
        }
        Pair<String, String> removed = list.remove(index - 1);
        setBindings(list);
        execution.sendMessage(Text.literal("已删除快捷键指令绑定: " + normalize(removed.getFirst()) + " -> "
                        + normalize(removed.getSecond()))
                .formatted(Formatting.GREEN));
    }

    private void onRun(CommandExecution execution, ArgumentInputStream args) {
        List<Pair<String, String>> list = commands.get().list();
        int index = args.nextInt();
        if (index < 1 || index > list.size()) {
            execution.sendMessage(Text.literal("绑定序号不存在: " + index).formatted(Formatting.RED));
            return;
        }
        Pair<String, String> binding = list.get(index - 1);
        if (runConfiguredCommand(binding.getFirst())) {
            execution.sendMessage(Text.literal("已执行快捷键指令: " + normalize(binding.getFirst()))
                    .formatted(Formatting.GREEN));
        }
    }

    private boolean onClear() {
        setBindings(List.of());
        Debug.chat(Text.literal("已清空快捷键指令绑定").formatted(Formatting.GREEN));
        return true;
    }

    private boolean onReload() {
        reloadBindings();
        Debug.chat(Text.literal("已重载快捷键指令绑定: " + commandHotkeys.size() + " 条")
                .formatted(Formatting.GREEN));
        return true;
    }

    private void setBindings(List<Pair<String, String>> list) {
        commands.set(new PrimitivePairList<>(NBTTypes.STRING_TYPE, NBTTypes.STRING_TYPE, list));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}