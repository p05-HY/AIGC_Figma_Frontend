# AIGC_Figma_Frontend

超级蓝心小V（BlueHeartV）是一款 Android AI 智能体前端客户端。

当前版本不再在 App 内直接接入模型供应商，而是连接 Python 侧部署的 LangGraph Agent Server。用户在 Android 端发送文本或图片消息，服务端负责会话状态、工具调用和流式响应，App 负责界面展示、移动端权限能力和 ADB 工具执行。

## 项目简介

本项目面向移动端智能体交互，核心目标是：

- 提供稳定的 LangGraph Agent 流式对话体验
- 支持文本与图片输入
- 从 Agent Server 获取会话历史与 thread 状态
- 展示 Agent 流式文本、节点执行状态和工具执行状态
- 通过 WebSocket 暴露手机控制能力给 Python Agent
- 支持悬浮球快捷交互

## 当前架构

Android App 主要承担前端和设备能力侧职责：

- Chat UI 保持本地 Compose 状态，用户消息发送到 Agent Server
- 会话历史从 Agent Server `/threads` 系列接口读取，不再用 Room 长期保存聊天记录
- 流式响应通过 Agent Server run stream 接口接收
- ADB 工具能力通过 `ws://host:port/adb` 或 `wss://host:port/adb` 暴露给后端 Agent
- 系统能力接口仍通过 `/system` WebSocket 协议提供

Agent Server 固定使用：

- `assistant_id`: `agent`
- `graph_id`: `agent`
- `stream_mode`: `messages-tuple`、`updates`、`tasks`、`custom`

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose + Material3
- 架构：MVVM（ViewModel + StateFlow）
- 依赖注入：Koin
- 网络：OkHttp（HTTP、SSE、WebSocket）
- 服务端：Python LangGraph / LangSmith Agent Server API
- 手机控制：Shizuku + AIDL + AccessibilityService + WebSocket 工具协议
- 本地存储：SharedPreferences（Agent Server 地址与 API Key 配置）
- 构建：Gradle Kotlin DSL

## 核心能力

- LangGraph Agent 流式对话
- 文本与图片多模态输入
- 服务端 thread 历史加载与会话切换
- Agent node/tool 执行状态展示
- 悬浮球服务与快捷入口
- ADB 工具 WebSocket 客户端
  - observe：采集当前屏幕快照与 UI 树
  - launch：启动应用
  - tap / doubleTap / longPress：触控操作
  - swipe：滑动操作
  - type：输入文本
  - keyevent：发送按键事件
  - interact：等待用户完成手动交互

## 项目结构说明

- `app/src/main/java/com/example/blueheartv/chat`
  Agent Server 配置、HTTP/SSE 客户端、ChatProvider 抽象与实现
- `app/src/main/java/com/example/blueheartv/viewmodel`
  聊天状态、服务端 thread 缓存、流式消息状态管理
- `app/src/main/java/com/example/blueheartv/ui`
  Compose 页面与组件
- `app/src/main/java/com/example/blueheartv/control`
  ADB WebSocket 服务、Shizuku shell 执行器、屏幕快照采集、无障碍服务
- `app/src/main/java/com/example/blueheartv/system`
  系统能力 WebSocket 客户端与协议处理
- `app/src/main/java/com/example/blueheartv/floating`
  悬浮球前台服务
- `app/src/main/aidl/com/example/blueheartv/control`
  Shizuku user service 使用的远程 shell AIDL
- `app/src/main/res/xml/adb_accessibility_service.xml`
  无障碍服务配置

## 环境要求

- Android Studio（建议最新稳定版）
- JDK 21
- Android SDK 36
- 可用 Android 设备或模拟器
- Python LangGraph Agent Server
- 控制能力建议真机验证，并确保 Shizuku 与无障碍服务可用

## 快速启动

推荐方式（Android Studio）：

1. 打开项目根目录
2. 同步 Gradle
3. 配置 Android SDK
4. 连接设备并运行 app 模块
5. 在 App 设置页填写 Agent Server 地址和 `X-Api-Key`

命令行方式：

```bash
gradle :app:assembleDebug
gradle :app:installDebug
```

## 配置说明

Agent Server 配置支持两种方式：

- 运行时在 App 设置页的“Agent 服务”中填写
- 开发期通过 `local.properties` 提供默认值

`local.properties` 示例：

```properties
sdk.dir=你的 Android SDK 路径
AGENT_SERVER_BASE_URL=http://127.0.0.1:8124
AGENT_SERVER_API_KEY=你的 LangSmith API Key
```

注意：

- `local.properties` 不应提交到 Git
- API Key 应只保留在本地开发环境
- Termux 在手机侧运行服务端时，可以在设置页填写手机本机可访问的 HTTP 地址
- 如果需要跨设备调试，运行服务端的机器需要配置Nginx反向代理，将局域网ip:port映射到127.0.0.1:2024（默认）。
- 如果需要在Android Studio里访问本机，需要使用10.0.2.2替代127.0.0.1。
- App 会从 Agent Server 地址自动派生 ADB WebSocket 地址：
  - `http://host:port` -> `ws://host:port/adb`
  - `https://host:port` -> `wss://host:port/adb`

## 权限与能力说明

主要权限用途：

- `INTERNET`：连接 Agent Server、SSE 和 WebSocket
- `SYSTEM_ALERT_WINDOW`：悬浮球与 ADB 交互提示
- `FOREGROUND_SERVICE`：悬浮球、系统连接、ADB WebSocket 前台服务
- `ACCESSIBILITY_SERVICE`：采集当前界面 UI 树与前台应用信息
- 存储与媒体权限：选择图片并发送给 Agent
- 日历、位置、通知等权限：供系统工具协议读取系统能力

控制能力依赖：

- Shizuku 可用并已授权
- 无障碍服务已启用
- Agent Server 已运行并监听 `/adb`

任一条件缺失时，对话本身仍可使用，但手机控制工具可能无法完整执行。

## 协议文档

- `Agent Server API.md`
  LangSmith / LangGraph Agent Server API 索引与鉴权说明
- `ADB工具协议.md`
  Python Agent 与 Android ADB 工具客户端之间的 WebSocket 协议
- `系统应用和API协议.md`
  Python Agent 与 Android 系统能力客户端之间的 WebSocket 协议

## 开发建议

- Chat 模块只负责 Agent Server 通信和事件转换，不直接执行设备动作
- 设备动作由 Python Agent 通过 `/adb` WebSocket 工具协议下发
- 新增 ADB 动作时优先扩展 `ADB工具协议.md` 和 `AdbWebSocketService`
- 新增系统能力时优先扩展 `系统应用和API协议.md`、`SystemProtocolHandler` 和 `SystemApi`
- 流式事件解析应兼容 `messages-tuple`、`updates`、`tasks`、`custom`

## TODO（可选）

- 完善 Agent Server 线程删除、重命名与 metadata 同步策略
- 增强 ADB WebSocket 连接状态在 UI 中的可见性
- 增加 Agent Server / ADB 协议端到端联调测试
- 增加图片大小压缩与上传限制

## 许可证

本项目仅供学习与内部开发使用。
