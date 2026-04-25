# Android AI 助手 - 项目合并指南
# 项目代号：BlueHeartV + AdbAgent

---

## 一、你的任务背景

你正在将两个已能独立运行的 Android 项目合并为一个：

| 源项目 | 包名 | 核心能力 |
|--------|------|----------|
| `figma_code` | `com.example.blueheartv` | AI 对话界面，流式回复，悬浮窗，历史持久化（Room） |
| `ADB-Test` | `io.njdldkl.android.adbtest` | 通过 Shizuku/AIDL 执行 shell 命令，AccessibilityService 采集 UI 树，截图回传 |

**合并目标**：在 BlueHeartV（figma_code）里，让 AI 能够理解"控制手机"的意图，并调用 ADB-Test 的执行能力，实现"自然语言 → 手机操作"的闭环。

---

## 二、合并后的目标架构

```
用户在 ChatScreen 输入消息
        ↓
ChatViewModel.sendMessage()
        ↓
[新增] PhoneControlRouter（路由决策层）
   ├─ 判断为"控制指令" → ActionDispatcher → AdbController（来自ADB-Test）→ 执行 → 返回结果文字给对话流
   └─ 判断为"对话指令" → 原有 ChatProvider（SiliconFlow 等）→ 流式回复
        ↓
UI 统一展示结果（复用现有消息气泡）
```

**关键原则**：
- ADB-Test 的 WebSocket 层（`AdbWebSocketClient`）在合并后**不保留**，改为 App 内部直接调用
- figma_code 的 UI/ViewModel 结构**保持不变**，仅在 `ChatViewModel` 的消息分发处插入路由逻辑
- 两套权限体系需要合并到同一个 `AndroidManifest.xml`

---

## 三、两个项目的源码位置

```
# 你工作时，两个源项目的绝对路径如下（请根据实际路径调整）：
SOURCE_FIGMA   = D:\JOINT DEBUGGING\FIGMA_CODE
SOURCE_ADB     = D:\JOINT DEBUGGING\ADB-TEST

# 合并后的新项目根目录（以 figma_code 为基础）：
MERGED_PROJECT = D:\JOINT DEBUGGING\FIGMA_CODE   ← 直接在这里扩展，不新建项目
```

---

## 四、关键文件速查表

### figma_code 核心文件（你需要熟悉）

| 文件 | 路径 | 作用 |
|------|------|------|
| `ChatViewModel.kt` | `app/src/main/java/com/example/blueheartv/viewmodel/` | 消息发送主控，**插入路由的位置在这里** |
| `SiliconFlowChatProvider.kt` | `app/src/main/java/com/example/blueheartv/chat/` | AI 流式调用，路由"对话"分支最终调它 |
| `FloatingBallService.kt` | `app/src/main/java/com/example/blueheartv/floating/` | 悬浮服务，合并后可扩展为也显示控制结果 |
| `MainActivity.kt` | `app/src/main/java/com/example/blueheartv/` | 主入口，需要在这里增加 Shizuku 权限检查 |
| `app/build.gradle.kts` | `app/` | 需要合并 ADB-Test 的依赖 |
| `AndroidManifest.xml` | `app/src/main/` | 需要合并两套权限和 Service 声明 |

### ADB-Test 需要迁移的文件（核心执行层）

| 文件 | 路径 | 迁移后放置位置 | 说明 |
|------|------|---------------|------|
| `AdbService.kt` | `app/src/main/java/io/njdldkl/android/adbtest/adb/` | `com/example/blueheartv/control/` | **重构**：删除 WebSocket 逻辑，保留 `handleRequest`、`runShell`、`typeWithAdbKeyboard` |
| `ShizukuAdbExecutor.kt` | 同上 | `com/example/blueheartv/control/` | **直接迁移**，仅改包名 |
| `RemoteShellUserService.kt` | 同上 | `com/example/blueheartv/control/` | **直接迁移**，仅改包名 |
| `IRemoteShellService.aidl` | `app/src/main/aidl/io/njdldkl/.../adb/` | `app/src/main/aidl/com/example/blueheartv/control/` | **直接迁移**，改 package 声明 |
| `AdbAccessibilityService.kt` | 同上 | `com/example/blueheartv/control/` | **直接迁移**，改包名；XML 配置也需迁移 |
| `AdbSnapshotCollector.kt` | 同上 | `com/example/blueheartv/control/` | **直接迁移** |
| `Protocol.kt` | 同上 | `com/example/blueheartv/control/` | **直接迁移** |
| `AdbOverlayController.kt` | 同上 | `com/example/blueheartv/control/` | **直接迁移** |

### ADB-Test 不需要迁移的文件

| 文件 | 原因 |
|------|------|
| `AdbWebSocketClient.kt` | 合并后用 App 内部调用替代，不需要 WebSocket 层 |
| `MainActivity.kt`（ADB-Test 的） | 丢弃，统一用 figma_code 的 MainActivity |
| `TermuxCommandRunner.kt` | 暂不迁移，阶段一不包含 Termux 功能 |
| `TermuxCommandResultService.kt` | 同上 |
| `ui/theme/*`（ADB-Test 的） | 丢弃，统一用 figma_code 的主题 |

---

## 五、分阶段任务清单

### ✅ 阶段 0：前置检查（动代码前必须完成）

```
[ ] 0.1 在 figma_code 根目录执行 git add -A && git commit -m "merge-baseline"
         确保有干净的回滚点，后续每阶段完成都要提交一次
[ ] 0.2 确认 figma_code 能正常编译：./gradlew assembleDebug
[ ] 0.3 记录当前 figma_code 的 compileSdk / minSdk 版本（在 app/build.gradle.kts 查看）
[ ] 0.4 记录 ADB-Test 的 compileSdk / minSdk 版本（对比是否一致）
```

---

### 🔧 阶段 1：迁移执行层代码（仅复制+改包名，不改逻辑）

**目标**：把 ADB-Test 的核心执行文件搬进 figma_code，确保能编译通过。

**步骤**：

1. 在 figma_code 的 `app/src/main/java/com/example/blueheartv/` 下新建包 `control/`

2. 将以下文件从 ADB-Test 复制过来，并将文件顶部的 `package io.njdldkl.android.adbtest.adb` 改为 `package com.example.blueheartv.control`：
   - `AdbAccessibilityService.kt`
   - `AdbOverlayController.kt`
   - `AdbSnapshotCollector.kt`
   - `Protocol.kt`
   - `RemoteShellUserService.kt`
   - `ShizukuAdbExecutor.kt`

3. 新建一个精简版 `AdbController.kt`（**不是直接复制 AdbService.kt**），内容如下：

```kotlin
package com.example.blueheartv.control

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AdbController 是合并后 App 内部调用 ADB 执行能力的统一入口。
 * 原 ADB-Test 的 AdbService 通过 WebSocket 接收指令；
 * 这里改为直接函数调用，供 ChatViewModel 的路由层使用。
 */
class AdbController(private val context: Context) {

    private val executor = ShizukuAdbExecutor(context)

    /** 执行一条 shell 命令，返回 stdout 或错误信息 */
    suspend fun runShell(command: String): String = withContext(Dispatchers.IO) {
        try {
            val result = executor.execute(command)
            result.stdout.ifBlank { result.stderr }
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }

    /** 模拟点击屏幕坐标 */
    suspend fun tap(x: Int, y: Int): String =
        runShell("input tap $x $y")

    /** 模拟滑动 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): String =
        runShell("input swipe $x1 $y1 $x2 $y2 $durationMs")

    /** 输入文字（需要 ADB Keyboard 已安装） */
    suspend fun typeText(text: String): String =
        runShell("am broadcast -a ADB_INPUT_TEXT --es msg '${text.replace("'", "\\'")}'")

    /** 模拟按键（keycode 参考 Android KeyEvent） */
    suspend fun pressKey(keycode: Int): String =
        runShell("input keyevent $keycode")

    /** 启动应用 */
    suspend fun launchApp(packageName: String): String =
        runShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")

    /** 获取当前屏幕 UI 树（XML 文本），供 AI 识别元素位置 */
    suspend fun dumpUiTree(): String = withContext(Dispatchers.IO) {
        try {
            val snapshot = AdbSnapshotCollector(context).collect()
            snapshot.uiTree ?: "无法获取 UI 树"
        } catch (e: Exception) {
            "UI 树采集失败: ${e.message}"
        }
    }

    fun destroy() {
        executor.destroy()
    }
}
```

4. 迁移 AIDL 文件：将 `IRemoteShellService.aidl` 复制到
   `app/src/main/aidl/com/example/blueheartv/control/IRemoteShellService.aidl`，
   修改文件内第一行 package 声明为 `package com.example.blueheartv.control;`

5. 将 ADB-Test 的 `app/src/main/res/xml/adb_accessibility_service.xml` 复制到 figma_code 的同路径

6. **编译验证**：`./gradlew compileDebugKotlin`，修复所有 import 错误（只改 import 路径，不改逻辑）

7. `git commit -m "feat: migrate adb control layer"`

---

### 🔧 阶段 2：合并 build.gradle.kts 和 AndroidManifest.xml

**目标**：让项目能引用 Shizuku、OkHttp 等 ADB-Test 的依赖，并声明必要权限。

**步骤**：

1. 打开 figma_code 的 `app/build.gradle.kts`，在 `dependencies {}` 中添加（参考 ADB-Test 的 build.gradle.kts 确认版本号）：

```kotlin
// Shizuku（从 ADB-Test 迁移）
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")

// OkHttp（figma_code 可能已有，检查是否重复）
// implementation("com.squareup.okhttp3:okhttp:4.x.x")  // 按需添加

// 确认 aidl 编译已启用
```

同时在 `android {}` 块内确认启用了 aidl：
```kotlin
buildFeatures {
    compose = true
    aidl = true   // ← 添加这行
}
```

2. 打开 figma_code 的 `AndroidManifest.xml`，从 ADB-Test 的 Manifest 中合并以下内容：

```xml
<!-- 权限（合并到 <manifest> 层级） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="com.termux.permission.RUN_COMMAND" />
<queries>
    <package android:name="com.termux" />
</queries>

<!-- Shizuku Provider（合并到 <application> 层级） -->
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

<!-- AccessibilityService（合并到 <application> 层级，注意 name 要用新包名） -->
<service
    android:name=".control.AdbAccessibilityService"
    android:exported="true"
    android:label="AI 助手控制服务"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/adb_accessibility_service" />
</service>
```

3. **编译验证**：`./gradlew assembleDebug`，修复依赖冲突

4. `git commit -m "feat: merge manifest and dependencies"`

---

### 🔧 阶段 3：新增路由决策层

**目标**：在 `ChatViewModel` 发消息的路径上插入一个路由器，判断是"控制指令"还是"对话指令"。

**新建文件** `app/src/main/java/com/example/blueheartv/control/PhoneControlRouter.kt`：

```kotlin
package com.example.blueheartv.control

import android.content.Context

/**
 * 路由决策层：判断用户输入是否为手机控制指令。
 *
 * 策略（阶段3用简单关键词，后续可升级为让 AI 返回 JSON tool_call）：
 * - 返回 ControlAction 表示控制指令
 * - 返回 null 表示普通对话，走原有 AI Provider
 */
object PhoneControlRouter {

    sealed class ControlAction {
        data class Tap(val x: Int, val y: Int) : ControlAction()
        data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : ControlAction()
        data class LaunchApp(val packageName: String) : ControlAction()
        data class PressKey(val keycode: Int) : ControlAction()
        data class TypeText(val text: String) : ControlAction()
        data class RunShell(val command: String) : ControlAction()
        object GoHome : ControlAction()
        object GoBack : ControlAction()
        object DumpScreen : ControlAction()  // 让 AI 看当前屏幕
    }

    /**
     * 分析用户输入，返回 ControlAction 或 null（走对话分支）。
     * 注意：这里的关键词匹配仅作为阶段3的快速验证；
     * 阶段4将改为把屏幕快照一起发给 AI，让 AI 决定返回结构化动作。
     */
    fun parse(userInput: String): ControlAction? {
        val input = userInput.trim()

        return when {
            input.startsWith("执行shell:") || input.startsWith("shell:") ->
                ControlAction.RunShell(input.substringAfter(":").trim())

            input.contains("返回桌面") || input.contains("回到主页") ->
                ControlAction.GoHome

            input.contains("返回上一页") || input.contains("按返回键") ->
                ControlAction.GoBack

            input.startsWith("打开应用") || input.startsWith("启动应用") -> {
                val pkg = input.substringAfter("应用").trim()
                if (pkg.isNotBlank()) ControlAction.LaunchApp(pkg) else null
            }

            input.startsWith("点击坐标") -> {
                // 格式："点击坐标 540 1200"
                val parts = input.removePrefix("点击坐标").trim().split(" ")
                if (parts.size >= 2) {
                    val x = parts[0].toIntOrNull()
                    val y = parts[1].toIntOrNull()
                    if (x != null && y != null) ControlAction.Tap(x, y) else null
                } else null
            }

            input.contains("截图") || input.contains("看一下屏幕") || input.contains("当前界面") ->
                ControlAction.DumpScreen

            else -> null  // 普通对话
        }
    }
}
```

**修改 `ChatViewModel.kt`**，在 `sendMessage` / `submitPrompt` 函数中插入路由：

找到 `ChatViewModel` 里调用 `startStreamingReply` 或 `ChatProvider.streamReply` 的位置，在它**之前**插入：

```kotlin
// 在 ChatViewModel 中注入 AdbController（通过 Koin 或构造函数）
// 示例：private val adbController: AdbController by inject()

private fun handleUserMessage(userInput: String) {
    val controlAction = PhoneControlRouter.parse(userInput)
    if (controlAction != null) {
        // 控制分支：执行后把结果当作 AI 回复插入对话
        viewModelScope.launch {
            val resultText = executeControlAction(controlAction)
            // 将 resultText 插入为一条 role=assistant 的消息
            appendAssistantMessage("[手机操作结果]\n$resultText")
        }
    } else {
        // 对话分支：原有逻辑不变
        startStreamingReply(userInput)
    }
}

private suspend fun executeControlAction(action: PhoneControlRouter.ControlAction): String {
    return when (action) {
        is PhoneControlRouter.ControlAction.GoHome -> adbController.pressKey(3)   // KEYCODE_HOME
        is PhoneControlRouter.ControlAction.GoBack -> adbController.pressKey(4)   // KEYCODE_BACK
        is PhoneControlRouter.ControlAction.Tap -> adbController.tap(action.x, action.y)
        is PhoneControlRouter.ControlAction.LaunchApp -> adbController.launchApp(action.packageName)
        is PhoneControlRouter.ControlAction.RunShell -> adbController.runShell(action.command)
        is PhoneControlRouter.ControlAction.DumpScreen -> adbController.dumpUiTree()
        is PhoneControlRouter.ControlAction.TypeText -> adbController.typeText(action.text)
        is PhoneControlRouter.ControlAction.Swipe ->
            adbController.swipe(action.x1, action.y1, action.x2, action.y2)
        is PhoneControlRouter.ControlAction.PressKey -> adbController.pressKey(action.keycode)
    }
}
```

**编译验证**：`./gradlew assembleDebug`

`git commit -m "feat: add phone control router and integrate with ChatViewModel"`

---

### 🔧 阶段 4：权限引导 UI

**目标**：在 `MainActivity` 启动时检查 Shizuku 和 AccessibilityService 权限，引导用户开启。

参考 ADB-Test 的 `MainActivity.kt` 里权限检查逻辑，在 figma_code 的 `MainActivity.kt` 中增加：

```kotlin
// 在 onCreate 或 NavGraph 入口处，增加权限状态检查
val isShizukuAvailable = Shizuku.pingBinder()
val isAccessibilityEnabled = isAccessibilityServiceEnabled(context, AdbAccessibilityService::class.java)

if (!isShizukuAvailable || !isAccessibilityEnabled) {
    // 弹出引导 Dialog，复用 figma_code 已有的 DialogUtil
    // 告知用户需要开启 Shizuku 和无障碍服务
}
```

`git commit -m "feat: add permission guard for control features"`

---

### 🔧 阶段 5：端到端测试

按顺序验证以下场景，每个场景若失败则读取 logcat 分析：

```bash
# 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 查看日志（过滤关键标签）
adb logcat | grep -E "BlueHeartV|AdbController|PhoneControlRouter|ShizukuAdbExecutor"
```

测试用例：
1. 打开 App，对话框正常显示 → 说明 figma_code UI 完好
2. 发送"今天天气如何" → AI 正常流式回复 → 说明对话分支没有被破坏
3. 发送"返回桌面" → 手机回到桌面 → 说明控制分支基本可用
4. 发送"执行shell: ls /sdcard" → 返回文件列表 → 说明 shell 执行链路通
5. 发送"当前界面" → 返回 UI 树 XML 文本 → 说明截图/UI 采集可用

---

## 六、已知冲突点和处理方式

| 冲突点 | 说明 | 处理方式 |
|--------|------|----------|
| 包名冲突 | 两个项目包名完全不同 | 以 figma_code 的 `com.example.blueheartv` 为准，迁移的文件全部改包名 |
| Shizuku 版本 | 两个项目可能引用版本不同 | 以 ADB-Test 的版本为准（它依赖更重），检查 `settings.gradle.kts` 里有无版本目录 |
| OkHttp 版本 | figma_code 已有 OkHttp（SSE 用），ADB-Test 也有 | 统一用较新的版本，删除重复声明 |
| AIDL 包路径 | AIDL 文件的 package 声明必须和文件路径完全一致 | 迁移时同步修改文件内的 package 和目录结构 |
| `minSdk` 不一致 | 两个项目的 minSdk 可能不同 | 取两者中**较高值**，以免低版本 API 编译报错 |
| `AdbService` 的 WebSocket 依赖 | 原 `AdbService.kt` 强依赖 `AdbWebSocketClient` | **不直接迁移 AdbService**，改用阶段1里新写的精简版 `AdbController.kt` |

---

## 七、不要做的事（避坑清单）

1. **不要**直接把 ADB-Test 整个目录 copy 进来合并——两套 Manifest 和两套 Application 类会冲突
2. **不要**把 `AdbWebSocketClient.kt` 迁移进来——合并后不再需要 WebSocket 服务端
3. **不要**一次性修改超过 3 个文件后再编译——遇到问题无法定位
4. **不要**跳过编译验证直接进入下一阶段——每阶段必须 `assembleDebug` 通过
5. **不要**在 `ChatViewModel` 里直接 `new AdbController()`——应通过 Koin DI 注入（与现有架构保持一致）
6. **不要**在主线程调用 `AdbController` 的任何函数——必须在协程 `Dispatchers.IO` 里执行

---

## 八、编译与调试命令

```bash
# 在 figma_code 项目根目录执行

# 编译（仅编译，不安装）
./gradlew assembleDebug

# 仅 Kotlin 编译（快速检查语法错误）
./gradlew compileDebugKotlin

# 安装到已连接设备
./gradlew installDebug

# 清理构建缓存（遇到奇怪编译错误时用）
./gradlew clean

# 查看依赖树（排查版本冲突）
./gradlew app:dependencies --configuration debugRuntimeClasspath

# 实时日志
adb logcat -c && adb logcat | grep -E "BlueHeartV|AdbController|ShizukuAdbExecutor|PhoneControlRouter"
```

---

## 九、架构注意事项

- `AdbController` 是有状态对象（持有 `ShizukuAdbExecutor` 连接），应作为**单例**通过 Koin 注册，生命周期绑定 Application
- `AdbAccessibilityService` 是系统服务，**不能**在 App 内主动 start，只能引导用户去无障碍设置里手动开启
- Shizuku 的 `awaitService()` 是耗时操作，必须在协程中调用，禁止阻塞 UI 线程
- 所有 shell 命令执行结果需在 UI 上给用户明确反馈，避免"发出去没反应"的黑盒感

---

## 十、进度检查点

| 阶段 | 验证标准 | 完成标志 |
|------|----------|----------|
| 阶段0 | `git log` 能看到 baseline commit | ✅ / ❌ |
| 阶段1 | `compileDebugKotlin` 无报错 | ✅ / ❌ |
| 阶段2 | `assembleDebug` 无报错 | ✅ / ❌ |
| 阶段3 | 安装后发"返回桌面"能触发 `GoHome` 日志 | ✅ / ❌ |
| 阶段4 | 启动 App 能看到权限引导弹窗 | ✅ / ❌ |
| 阶段5 | 全部 5 个测试用例通过 | ✅ / ❌ |