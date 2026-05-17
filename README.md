# MC-AI-Assistant

基于 NeoForge 1.21.1 的 Minecraft AI 聊天助手 Mod。

## 概述

MC-AI-Assistant 将 AI 聊天助手直接集成到 Minecraft 中。通过 JEI 风格的侧边栏界面与 AI 对话，支持 OpenAI 兼容 API 的完整 function calling，让 AI 能查询游戏状态、搜索网络、规划项目。

## 功能特性

- **🤖 AI 聊天界面** — JEI 风格侧边栏，支持多线程对话、WeChat 式聊天气泡
- **🔗 OpenAI 兼容 API** — 支持 OpenAI / DeepSeek / Ollama 等后端，SSE 流式输出
- **⚡ 12 个 Function Calling 工具**：
  - `get_inventory` — 背包 + 盔甲 + 副手
  - `get_player_status` — 位置、维度、生命、饥饿
  - `get_world_state` — 时间、天气、难度、生物群系
  - `item_info` — 物品详情（最大堆叠、稀有度、食物属性）
  - `lookup_recipe` — 查询合成配方（JEI 增强）
  - `lookup_usages` — 反向查询物品用途
  - `calculate_materials` — 批量合成原料树计算
  - `web_search` — 网络搜索（DDG→Bing 兜底，深度模式抓取全文）
  - `read_webpage` — 读取指定网页内容
  - `list_mods` — 列出已安装 Mod
  - `game_info` — Minecraft 版本信息
  - `manage_project` — 项目管理（创建/追踪/完成/取消）
- **📋 项目系统** — 将目标拆解为步骤，HUD 实时追踪背包进度，级联完成 + 自动归档
- **📝 Markdown 渲染** — 加粗/斜体/删除线/链接/表格/引用/标题/分割线，链接可点击
- **🧠 DeepSeek 思考模式** — 思考过程折叠显示，tool call XML 过滤
- **💾 按世界存储** — 对话历史 + 项目数据 + 会话日志独立存储
- **🎨 可配置** — API Key / 端点 / 模型 / 温度 / 最大 Token
- **🔍 Debug 模式** — 内置 AI 测试套件，性能监控

## 安装

### 前置要求

- Minecraft 1.21.1
- NeoForge 21.1.219+
- Java 21+

### 安装步骤

1. 下载 `agentchat-1.0.0.jar`
2. 放入 `.minecraft/mods/` 文件夹
3. 启动游戏，按 `O` 键打开 AI 聊天界面

## 配置

### API Key 设置

在游戏内按 `O` → 设置界面直接填入 API Key / Endpoint / Model。

或手动创建 `config/agentchat-secrets.json`：

```json
{
  "api_key": "your-api-key-here",
  "endpoint": "https://api.openai.com/v1",
  "model": "gpt-4o-mini"
}
```

> ⚠️ **安全提示**：`secrets.json` 已包含在 `.gitignore` 中，不会提交到版本控制。

### 配置项

| 选项 | 默认值 | 说明 |
|------|--------|------|
| API Key | — | OpenAI 兼容 API Key |
| Endpoint | `https://api.openai.com/v1` | API 端点地址 |
| Model | `gpt-4o-mini` | 模型名称 |
| Temperature | 0.7 | 采样温度 (0.0–2.0) |
| Max Tokens | 1024 | 最大输出 Token (100–4096) |

## 快捷键

| 按键 | 功能 |
|------|------|
| `O` | 打开/关闭 AI 聊天界面 |
| `/chat` | 命令行快速对话 |

## 技术栈

- **Mod 框架**: NeoForge 1.21.1
- **构建工具**: Gradle
- **语言**: Java 21
- **HTTP 客户端**: `java.net.http.HttpClient`（无外部依赖）
- **JSON**: Gson
- **JEI**: compileOnly 可选依赖

## 编译

```bash
# 完整构建
./gradlew build --no-daemon

# 构建并部署到 mods 文件夹
./gradlew deployToMods --no-daemon

# 运行测试
./gradlew test --no-daemon
```

## 项目结构

```
src/main/java/com/lumoren/agentchat/
├── AgentChat.java              # @Mod 入口
├── ai/                         # AI 服务层
│   ├── AIChatService.java      # 对话编排（tool calling loop）
│   ├── OpenAICompatClient.java # HTTP 客户端（SSE 流式）
│   ├── ToolCallDispatcher.java # 工具调度
│   ├── GameDataAccess.java     # Minecraft 数据访问
│   ├── MaterialCalculator.java # 合成原料树计算
│   ├── ProjectPlanningService.java
│   ├── SessionLogger.java      # 会话日志
│   └── TestSuite.java          # AI 测试套件
├── client/                     # 客户端事件
├── config/                     # 配置管理
├── model/                      # 数据模型（records）
├── persistence/                # 持久化（JSON 文件）
├── tools/                      # 12 个 GameTool 实现
├── ui/                         # UI 组件（Screen + Widget）
└── i18n/                       # 国际化（中/英）
```

## 许可证

GNU Lesser General Public License v3
