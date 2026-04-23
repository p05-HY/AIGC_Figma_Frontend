# AIGC_Figma_Frontend

超级蓝心小V —— 一款基于 Jetpack Compose 构建的 Android AI 智能助手应用，支持多模型对话、流式响应、悬浮球交互和本地会话持久化。

## 项目定位

本项目是 AIGC + Figma 设计稿驱动的 Android 前端实现，目标是打造一个系统级 AI 智能体客户端，具备聊天对话、屏幕辅助、日程管理等能力。当前版本已实现核心聊天功能和基础 UI 框架。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material3 |
| 架构模式 | MVVM (ViewModel + StateFlow) |
| 依赖注入 | Koin 3.5 |
| 本地数据库 | Room 2.6 (KSP) |
| 网络请求 | OkHttp3 4.12 |
| 导航 | Navigation Compose 2.7 |
| 构建工具 | Gradle 8.13 (Kotlin DSL) |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 34 (Android 14) |

## 项目结构

```
app/src/main/java/com/example/blueheartv/
├── chat/                  # 聊天服务层（多 Provider 架构）
│   ├── ChatProvider.kt          # 聊天接口定义
│   ├── SiliconFlowChatProvider  # SiliconFlow API 实现
│   ├── UniAixSdkChatProvider    # UniAix SDK 实现
│   ├── FakeStreamingChatProvider# 测试用模拟 Provider
│   └── ChatSessionStore.kt     # 会话存储
├── db/                    # Room 数据库（Entity / DAO / Database）
├── di/                    # Koin 依赖注入模块
├── floating/              # 悬浮球服务（前台 Service + 悬浮窗）
├── model/                 # 数据模型（Message, ChatHistory, ToolCall）
├── navigation/            # 导航图与路由定义
├── telemetry/             # 事件日志
├── ui/
│   ├── components/        # 可复用 UI 组件
│   │   ├── ChatBubble.kt        # 聊天气泡
│   │   ├── BottomInputBar.kt    # 底部输入栏
│   │   ├── ActionToolbar.kt     # 操作工具栏
│   │   ├── HistoryDrawer.kt     # 历史记录抽屉
│   │   ├── FloatingWidget.kt    # 悬浮组件
│   │   ├── SmartCard.kt         # 智能推荐卡片
│   │   ├── TopBar.kt            # 顶部导航栏
│   │   └── ...
│   ├── screens/           # 页面
│   │   ├── HomeScreen.kt        # 主聊天页
│   │   ├── SettingsScreen.kt    # 设置页
│   │   └── SettingsDetailScreen # 设置详情页
│   └── theme/             # 主题（颜色、字体、深色模式）
├── util/                  # 工具类（权限、弹窗、Toast）
├── viewmodel/             # ViewModel 与 Repository
├── BlueHeartVApplication.kt     # Application 入口
└── MainActivity.kt              # 主 Activity
```

## 核心功能

- **AI 对话**：支持多 LLM Provider 切换，流式响应实时显示
- **会话管理**：多会话创建、切换、置顶、重命名，Room 持久化存储
- **Tool Calling**：支持函数调用追踪与状态展示
- **悬浮球**：前台 Service 驱动的悬浮窗，支持快捷操作菜单
- **智能推荐**：首页卡片式快捷入口（行程、天气、快递等）
- **主题切换**：浅色 / 深色 / 跟随系统
- **设置系统**：个人信息、通知、隐私、存储管理等完整设置页

> 部分功能（截图识别、快速翻译、内容总结、语音输入）目前处于开发中状态。

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 34
- Gradle 8.13

## 配置

项目通过 `local.properties` 配置 API 密钥（该文件不会被提交到 Git）：

```properties
sdk.dir=/path/to/your/Android/SDK
SILICONFLOW_API_KEY=your_api_key_here
SILICONFLOW_MODEL=deepseek-ai/DeepSeek-R1-Distill-Qwen-32B
```

## 构建与运行

```bash
# 编译调试版本
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug

# 编译发布版本
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行 Instrumented 测试
./gradlew connectedAndroidTest
```

也可以直接在 Android Studio 中打开项目，选择设备后点击 Run 运行。

## 权限说明

应用声明了以下权限：

| 权限 | 用途 |
|------|------|
| INTERNET | 网络请求（AI 对话） |
| SYSTEM_ALERT_WINDOW | 悬浮球显示 |
| FOREGROUND_SERVICE | 悬浮球后台服务 |
| ACCESS_FINE/COARSE_LOCATION | 天气与出行建议 |
| CAMERA | 拍照识别（开发中） |
| READ/WRITE_CALENDAR | 日程读取 |
| POST_NOTIFICATIONS | 消息推送 |
| RECORD_AUDIO | 语音输入（开发中） |

## 许可证

本项目仅供学习与内部开发使用。
