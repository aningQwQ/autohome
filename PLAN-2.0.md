# AutoHome 2.0 效果规划

目标 MC 版本：**26.2**（与 1.0 相同，不升级游戏版本）
mod 版本：`1.0.0` → `2.0.0`，mod id 保持 `autohome`

---

## 1. 定位

客户端**应急逃生**，不是挂机脚本。核心循环：

> 检测到危险 → 发送逃生命令 → 进入冷却 → 满足条件后重新武装

1.0 的骨架正确，2.0 解决三件事：状态**显式化**、开关**名副其实**、配置**可预期**。

---

## 2. 玩家可见效果走查

这是本规划的验收标准，实现完成后逐条手动验证。

| # | 场景 | 期望效果 |
|---|---|---|
| 1 | 战斗中血量掉到 6 | 走命令通道执行 `spawn`，聊天显示"已执行 /spawn"，HUD 出现倒计时 `10s` |
| 2 | 冷却中再次被打到低血量 | **不**重发命令，HUD 倒计时继续走 |
| 3 | 复活/回血到 7 | 冷却立即解除，聊天提示可再次触发，HUD 倒计时消失 |
| 4 | **冷却中按手动触发键** | **立即执行命令**，倒计时重置为 `10s` |
| 5 | 可触发状态按手动触发键 | 无视当前血量，立即执行命令 |
| 6 | 冷却中按手动复位键 | 冷却立即清除，回到可触发状态 |
| 7 | 配置里命令写成 `/spawn` | 走命令通道正常执行，无需手动去掉斜杠 |
| 8 | 配置里命令写成 `.home` 或 `!sell` | 原样走聊天通道发送，服务端插件照常识别前缀 |
| 9 | 配置里命令为空 / 纯空白 | 不发送任何内容，聊天提示 + `LOGGER.warn` |
| 10 | 逃生执行后立刻死亡重生 | 状态清空为可触发，不会卡在冷却 |
| 11 | 换维度 / 换服务器 / 断线重连 | 状态与冷却全部清空 |
| 12 | 死亡中 / 观战 / 创造模式 | 不触发任何命令 |
| 13 | 单人游戏按 ESC 暂停 | 冷却基于游戏刻，暂停时不推进 |

---

## 3. 状态机

两态，取代 1.0 中散落在条件里的隐式状态。

```
        ┌──── 自动触发（仅 ARMED）────┐
        │                             ▼
      ARMED                       COOLDOWN
        ▲                             │
        └──── 复位路径 / 手动触发 ──────┘
```

| 状态 | 含义 | HUD |
|---|---|---|
| `ARMED` | 武装，低血量可自动触发 | 不显示 |
| `COOLDOWN` | 冷却中，屏蔽自动触发 | `AutoHome 冷却 9s` |

- 发送命令是**瞬时**的，发送后立刻进入 `COOLDOWN`，因此不需要中间态
- **自动触发**：仅 `ARMED` 状态
- **手动触发**：`ARMED` 和 `COOLDOWN` 都可触发；在 `COOLDOWN` 中触发会把倒计时**重置**为完整时长
- **关键约束**：冷却计时用游戏刻（`Level.getGameTime()`），不是 `System.currentTimeMillis()`。这样暂停、系统时钟调整、卡顿都不影响冷却语义

---

## 4. 触发器

2.0 只做两个触发源，误判率为零。

### 4.1 低血量

- 条件：`!player.isDeadOrDying() && !player.isRemoved() && player.getHealth() < healthThreshold`
- 语义从 1.0 的 `<=` 改为 **`<`**，与 lang 文案 "drops below" 一致
- 仅在 `ARMED` 状态生效
- 创造模式、观战模式跳过

### 4.2 手动快捷键

| 按键 | 默认 | 行为 |
|---|---|---|
| `key.autohome.trigger` | 未绑定 | **两个状态都触发**：无视血量立即执行命令，并重置冷却倒计时 |
| `key.autohome.reset` | 未绑定 | 立即清除冷却，回到 `ARMED` |

手动触发不检查血量，但仍检查死亡 / 观战 / 创造模式，以及 `enabled` 开关。

---

## 5. 逃生动作

### 5.1 单条命令，原样发送

配置项是**单个字符串**。不剥离前缀、不拆分、不编排。

发送规则（按前缀分流）：

| 配置值 | 通道 | 实际行为 |
|---|---|---|
| `/spawn` | 命令通道 | 去掉前导 `/` 后经 `sendCommand("spawn")` 发送 |
| `.home`、`!sell`、`-warp` 等 | 聊天通道 | 原样经 `sendChat(raw)` 发送，服务端插件自行识别前缀 |
| 纯空白 | — | 不发送，提示 + warn |

**为什么必须分流**（已核对 26.2 字节码）：

- `ClientPacketListener.sendCommand(String)` 发送的是 `ServerboundChatCommandPacket`，包内是**不含 `/`** 的命令原文；客户端 dispatch 依赖服务端下发的命令树
- `ClientPacketListener.sendChat(String)` 发送的是 `ServerboundChatPacket`，即普通聊天文本
- 服务端 `ServerGamePacketListenerImpl.handleChat` **没有任何前缀检测**，聊天包就是聊天，不会被当作命令执行；命令只走 `handleChatCommand`

所以：写 `/spawn` 却用 `sendChat` 发出去 = 在公屏打了一句 "/spawn"，不会传送；而 `.home` 这类插件触发词用 `sendCommand` 发又不在命令树里。两条通道各走各的，才能同时支持 `/` 和其它前缀。

### 5.2 不做发送结果检测

服务端不会告知命令是否成功执行。**不采用**基于玩家位移的成功性判断与自动重试：

- 传送到相近坐标、被拉回、服务端延迟都会误判
- 误重发可能造成重复扣费 / 重复传送
- 收益远小于风险

失败只能靠聊天反馈由玩家自行发现。

---

## 6. 冷却与复位

复位路径**各自独立开关**，且每条都真正生效（这是 1.0 最大的问题）。

| 复位路径 | 默认 | 说明 |
|---|---|---|
| 定时复位 | 开 | 经过 `cooldownSeconds` 后复位 |
| 血量恢复复位 | 开 | `getHealth() > healthThreshold` 时立即复位 |
| 重生复位 | **强制开** | 检测到死亡后重生，无条件清空状态 |
| 手动复位 | 开 | 快捷键 |
| 换服 / 换维度复位 | **强制开** | 无条件清空状态 |

**两条强制路径的存在**，保证"定时 + 血量 + 手动"全关时也不会永久锁死——这是 1.0 的硬伤。

复位提示 `chat.autohome.reset_done` 每个冷却周期**只发一次**（用 session 里的 `resetNotified` 标志），避免每次判定都刷屏。

---

## 7. 反馈

### 7.1 聊天

| 时机 | lang key |
|---|---|
| 触发 | `chat.autohome.triggered`（已存在） |
| 复位 | `chat.autohome.reset_done`（已存在） |
| 命令为空 / 无效 | `chat.autohome.no_command`（新增） |

### 7.2 HUD 倒计时

- 冷却期间在屏幕角落显示剩余秒数，整数向上取整
- 位置四角可选，默认右上
- 字体用原版 `Font`，不引入任何渲染库
- 可整体开关

---

## 8. 配置项清单

全新结构，**不迁移** 1.0 配置。旧配置文件会被忽略并从默认值开始。

`@Config(name = "autohome")` 单个配置类，UI 用 Cloth Config 分组。

### 触发器
| 字段 | 类型 | 默认 | 范围 |
|---|---|---|---|
| `enabled` | boolean | `true` | — |
| `healthThreshold` | int | `6` | 1–20 |

### 逃生动作
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `command` | String | `"/spawn"` | 单个命令或聊天触发词，含前缀。默认带 `/` |

### 冷却
| 字段 | 类型 | 默认 | 范围 |
|---|---|---|---|
| `cooldownSeconds` | int | `10` | 1–600 |
| `resetOnTimer` | boolean | `true` | — |
| `resetOnHealth` | boolean | `true` | — |
| `manualResetEnabled` | boolean | `true` | — |

### 反馈
| 字段 | 类型 | 默认 | 范围 |
|---|---|---|---|
| `chatNotify` | boolean | `true` | — |
| `resetNotify` | boolean | `true` | — |
| `hudEnabled` | boolean | `true` | — |
| `hudCorner` | enum | `TOP_RIGHT` | 四角 |

### 调试
| 字段 | 类型 | 默认 | 范围 |
|---|---|---|---|
| `debugLog` | boolean | `false` | — |

### 配置校验

- `command` `trim()` 后为空 → 拒绝，回退到 `"/spawn"` 并 warn
- `resetOnTimer` / `resetOnHealth` / `manualResetEnabled` 全为 false → 强制开启 `resetOnTimer` 并 warn
- 阈值、范围越界由 Cloth Config 的 `@BoundedDiscrete` 拦截

---

## 9. 边界情况

| 情况 | 处理 |
|---|---|
| `client.player == null` | 直接返回 |
| `client.level == null` | 直接返回 |
| 死亡 / 濒死中 | 不触发；重生后清空状态 |
| 观战 / 创造模式 | 不触发 |
| `player.isRemoved()` | 不触发 |
| 单人暂停 | 计时基于游戏刻，天然不受影响 |
| 换维度 | 清空所有 session |
| 断线 / 换服 | 监听 `ClientPlayConnectionEvents.DISCONNECT` 清空 |
| 服务器拒绝命令 | 无法可靠检测，不做弥补；`debugLog` 输出 |
| 刚进服未加载完 | 状态从 `ARMED` 开始，无冷却残留 |

**session 存储**：`Map<UUID, Session>` 作为 handler 的实例字段，随连接生命周期创建销毁。彻底取代 1.0 的 `static ConcurrentHashMap`（跨世界残留 + UUID 无限增长）。

---

## 10. 明确不做

- 命令列表 / 多命令编排 / 命令间延迟
- 基于位移的发送成功检测与自动重试
- 自动寻路 / 自动走位
- 自动战斗 / 自动补血 / 自动吃东西
- 解析服务端 GUI 自动点击确认
- 敌对生物邻近检测（误判率高、每刻扫描开销大）
- 服务端侧任何逻辑（保持纯客户端 mod）

---

## 11. 前置阻塞项（已解决）

1. ~~**mappings 方案未定**~~ **已解决**：MC 26.2 官方发布即非混淆，生态已全部转向 official 命名空间，无需任何 mappings provider。落地方式与依据见第 14 节。
2. **`assets/autohome/icon.png` 缺失**。`fabric.mod.json` 引用了但 jar 里没有，2.0 已补一张脚本生成的 64×64 占位图，建议替换为正式图标。
3. **包名是否沿用 `com.example.autohome`**。1.0 用的是 `com.example`，如果打算正式发布建议换成真实域名反写，但会改变 entrypoint 配置。

---

## 12. 里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| **M1 骨架** | 2.0 工程搭建、mappings 打通、配置类 + lang 就位、两态状态机骨架 | `gradle build` 通过，进游戏无崩溃 |
| **M2 核心** | 低血量触发、手动触发（含冷却中触发）、命令双通道发送、冷却与复位路径 | 走查表 1–11 通过 |
| **M3 交互** | HUD 倒计时、两个快捷键、聊天提示、边界情况 | 走查表 12–13 通过，边界表全绿 |
| **M4 打磨** | 配置校验、icon、README、打包 | 产出可发布的 2.0.0 jar |

每个里程碑结束都跑一次完整走查表，不依赖"应该没问题"。

---

## 13. 验证方式

- **编译验证**：`gradle build`（需先解决 mappings）
- **实机走查**：对照第 2 节表格逐条手动验证
- **状态机单测**：把 `(state, health, pressedTrigger, config) -> action` 抽成纯函数，用 JUnit 覆盖状态转移，不需要启动 Minecraft
- **命令通道验证**：分别用 `/spawn` 和 `.xxx` 两种前缀实测，确认一个走命令包、一个走聊天包
- **边界验证**：专门测试死亡重生、换维度、断线重连三条强制复位路径

---

## 14. 实现期确认的坑与依据

以下是 2.0 实现过程中实际踩到或需要反汇编确认的点，均已固化进代码。保留此节是为了避免后续维护时重复排查。

### 14.1 MC 26.2 无混淆，不需要 mappings

`loom.officialMojangMappings()` 在 26.2 上报 `Failed to find official mojang mappings for 26.2`，原因是 Mojang 的 26.2 版本清单里 `downloads` 只有 `client`/`server`，**没有 `client_mappings`**。

但这不是"缺少 mappings"，而是**26.2 官方发布即非混淆**：下载官方 client jar 抽样检查，类名为 `net/minecraft/client/Camera.class` 这类原名，混淆名为 0 个。fabric-api 0.157.0+26.2、cloth-config 26.2.155、modmenu 20.0.1 的 manifest 全部标注 `Fabric-Mapping-Namespace: official`；yarn 与 intermediary 都停在 1.21.11。

Loom 1.17.19 内置了对应的自动检测：

```java
// MinecraftMetadataProvider
public boolean isUnobfuscated() {
    return this.getVersionMeta().isVersionOrNewer("2025-11-01T00:00:00+00:00")
        && !this.getVersionMeta().downloads().containsKey("client_mappings");
}
```

命中后会自动把 production namespace 设为 `official`、关闭 intermediary、把 mixin remap 设为 `static`。

Loom 仍强制要求一个 mappings 依赖（否则报 `Configuration 'mappings' has no dependencies`），因此工程里放了一个空 identity 映射：

```groovy
mappings loom.layered { mappings(file('identity.tiny')) }
loom { noIntermediateMappings() }
```

**注意**：`identity.tiny` 的 tiny v2 头必须把命名空间写在**同一行**（`tiny\t2\t0\tsource\ttarget`），写成两行会报 `no source namespace in Tiny v2 header`。另外 `remapSourcesJar` 在无 intermediary 时会失败，因此工程没有启用 `withSourcesJar()`。

### 14.2 命令必须按前缀分流（否则 `/spawn` 会变成公屏发言）

反汇编 `ServerGamePacketListenerImpl` 确认，客户端两条发送通道对应服务端两个独立处理器：

- `sendCommand(String)` → `ServerboundChatCommandPacket`，包内是**不含 `/`** 的命令原文，服务端走 `handleChatCommand` 并依赖下发的命令树解析
- `sendChat(String)` → `ServerboundChatPacket`，就是普通聊天，服务端走 `handleChat`
- `handleChat` 最终调用 `tryHandleChat(...)`，其中**没有任何前缀检测**，聊天包不会被当作命令执行

所以配置里写 `/spawn` 却用聊天通道发出去，只会在公屏打出一句 "/spawn"。2.0 据此按前缀分流（见 `AutoHomeHandler.send()`）：以 `/` 开头走命令通道并剥掉前导斜杠，其它前缀（`.home`、`!sell` 等）原样走聊天通道交给服务端插件识别。

### 14.3 Cloth Config 会把 static 字段当成配置项，并让保存按钮失效

**这是 2.0 初版实际出现过的 bug，两个症状同源。**

`ConfigScreenProvider` 收集配置项时是：

```java
Arrays.stream(configClass.getDeclaredFields())   // 无任何 Modifier 过滤
```

反汇编可确认调用点之后直接进入 `groupingBy`，中间没有 `Modifier.isStatic` / `isTransient` 之类的判断。因此**不能**在 `@Config` 类里放任何 static 字段。

症状一：static 字段被渲染成多一个配置项，标签显示为原始 i18n key。症状二（更严重）：保存链路是

```
saveAll() → entry.save() → Utils.setUnsafely(field, instance, value) → field.set(...)
```

`Field.set` 写 `static final` 字段抛 `IllegalAccessException`，`Utils` 将其包装为 `RuntimeException` 抛出，导致后续的 `this.save()`（落盘）与退出界面都执行不到，表现为**保存并退出按钮点不动**。反汇编 `AbstractConfigEntry.save()` 确认它无条件调用 `saveCallback`，没有"仅在改动时保存"的短路，所以必然触发。

**约定**：常量与 logger 一律放在 `ModConfig`（无 `@Config` 注解，不参与扫描）；`AutoHomeConfig` 类注释已写明此约束。

同类问题还有枚举标签：Cloth 用 `Component.translatable(enum.toString())` 渲染，即裸常量名 `TOP_RIGHT`，同样会显示原始 key。解法是让枚举实现 `SelectionListEntry.Translatable` 并返回带命名空间的 key（cloth 的两个枚举渲染分支都认这个接口）。

### 14.4 26.2 与 1.0 时代的 API 差异

1.0（Loom 1.17.19 时代）用到的若干接口在 26.2 已变更，2.0 按字节码核对后替换：

| 1.0 用法 | 26.2 替代 |
|---|---|
| `KeyBindingHelper` | `KeyMappingHelper` |
| `KeyMapping(String, Type, int, String)` | `KeyMapping(String, Type, int, KeyMapping.Category)`，分类用 `Category.register(Identifier)` 注册 |
| `HudRenderCallback` + `GuiGraphics` | `HudElementRegistry` + `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` |

### 14.5 检查命令

```bash
# 从仓库根 code/ 执行

# 2.0 完整构建 + 状态机检查
cd autohome && gradle clean build

# 单独跑状态机检查（免依赖，Maven Central 不可用时也能跑）
gradle stateMachineTest --rerun-tasks
```

状态机检查写成免依赖的 `main` 断言程序（`CooldownDecisionTest`），通过 `JavaExec` 挂在 `check` 上。原因是本机曾出现 Maven Central 返回 403、JUnit 无法下载的情况，避免把验证能力绑在外部仓库可达性上。
