# AIGC_Figma_Frontend

超级蓝心小V（BlueHeartV）是一款 Android AI 智能助手客户端。
V2 版本在原有多模型对话能力基础上，融合了手机控制执行链路，实现“自然语言对话 + 设备操作反馈”的一体化体验。

## 项目简介

本项目面向 AIGC 场景与移动端智能体交互，核心目标是：
- 提供稳定的多模型流式对话体验
- 支持会话历史持久化与多会话管理
- 支持悬浮球快捷交互
- 在 V2 中新增手机控制能力（Shizuku + ADB 指令 + 无障碍快照）

## V2 更新说明（融合外部项目能力）

V2 主要改动：
- 新增控制模块目录：app/src/main/java/com/example/blueheartv/control
- 新增控制路由：PhoneControlRouter（识别用户控制意图）
- 新增执行入口：AdbController（统一封装 shell、按键、点击、滑动等）
- 新增 AIDL 接口与无障碍服务声明
- Manifest 与依赖已合并 Shizuku、AccessibilityService 相关配置

融合结果：从“聊天应用”升级为“可执行动作的 AI 助手前端”。

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose + Material3
- 架构：MVVM（ViewModel + StateFlow）
- 依赖注入：Koin
- 本地存储：Room + KSP
- 网络：OkHttp
- 系统控制：Shizuku + AIDL + AccessibilityService
- 构建：Gradle Kotlin DSL

## 核心能力

- 多模型对话与流式输出
- 多会话管理与本地历史持久化
- 悬浮球服务与快捷入口
- 工具调用状态展示
- 手机控制执行（V2）
	- 执行 shell
	- 模拟按键、点击、滑动、文本输入
	- 启动应用
	- 采集当前界面 UI 树

## 项目结构说明

- app/src/main/java/com/example/blueheartv/chat
	聊天 Provider 抽象与具体实现
- app/src/main/java/com/example/blueheartv/viewmodel
	聊天与会话主业务状态管理
- app/src/main/java/com/example/blueheartv/ui
	Compose 页面与组件
- app/src/main/java/com/example/blueheartv/floating
	悬浮球前台服务
- app/src/main/java/com/example/blueheartv/control
	V2 控制执行层（路由、执行器、快照采集、无障碍服务）
- app/src/main/java/com/example/blueheartv/db
	Room 数据库实体与 DAO
- app/src/main/aidl/com/example/blueheartv/control
	远程 shell 接口定义
- app/src/main/res/xml/adb_accessibility_service.xml
	无障碍服务配置

## 环境要求

- Android Studio（建议最新稳定版）
- JDK 17 或更高
- Android SDK 34
- 可用 Android 设备或模拟器（控制能力建议真机验证）

## 快速启动

推荐方式（Android Studio）：
1. 打开项目根目录
2. 同步 Gradle
3. 配置 local.properties 中必要参数
4. 连接设备并运行 app 模块

命令行方式（可选）：

```bash
gradlew.bat assembleDebug
gradlew.bat installDebug
```

## 配置说明

local.properties 示例：

```properties
sdk.dir=你的 Android SDK 路径
SILICONFLOW_API_KEY=你的密钥
SILICONFLOW_MODEL=deepseek-ai/DeepSeek-R1-Distill-Qwen-32B
```

注意：
- local.properties 不应提交到 Git
- API 密钥应只保留在本地开发环境

## 权限与能力说明

主要权限用途：
- INTERNET：模型请求
- SYSTEM_ALERT_WINDOW：悬浮球
- FOREGROUND_SERVICE：前台服务
- ACCESSIBILITY_SERVICE：界面元素采集与辅助控制
- 存储与媒体权限：文件与内容访问相关场景

V2 控制能力依赖：
- Shizuku 可用
- 无障碍服务已启用

若任一条件缺失，控制相关指令将无法完整执行。

## 开发建议

- 将聊天逻辑与控制逻辑保持解耦
- 新增控制动作时优先扩展 PhoneControlRouter 与 AdbController
- 所有控制执行必须在后台线程进行，避免阻塞 UI

## TODO（可选）

- 控制指令由关键词匹配升级为结构化工具调用
- 增强异常提示与权限引导闭环
- 增加端到端自动化验证脚本
- 完善融合版本变更日志

## 许可证

本项目仅供学习与内部开发使用。
