---
name: language-file-generation
description: 为 SlimefunHelper 补全语言 key、对齐现有语言文件结构与文案风格。用于新增配置项、命令项、模块显示名后，统一补全 `src/main/resources/assets/slimefunhelper/lang/en_us.json`。
disable-model-invocation: true
---

# language-file-generation

## 目标

当出现 Missing translation key，或者新增了配置项、模块项、命令项后，快速补齐语言文件，并保持以下几件事一致：

- key 路径与配置路径一致
- 文案风格与现有 `en_us.json` 一致
- 同前缀条目在同一段附近
- tooltips 与主文案分层清晰

## 先看什么

优先阅读：
- `src/main/resources/assets/slimefunhelper/lang/en_us.json`
仿照当前风格和同前缀路径
不要凭名字硬翻。
不要阅读代码文件

## 文件结构规律

这个项目当前语言文件核心特点：

- 只维护一个主语言文件：`en_us.json`
- 实际文案主体是中文，不是英文
- key 直接复用配置路径，例如：
  - `att-bot.legal-mode`
  - `combat-bot.elytra-bot.mode`
  - `velocity-management.antikb.mode`
- 同一模块的 key 通常连续放在一起
- 说明文本使用 `.tooltips` 后缀

当前语言文件包含的有:
- 配置项名字
- 配置项路径
- 枚举类型（实现了ConfigEnum接口的）的Display名（前缀为configenum）
- 部分屏幕组件的显示名字 （前缀为widget.
- ModuleEntry的名字 (前缀为module-toggle.)
- ModuleMeta的翻译值 (前缀为module-meta.)
- ClickGui中的模块名 (前缀为widget.click-gui.module-name)
- ClickGui中的模块介绍 

在补全的时候 你不需要调查这些 你只需要认准给出的missing是哪组的然后补全即可

## 工作流

### 1. 文案生成规则

遵守当前项目已有风格：

- 主文案优先写“模块名: 配置名”
- 子项命名尽量短，不解释实现
- 布尔和开关常用：`启用`、`快捷键`、`渲染目标`、`自动...`
- 坐标项常用：`位置`、`X偏移/Y偏移`、`主手/副手/头盔/...位置`
- 数值项常用：`阈值`、`距离`、`高度`、`时长`、`倍率`
- tooltip 只解释意图、行为边界、限制条件

不要做这些事：

- 不要写成代码解释
- 不要引入与相邻文案风格冲突的新术语
- 不要把英文 UI 名称乱翻成另一套体系

## 放置规则

新增 key 时：

- 就近插入到同前缀分组附近
- 同一个前缀的最好连续

例如：

- `combat-bot.elytra-bot.*` 放到 ElytraBot 那一段
- `velocity-management.antikb.*` 放到 antikb 那一段
- `spear-module.*` 放到 spear-module 那一段

## 检查清单

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

无需检查 用户会自己检查

## 快速模板

### 开关

```json
"some-module.enable": "某功能: 启用"
```

### 热键

```json
"some-module.hotkey": "某功能: 快捷键"
```

### 数值

```json
"some-module.range": "某功能: 距离"
```

### tooltip

```json
"some-module.range.tooltips": "用于控制触发范围"
```

### 列表型隐私关键词

```json
"config.privacy-protection-path-keywords": "配置快照: 隐私保护关键词"
```


## 用户需求
当用户需求提供了一组Missing translation key的时候 尝试按上面规则补全他们