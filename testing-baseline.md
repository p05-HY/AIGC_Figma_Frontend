# P5 测试与发布基线

本项目在 Android Studio 中的提测前一键执行基线如下（不依赖全局 Gradle）。

## 一键任务组合（建议在 Gradle 面板执行）

按顺序执行：

1. `:app:testDebugUnitTest`
2. `:app:connectedDebugAndroidTest`
3. `:app:assembleDebug`

说明：

- 第 1 步覆盖 ViewModel 与核心状态机单测。
- 第 2 步覆盖 Compose UI 冒烟与聊天流关键路径。
- 第 3 步确认可发布的 Debug 包可构建。

## Android Studio 操作步骤

1. 打开右侧 Gradle 工具窗口。
2. 依次展开 `figma_code > app > verification`，运行 `testDebugUnitTest` 与 `connectedDebugAndroidTest`。
3. 再展开 `figma_code > app > build`，运行 `assembleDebug`。
4. 三步全部通过后再提测。

## 当前测试文件清单

- `app/src/test/java/com/example/blueheartv/test/MainDispatcherRule.kt`
- `app/src/test/java/com/example/blueheartv/viewmodel/ChatViewModelTest.kt`
- `app/src/androidTest/java/com/example/blueheartv/MainNavigationSmokeTest.kt`
- `app/src/androidTest/java/com/example/blueheartv/HomeChatFlowUiTest.kt`
