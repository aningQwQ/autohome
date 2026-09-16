# AutoHome

[![Build](https://github.com/aningQwQ/autohome/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/aningQwQ/autohome/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Fabric-green)

Minecraft 客户端**应急逃生** Mod：血量掉到阈值以下时，自动通过命令通道执行逃生命令（如 `/spawn`），并在 HUD 显示冷却倒计时。

不是挂机脚本。核心循环只有一条：

> 检测到危险 → 发送逃生命令 → 进入冷却 → 满足条件后重新武装

## 特性

- **显式状态机**：`ARMED`（武装）↔ `COOLDOWN`（冷却），只有两个状态，行为可预期
- **游戏刻计时**：冷却基于 `Level.getGameTime()`，暂停、系统时钟调整、卡顿都不影响冷却语义
- **命令前缀分流**：`/spawn` 走命令通道，`.home`、`!sell` 等插件触发词原样走聊天通道，服务端插件照常识别
- **多条复位路径**：定时复位、血量恢复复位、手动复位键、重生复位、换服/换维度复位。重生与换服复位强制生效，任何配置组合下都不会永久锁死
- **手动触发键**：冷却中也可强制触发自救，倒计时重置为完整时长
- **可选 HUD 倒计时**：四角可选，原版字体渲染，无额外渲染库
- **Cloth Config 配置界面**：在 Mod Menu 中直接改，分组折叠 + 悬浮说明
- **安全边界**：死亡中、观战、创造模式不触发；命令为空不发送
- **中英双语**，客户端 Mod，服务端无需安装

## 下载

- **发行版**：[Releases](https://github.com/aningQwQ/autohome/releases) —— `nightly` 标签随 main 分支每次构建自动更新
- 将 `autohome-x.y.z.jar` 放入 `.minecraft/mods` 即可

| 依赖 | 版本 | 必需 |
|---|---|---|
| Minecraft | 26.2 | 是 |
| Fabric Loader | ≥ 0.19.3 | 是 |
| Fabric API | 对应 26.2 | 是 |
| Cloth Config | ≥ 26.2.155 | 是 |
| Mod Menu | ≥ 20.0.1 | 否（推荐，提供配置入口） |

## 配置

游戏内 **Mods → AutoHome → Configure** 打开，或编辑 `config/autohome.json`。

| 分组 | 选项 | 默认 | 说明 |
|---|---|---|---|
| 触发器 | `enabled` | `true` | 总开关 |
| 触发器 | `healthThreshold` | `6` | 血量**低于**该值时自动触发（1–20） |
| 逃生动作 | `command` | `"/spawn"` | 单个命令或聊天触发词，含前缀 |
| 冷却 | `cooldownSeconds` | `10` | 冷却时长（1–600 秒） |
| 冷却 | `resetOnTimer` | `true` | 定时复位 |
| 冷却 | `resetOnHealth` | `true` | 血量回到阈值以上立即复位 |
| 冷却 | `manualResetEnabled` | `true` | 允许手动复位键 |
| 反馈 | `chatNotify` / `resetNotify` | `true` | 触发 / 复位时的聊天提示 |
| 反馈 | `hudEnabled` / `hudCorner` | `true` / 右上 | HUD 倒计时及位置 |
| 调试 | `debugLog` | `false` | 详细日志 |

## 快捷键

在 **选项 → 按键绑定 → AutoHome** 中绑定（默认未绑定）：

| 按键 | 行为 |
|---|---|
| `key.autohome.trigger` | 立即执行逃生命令（无视血量与冷却；冷却中触发会重置倒计时） |
| `key.autohome.reset` | 立即清除冷却，回到可触发状态 |

## 构建

需要 JDK 25，其余由 Gradle wrapper 自动处理：

```bash
./gradlew build          # 产物在 build/libs/
./gradlew stateMachineTest   # 无依赖的状态机检查
```

CI：每次 push / PR 由 GitHub Actions 自动构建并上传产物，main 分支构建同步更新 nightly release。

## FAQ

**为什么触发后没传送？** 检查 `command` 是否在服务端存在；本 Mod 不检测命令执行结果（服务端不会回执，位移检测误判率过高），失败时不会重试，也不会重复扣费。

**冷却会被暂停/改系统时间绕过吗？** 不会。冷却用游戏刻，单人游戏按 ESC 暂停时冷却不推进。

**配置 1.0 还在吗？** 2.0 结构全新，不迁移 1.0 配置，从默认值开始。

## License

[MIT](LICENSE) © 2026 徐英珺
