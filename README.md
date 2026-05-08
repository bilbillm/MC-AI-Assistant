# MC-AI-Assistant

一个基于 NeoForge 1.21.1 的 Minecraft AI 聊天助手 Mod。

## 概述

MC-AI-Assistant 是一款 Minecraft 客户端 Mod，将 AI 聊天助手直接集成到游戏中。它提供类似 JEI 侧边栏的交互界面，支持与 OpenAI 兼容的 API，让玩家无需切换窗口即可与 AI 对话、查询游戏数据。

## 功能特性

- **🤖 AI 聊天界面** — JEI 风格的侧边栏 UI，游戏内直接对话
- **🔗 OpenAI 兼容 API** — 支持 OpenAI、Azure OpenAI、Ollama 等后端
- **⚡ Function Calling** — AI 可直接调用 5 种游戏数据工具：
  - 查询物品信息
  - 查询方块信息
  - 查询实体信息
  - 查询合成配方
  - 查询进度/成就
- **💾 按存档存储聊天历史** — 每个世界独立保存对话记录
- **🎨 可自定义 UI** — 字体大小、侧边栏宽度、主题色等

## 安装

### 前置要求

- Minecraft 1.21.1
- NeoForge 1.21.1（推荐最新稳定版）
- Java 21+

### 安装步骤

1. 下载最新版本的 MC-AI-Assistant
2. 将 `.jar` 文件放入 Minecraft 的 `mods` 文件夹
3. 启动游戏，确认 Mod 已加载

## 配置

### API Key 设置

创建文件 `run/config/mcaiassistant-secrets.json`：

```json
{
  "api_key": "your-api-key-here",
  "endpoint": "https://api.openai.com/v1",
  "model": "gpt-4o-mini"
}
```

> ⚠️ **安全提示**：`secrets.json` 文件已包含在 `.gitignore` 中，不会提交到版本控制。请勿分享此文件。

### 客户端配置

在游戏内按 `O` 键打开设置界面，可配置：

| 选项 | 默认值 | 说明 |
|------|--------|------|
| API Key | — | OpenAI 兼容 API Key |
| Endpoint | `https://api.openai.com/v1` | API 端点地址 |
| Model | `gpt-4o-mini` | 模型名称 |
| 侧边栏宽度 | 300px | 聊天界面宽度 |
| 字体大小 | 14px | 聊天字体大小 |

## 截图

> TODO: 添加游戏内截图

## 技术栈

- **Mod 框架**: NeoForge 1.21.1
- **构建工具**: Gradle
- **语言**: Java 21
- **API 通信**: OkHttp + Gson
- **许可证**: GNU LGPL v3

## 开源协议

本项目基于 **GNU Lesser General Public License v3** 开源。

## 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feat/amazing-feature`)
3. 提交变更 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feat/amazing-feature`)
5. 创建 Pull Request

## 致谢

- [NeoForge](https://neoforged.net/) — Mod 框架
- [OpenAI](https://openai.com/) — AI API
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — UI 设计灵感
