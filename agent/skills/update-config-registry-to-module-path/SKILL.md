---
name: update-config-registry-to-module-path
description: 将同一模块下、同一 config 根路径的配置声明统一改造成 ModulePath 复用，减少重复的静态路径字段。用于把 `Configs.X_CONFIG + String[] PATH` 风格收口为 `ModulePath module = makePath(...)` 加 `module.add(...)`。
disable-model-invocation: true
---

# update-config-registry-to-module-path

## 目标

把一个 module 内、明显属于同一个 config 和同一个根路径的配置项，统一收口到一个 `ModulePath`。

结果形态：

- 旧结构
  - 多个 `public static final String[] XXX = {"module-root", "child"};`
  - 多个 `flagBuilder(Configs.X_CONFIG, XXX)`
  - 多个 `builder(Configs.X_CONFIG, Type).path(XXX)`
- 新结构
  - 一个 `public final ModulePath module = makePath(Configs.X_CONFIG, "module-root");`
  - 多个 `flagBuilder(module.add("child"))`
  - 多个 `builder(module.add("child"), Type)` 或 `intBuilder(module.add("child"))`

## 适用判断

满足以下条件时优先改：

1. 同一个类里的多数配置都来自同一个 `Configs.*_CONFIG`
2. 路径前缀一致，例如都在 `chat-helper.*`
3. 这些 `String[]` 路径数组多半占据着public static final String[] 这时候我们必然优化他们
4. 注意 有些在改动的过程中使用了 仅一个String参数的makePath函数，其返回值也是String[], 这些也需要被优化掉

如果某些配置实际属于另一个根路径，例如 `chat-screen-tools.*`，不要强行并到当前 `ModulePath`，需要创建新的 `ModulePath` 实例，例如 `chatTools = makePath(Configs.CHAT_CONFIG, "chat-screen-tools")`，继续添加。

补充规则：`makePath(Config, String)` 只接受一个根段字符串，不支持一次传多个路径段。遇到 `a.b.*` 这种两层根时，应先建根对象，再链式下钻，例如：

```java
public final ModulePath detectEntity = makePath(Configs.RENDER_CONFIG, "detect-entity");
public final ModulePath entityEsp = detectEntity.add("entity-esp");
```

## 改造规则

### 1. 先抽根路径

示例：

```java
public final ModulePath chat = makePath(Configs.CHAT_CONFIG, "chat-helper");
```

### 2. 同根字段全部改成 `module.add(...)`

布尔项：

```java
flagBuilder(chat.add("ignore-chat-len-limit")).build();
```

整型项优先用专门 builder：

```java
intBuilder(chat.add("chat-len-limit"))
    .defaultValue(256)
    .build();
```

字符串项：

```java
builder(chat.add("limit-warn-format"), String.class)
    .defaultValue("...")
    .build();
```
其他项: 保留原有格式, 仅替换builder()函数的调用

### 3. 删除无用路径常量

当 `String[]` 常量仅用于当前类、且已被 `ModulePath` 完全替代后，直接删除，减少无用字段数量。

## 推荐替换模式

### FlagRef

- 旧

```java
flagBuilder(Configs.CHAT_CONFIG, SOME_FLAG).build();
```

- 新

```java
flagBuilder(chat.add("some-flag")).build();
```

### IntRef

- 旧

```java
builder(Configs.CHAT_CONFIG, Integer.class)
    .path(SOME_INT)
    .defaultValue(1)
    .build();
```

- 新

```java
intBuilder(chat.add("some-int"))
    .defaultValue(1)
    .build();
```

### StringRef

- 旧

```java
builder(Configs.CHAT_CONFIG, String.class)
    .path(SOME_STR)
    .defaultValue("...")
    .build();
```

- 新

```java
builder(chat.add("some-str"), String.class)
    .defaultValue("...")
    .build();
```

## 替换完之后无需做检查。直接终止任务并提交结果

## ChatExtra 这次总结出的经验

- `ModulePath` 类型在 `me.matl114.hacks.api.ModulePath`，不是 `me.matl114.managers.config` 下的类型
- `chat-helper.*` 这一组几乎都属于同一个模块域，适合统一挂到 `chat` 下
- 改造后字段数量下降，因为中间那批 `String[]` 常量可以整体删除
- 代码关注点从“路径数组命名”转为“模块域 + 子键名”，读起来更接近配置树结构
- 真正需要保留的不是“每个键一个常量”，而是“每个模块一个根路径对象”
