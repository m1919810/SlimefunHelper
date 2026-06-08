---
name: base-module-register-all
description: 为 SlimefunHelper 新模块补全 BaseModule 基础注册骨架。用于创建或改造模块时，统一完成模块名、ModulePath、Flag 绑定、不同类别 Ref、监听器、常用监听器、热键和命令注册。
disable-model-invocation: true
---

# base-module-register-all

## 目标

为一个 `extends BaseModule` 的模块一次性补齐基础注册结构，保持这几个面一致：

- 模块命名与 `ModulePath`
- 启用开关 `FlagRef` 与 `bindFlag`
- 向ModuleList注册可显示的模块
- 常见配置项注册
- 事件监听注册
- 命令注册
- 生命周期放置位置

## 核心原则

所有可注册对象都走 `BaseModule` 提供的方法，不要绕开。

必须遵守：

- 事件监听放在 `registerAll()`
- 命令注册放在 `registerAll()`
- 所有监听器和命令Bootstrap均为本模块的方法化为的lambda
- 所有监听器和命令必须要通过BaseModule中的指定方法注册
- 所有配置项必须通过BaseModule中的指定方法创建builder注册
- 模块启用状态通过在<init>中创建 `bindFlag(enable)` 绑定
- 运行时状态初始化放 `onEnableModule()`
- 需要主动清理的任务或外部状态放 `onDisableModule()`

## 最小骨架

```java
public class MyModule extends BaseModule {
    public static MyModule INSTANCE;

    public final ModulePath module = makePath(Configs.EXTRA_CONFIG, "my-module");

    public MyModule() {
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(module.add("enable")).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public void onEnableModule() {}

    @Override
    public void onDisableModule() {}
}
```

## 模块名与路径

### 模块显示名

有显示名需求(默认没有)时用构造器：

```java
super("Config");
super("Printer");
super("BeaconPlus");
```

否则直接默认类名。

### 根路径
ModulePath集成了Config和Path，指定了保存路径

优先先抽一个 `ModulePath` 根，再从根派生：

```java
public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "attack");
public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
```

子项统一通过根项追加生成：

```java
attack.add("whitelist")
moveSafety.add("auto-resync-pos")
```

## Flag 绑定

如果该模块希望可以在菜单中快捷启用/关闭， 则其需要指定一个 enable flag，并在构造器中绑定：

```java
public final FlagRef enable = flagBuilder(module.add("enable")).build();

public MyModule() {
    bindFlag(enable);
}
```

这类模型通常只有一个主要功能

## Ref(配置项) 注册套路

### FlagRef

```java
// 默认值为false，不可修改
public final FlagRef enable = flagBuilder(module.add("enable")).build();

public final FlagRef log = builder(module.add("log"), Boolean.class)
        .defaultValue(true)
        .build();
```

### IntRef

```java
public final IntRef delay = intBuilder(module.add("delay"))
        .defaultValue(5)
        .validator(Configs.INT_POSITIVE)
        .build();
```

### DoubleRef

```java
public final DoubleRef threshold = doubleBuilder(module.add("threshold"))
        .defaultValue(0.5D)
        .validator(Configs.doubleRange(0.0, 1.0))
        .build();
```

### StringRef

```java
public final StringRef regex = builder(module.add("regex"), StringRef.TYPE)
        .defaultValue("^(.*)$")
        .validator(Configs.REGEX_VALIDATOR)
        .build();
```

### ListRef

`ListRef` 只存 `String` 列表。

```java
public final ListRef friendList = builder(module.add("friend-list"), ListRef.TYPE)
        .defaultValue(List.of())
        .listValidator(Configs.REGEX_VALIDATOR)
        .build();
```

### EnumRef

```java
public final EnumRef<Type> mode = builder(module.add("mode"), Type.class)
        .defaultValue(Type.DEFAULT)
        .build();
```

要求该枚举实现 `ConfigEnum`。

### NBTRef

```java
public final NBTRef<Vec2> pos = builder(module.add("pos"), Vec2.class)
        .defaultValue(new Vec2(0.5D, 0.5D))
        .validator(v -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
        .build();
```

### KeyBindRef
运行功能形快捷键：
```java
public final KeyBindRef openMenu = hotkey(module.add("open-menu"))
        .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_G))
        .registerHotkey(HotKeyUtils.wrapAsHandler(this::openMenu))
        .build();
```
切换形快捷键在下章

## 注册到ModuleList
我们有一些功能会注册到ModuleList中， 其中 ModuleEntry是我们注册的东西

一个ModuleEntry并不和一个BaseModule绑定， 一个BaseModule可以注册多个ModuleEntry

只要有一个FlagRef和配套的KeyBind用于切换该FlagRef即可注册ModuleEntry

```java
public final FlagRef enable = flagBuilder(module.add("enable")).build();

// 通过调用moduleEntry方法将enable绑定到该快捷键并自动注册ModuleEntry
public final KeyBindRef hotkey = moduleEntry(module.add("hotkey"), new MultiKeyBind(), module.add("enable"))
    .build();
// 可以通过该方法设置动态附加给ModuleEntry的metadata， 让其显示例如当前模式等
public final KeyBindRef hotkey = moduleEntry(module.add("hotkey"), new MultiKeyBind(), module.add("enable"), ()-> Text.literal("当前的模式..."))
    .build();
```

## 常用配置项注意事项

### 配置 validator

用于拦截非法值：

```java
.validator(Configs.INT_POSITIVE)
.validator(Configs.doubleRange(0.0, 1.0))
.validator(Configs.REGEX_VALIDATOR)
```

### 配置 updateListener

用于配置变化后同步派生状态：

```java
.updateListener(this::parseEntityTypes)
.updateListener(v -> compile = v.stream().map(Pattern::compile).toList())
.updateListener(v -> typesDebug = getDebugTypes(v))
```


### listValidator

仅 `ListRef` 使用：

```java
.listValidator(Configs.REGEX_VALIDATOR)
```

### show / hideConfig

用于配置界面显隐：

```java
.show(() -> controlType.get().isIn(Type.MOV_VOID_2))
.hideConfig()
```

## 监听注册

统一放在 `registerAll()`。

```java
@Override
public void registerAll() {
    super.registerAll();
    registerListener(Listener.getPostGameTick(), this::onTick);
    registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
    registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
}
```

## 常用监听器类别
监听channel定义在Listener和RenderListener两个类中

### 基础生命周期 / tick

- `Listener.getPostGameTick()` // post tick
- `Listener.getPreGameTick()` // pre tick

### 网络包精确监听

优先用 `getChannel(Class)` 精确收窄：

```java
registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
```

如果包含了全部入站/出站包，可以使用
```java
registerListener(Listener.getPacketPoint().getPacketSendChannel(), this::onSetBack);
registerListener(Listener.getPacketPoint().getPacketReceiveChannel(), this::onSetBack);
```

### 自定义事件
注： 常用的channel:
- ModulePreset.class, 事件发送于玩家使用preset指令切换反作弊配置，如果某些配置需要基于反作弊变化，则需要注册其监听器自动修改模式
- FlightVelocity.class, 事件发送于飞行控制模块的决策过程。通过修改FlightVelocity可以修改鞘翅飞行/动量飞行的飞行决策
```java
registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
```

## 热键注册套路

### 普通动作热键

```java
public final KeyBindRef actionKey = hotkey(module.add("action"))
        .defaultValue(new MultiKeyBind(KeyCode.KEY_Z))
        .registerHotkey(HotKeyUtils.asHandler(this::doAction))
        .build();
```

### 包一层上下文
用于让该快捷键在打开其他屏幕/玩家不在游戏中 的时候不起作用
如果该功能涉及游戏修改，那么最好需要使用

```java
.registerHotkey(HotKeyUtils.wrapAsHandler(this::openConfigMenu))
```

### 开关配置热键
(仅包含不需要注册为ModuleEntry的开关)

```java
public final KeyBindRef toggleKey = toggleHotkey(
                module.add("toggle-hotkey"),
                new MultiKeyBind(KeyCode.KEY_X),
                module.add("enable"))
        .build();
```

### 模块热键
在ModuleEntry处已经讲过了

## 命令注册

统一放在 `registerAll()`：

```java
@Override
public void registerAll() {
    super.registerAll();
    registerCommandBootstrap(this::bootStrapTargetCommand);
}
```

### 命令体系简介
命令包含若干类型节点：用来操纵参数流以及响应
- Task : 接受后面所有的参数，执行指定任务，并返回接受状态
- Tree : 接受一个参数，并基于该参数dispatch到子节点， 返回子节点的接收状态
- List : 不接受参数, 直接将参数流依次推送向子节点直到某个子节点返回了接受的接受状态
- Bridge : 接受一个参数，并要求该参数与指定名字相同，如果相同则将参数流推送给子节点，否则返回不接受
- Delegate : 将参数流直接推送给delegate
- Main : 结构为一个delegate的List，相当于直接将参数流推送给List节点，当被“外部”调用的时候，会在参数流前面追加MainName（并标记为已接受）
- 
我们的MainCommand构成：一个AbstractMain

### 命令入口方法形态
向MainCommand的List节点中注册一个命令节点时，要先分清你要的是哪一层：

- `mainBuilder()`：直接把 `Tree` 节点塞进 `MainCommand` 的 `List` 根节点
- `subMainBuilder()`：先用同名 `Bridge` 节点包一层，再把里面的 `Tree` 节点塞进 `MainCommand` 的 `List` 根节点

这里有一个关键点：

- `mainBuilder()` 返回的 `Tree` 节点名字本身不重要，因为它是被直接放进 `List` 根节点里按顺序尝试接收的
- 只有 `subMainBuilder()` 这种“先包一层 Bridge”的入口，`Tree` 的名字才会作为外部命令分发名真正生效

直接注册 `Tree` 节点的例子：
```java
public void bootStrapTargetCommand(MainCommand mainCommand) {
    TreeSubCommand main = mainCommand.mainBuilder().name("target").build();
    main.subBuilder(SubCommand.treeBuilder())
            .name("calculate")
            .post(m -> m.subBuilder(SubCommand.taskBuilder())
                    .name("pos")
                    .helper("<target> calculate")
                    .arg(SimpleCommandArgs.argumentBuilder(MyArgType::new).name("target").build())
                    .post(s -> s.executor(CommandContext.execute(this::onTarget)))
                    .complete())
            .complete();
}

registerCommandBootstrap(this::bootStrapTargetCommand);
```

需要把整棵树作为一个具名子命令暴露时，用 `subMainBuilder()`：

```java
public void bootStrapConfigCommand(MainCommand mainCommand) {
    TreeSubCommand main = mainCommand.subMainBuilder().name("config").build();
    main.subBuilder(SubCommand.taskBuilder())
            .name("open")
            .post(e -> e.executor(CommandContext.run(this::onOpen)))
            .complete();
}
```

### 不推荐的方案
可以注册一个SubCommand Factory 通过registerCommands和registerSubCommands注册入MainCommand 

其中 两者的区别在于registerCommands直接将实例加入MainCommand的List节点中， 而registerSubCommands会先包装一个Bridge节点（用提供的name）再加入List节点

### 命令处理函数常见形态

```java
public void onTarget(CommandExecution exec, ArgumentInputStream args) {}
public boolean onTarget(CommandExecution exec, ArgumentInputStream args, ArgumentReader reader) { return true; }
public void onCancel() {}
```

## 生命周期分工

### 构造器

只放这些：

- `INSTANCE = this`
- `super("Name")`
- `bindFlag(enable)`
- 少量 delegate 绑定

不要在构造器里注册监听器和命令。

### registerAll

只放注册动作：

- `registerListener(...)`
- `registerCommandBootstrap(...)`


### onEnableModule

- 清空计数器
- 重置缓存状态

### onDisableModule
- 取消任务
- 回滚临时状态
- 清空本模块持有的运行期引用

## 推荐学习样例

- 配置注册 + updateListener：`TargetSelector`
- 包监听 + 自定义事件：`AutoResync`
- 热键菜单：`ConfigSystem`
- 命令树注册：`TargetCommand`
- 命令 + 存储：`Warps`

## 禁止事项

- 不要直接静态注册监听器
- 不要在 `onEnableModule()` 里重复注册监听器
- 不要绕过 `BaseModule` 自己向输入系统塞热键
- 不要把命令注册写到构造器里
- 不要把模块根路径拆成大量零散 `String[]` 常量

## 完成模块基础注册时的检查顺序

无需检查，用户会自行检查