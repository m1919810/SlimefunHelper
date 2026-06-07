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
- 常见配置项注册
- 事件监听注册
- 热键注册
- 命令注册
- 生命周期放置位置

## 核心原则

所有可注册对象都走 `BaseModule` 提供的方法，不要绕开。

必须遵守：

- 事件监听放在 `registerAll()`
- 命令注册放在 `registerAll()`
- 热键优先通过配置构建器注册
- 模块启用状态通过 `bindFlag(enable)` 绑定
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

有显示名需求时用构造器：

```java
super("Config");
super("Printer");
super("BeaconPlus");
```

否则直接默认类名。

### 根路径

优先先抽一个 `ModulePath` 根，再从根派生：

```java
public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "attack");
public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
```

子项统一写成：

```java
attack.add("whitelist")
moveSafety.add("auto-resync-pos")
```

## Flag 绑定

模块型功能默认都要有 enable flag，并在构造器中绑定：

```java
public final FlagRef enable = flagBuilder(module.add("enable")).build();

public MyModule() {
    bindFlag(enable);
}
```

如果模块不是典型开关模块，可以不绑，但要明确是工具模块还是命令模块。

## Ref 注册套路

### FlagRef

```java
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

```java
public final KeyBindRef openMenu = hotkey(module.add("open-menu"))
        .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_G))
        .registerHotkey(HotKeyUtils.wrapAsHandler(this::openMenu))
        .build();
```

## 常用监听器写法

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

原则：

- 放纯同步逻辑
- 只做派生缓存刷新、编译、索引重建
- 不在这里注册事件或命令

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

## 事件监听注册

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

### 基础生命周期 / tick

- `Listener.getPostGameTick()`
- `Listener.getPreTick()`
- `Listener.getWorldSwitchPoint()`

### 网络包精确监听

优先用 `getChannel(Class)` 精确收窄：

```java
registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
```

### 自定义事件

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

### 包一层模块上下文

```java
.registerHotkey(HotKeyUtils.wrapAsHandler(this::openConfigMenu))
```

### 开关配置热键

```java
public final KeyBindRef toggleKey = toggleHotkey(
                module.add("toggle-hotkey"),
                new MultiKeyBind(KeyCode.KEY_X),
                module.add("enable"))
        .build();
```

### 模块入口热键

用于把热键和 toggle/config 元数据一起挂到模块入口：

```java
public final KeyBindRef entry = moduleEntry(
                module.add("hotkey"),
                new MultiKeyBind(KeyCode.KEY_R),
                module.add("enable"))
        .build();
```

## 命令注册

统一放在 `registerAll()`：

```java
@Override
public void registerAll() {
    super.registerAll();
    registerCommandBootstrap(this::bootStrapTargetCommand);
}
```

### 命令入口方法形态

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
```

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
- 极少量一次性初始化入口

### onEnableModule

放运行时开启动作：

- 清空计数器
- 重置缓存状态
- 打开某个活动流程

### onDisableModule

放运行时关闭动作：

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

1. 是否存在模块根 `ModulePath`
2. 是否存在 enable flag，且需要时已 `bindFlag`
3. 配置项是否都走构建器注册
4. `validator` / `updateListener` 是否只做配置层逻辑
5. 监听器是否都在 `registerAll()`
6. 命令是否都在 `registerAll()`
7. 热键是否通过构建器注册
8. `onEnableModule` / `onDisableModule` 是否只处理运行时状态