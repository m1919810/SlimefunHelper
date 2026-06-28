## 粘液助手

该mod由 m1919810 开发

目前支持1.21.11, 1.21.4, 1.21.1版本

目前所有最新更新均在1.21.11版本

往期版本有1.21.8和1.20.4,不再更新,可以在旧release中找到

旨在**为[粘液科技游戏](https://slimefun-wiki.guizhanss.cn/)和普通生存提供更多便利和增强游戏与开发体验**

更多功能有待开发

## 安装须知
**该Mod不是服务端插件或者模组，请不要把他放到服务端里**

启动成功后,在多人游戏菜单的右上角，或者在游戏中使用右侧Alt或者使用指令!!openmenu clickgui打开ClickGui

其中可以配置所有快捷键和参数

模组指令前缀为!!或者/!!

## 依赖相关

该模组**不需要**除了FabricAPI以外的任何模组依赖

但是该模组与以下模组均有关联,推荐同时安装

注: 不是必须安装

- ViaFabricPlus(强烈推荐安装)
- Litematica
- JsMacros
- Baritone
- IMBlocker
- Meteor

如果遇到了和其他客户端同时使用导致的异常行为或者崩溃,请联系作者或者提出issue

## 配置相关
在游戏中输入/!!config load指令, 其便会弹出一行字,提示你需要放入文件. 点击该行字即可打开配置导入的文件夹

将保存的配置文件(后缀名.nbt)放入后使用/!!config load 你文件的名字(可以tab出来即可)  即可加载配置文件

如果想要保存配置文件,输入/!!config save 保存的名字,即可保存。

更多的使用方式输入/!!help config 获取

## 功能列表

该模组提供了一系列特殊的功能为粘液科技游玩和服务器探索与开发方面提供便利

这些功能分为以下若干模块

- 挖掘模块
  - 自动挖掘机器人
  - 快速破坏
  - GrimFastBreak
  - 挖掘行为优化
  - 发包挖掘
  - 等其他功能
- 聊天模块
  - 聊天助手
  - 聊天框合并
  - 客户端代理指令
  - 命令体系
  - 聊天屏蔽
  - 等其他功能
- 对战模块
  - 自动瞄准
  - 自动攻击
  - tp相关
  - 战斗工具
  - 等其他功能
- 实体渲染模块
  - 实体追踪
  - 抛射物计算
  - 方块追踪
  - 材质包相关
  - 禁用某些效果
  - 等其他功能
- 物品栏模块
  - 快捷物品栏操作
  - 快捷合成操作
  - 箱子界面管理器
  - 自动物品栏功能
  - 物品NBT编辑器
  - 物品数据库
  - 等其他功能
- 移动模块
  - 简易飞行
  - NoFall
  - TP
  - 相关日志
  - 鞘翅功能
  - Travel
  - 等其他功能
- 网络模块
  - 代理
- 交互模块
  - TP交互
  - 简单交互优化
  - 还在开发中
- 粘液模块
  - 配方数据库
  - 粘液指南书
  - 多方块助手
- 物品模型模块
  - 粘液材质附加
  - 覆盖物品模型
  - 新风格物品模型
  - 容器物品附加信息显示
  - 等其他功能
- 其他模块
  - 其他功能

目前还在开发中

## JsMacros与脚本
slimefunHelper目前对jsMacros提供了拓展支持
slimefunHelper对jsMacros的脚本运行环境注入了部分lib和utils,同时提供了有用的工具
目前的提供的工具lib列表
- ClientHelper
- DataHelper

- InputHelper

- KeyBindingHelper

- PacketHelper

- RenderHelper

- ReflectHelper
- JsHelper
- RegistryHelper
- NBTHelper
- EnumHelper
- EntityHelper
- ScreenHelper
- ItemStackHelper
- FileHelper
- WorldHelper
- MovTasks
- Tasks
- CombatTasks
- MineTasks
- InvTasks
- CommonUtils
- ChatUtils
- InventoryUtils
- CollectionUtils
- RaycastUtils
- ItemStackUtils
- Consts        
- Debug        
- 等

其中Consts之中还提供了方法用于向js运行环境导入所有的常用java类
之后会提供相关wiki或文档

## 不支持的模组

暂时和以下的模组冲突
- Language-reload