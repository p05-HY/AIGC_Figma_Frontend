# App 前端任务审计

范围：仅包含 Android/Kotlin 前端。后端仓库未修改。

## 任务完成情况

| 序号 | 任务 | 前端状态 | 证据 / 说明 |
|---|---|---|---|
| 1 | 唯一设备标识（UUID）生成与持久化 | 已完成，并已补强 | `DeviceIdStore` 会生成并持久化 `device_id`，`MainActivity` 启动时初始化。现在普通 HTTP 请求、状态查询、ADB WebSocket、System WebSocket、ADB connect 快照都会携带 `X-Device-Id` / `deviceId`。 |
| 2 | 配合后端接口路径修改 | 前端基本完成，后端契约仍需确认 | `ApiPaths` 已集中维护 LangGraph 和 WebSocket 路径片段。`AgentServerClient` 已用于 `/threads/...` 和 `/adb/{deviceId}`。`SystemWebSocketClient` 已改为使用 `ApiPaths.SYSTEM_WS`，并保留 baseUrl 自带路径前缀。状态查询 HTTP 路径已经包含 deviceId，但后端是否支持这些路径仍需确认。 |
| 3 | 图片压缩与降采样 | 已完成，并已补强 | `AdbSnapshotCollector` 会把截图降采样到长边不超过 1280，并压缩为 WebP/PNG。快照 JSON 现在新增 `screenshotMimeType`，方便后端/模型按正确图片格式解析。 |
| 4 | 缩放系数维护与坐标还原 | 已完成 | `AdbSnapshotCollector` 会更新 `ScreenScaleState`；`AdbWebSocketService` 在执行 `tap`、`swipe`、`longPress`、`doubleTap` 前，会把模型坐标还原到真实设备像素。 |
| 5 | 悬浮球三个状态切换 + 过渡动画 | 已完成 | `FloatingBallService` 实现了球态、输入气泡、小窗、复杂任务等待/完成提示等状态。`FloatingBallView`、`FloatingBubbleInput`、`FloatingChatWindow`、`FloatingBubbleNotification` 实现了淡入、缩放、吸边等动画。 |
| 6 | 图标透明底处理 | 已完成 | `ic_echo_face.png` 和 `ic_avatar_brand.png` 均带 alpha 通道。launcher foreground 的 `ic_echo_face_inset.xml` 已改为引用 `ic_echo_face`，不再引用非透明的 `appicon.png`。 |
| 7 | 对话与工具调用对接 | 前端侧已完成 | `AgentServerClient` 会解析 SSE 的 `messages`、`updates`、`tasks`、`custom/task_progress`；`ChatViewModel` 会把工具进度映射为 `ToolCall`；`ChatBubble` 会渲染工具状态和详情。 |
| 8 | 前端输出优化（折叠/嵌入工具链） | 前端侧已完成 | `ChatBubble` 会展示思考内容和可折叠工具调用详情，包括阶段、说明、入参、出参、错误和步骤进度，并保持结果嵌入在对话流中。 |

## 本次前端改动

- ADB 快照 JSON 增加 `screenshotMimeType` 和 `deviceId`。
- Agent Server 普通 HTTP 请求、状态查询 HTTP 请求、ADB WebSocket、System WebSocket 增加 `X-Device-Id`。
- ADB connect payload 增加 `deviceId`，并兼容旧协议冗余携带 `token`。
- System WebSocket 路径构造现在会保留 baseUrl 中已有的路径前缀。
- launcher foreground 改为使用透明 Echo 图标资源。

## 后端后续事项（本次未修改）

以下是读前后端代码时发现的后端契约风险：

1. 前端默认连接 `/adb/{deviceId}` 和 `/system/{deviceId}`。如果后端当前只注册了 `/adb` 和 `/system`，WebSocket 会连接失败；临时联调可以在 `local.properties` 里设置 `DEVICE_ID_IN_PATH=false`。
2. 前端状态查询会请求 `/adb/{deviceId}/status`、`/system/{deviceId}/status`、`/network/{deviceId}/status`。后端需要确认是否支持这些路由；否则前端需要临时兼容旧路由。
3. 前端截图可能是 WebP。后端向模型注入图片时应使用新的 `screenshotMimeType` 字段，或从图片字节推断 MIME 类型，不能固定按 PNG 处理。

## 真机验证清单

在真实 Android/Kotlin 开发机上按下面步骤验证：

1. 首次启动 App 后退出再重启，确认 `device_identity` SharedPreferences 里的 `device_id` 保持稳定。
2. 配置 Agent Server 地址并启动 ADB/System 服务。`DEVICE_ID_IN_PATH=true` 时，确认 WebSocket URL 为 `/adb/{deviceId}` 和 `/system/{deviceId}`。
3. 如果后端暂未实现 deviceId 路由，在 `local.properties` 设置 `DEVICE_ID_IN_PATH=false`，确认 WebSocket URL 回退为 `/adb` 和 `/system`。
4. 触发一次 `observe`，确认 ADB connect/action JSON 包含 `deviceId`、`screenshot`、`screenshotMimeType`。
5. 在高分辨率设备上触发 observe，确认 connect 上报的 `width`/`height` 是降采样后的截图尺寸，而不是原始屏幕尺寸。
6. 让 Agent 点击屏幕中心附近的可见目标，确认经过 `ScreenScaleState` 坐标还原后，真实点击位置准确。
7. 打开悬浮球，分别验证单击、双击、复杂任务等待、任务完成提示等状态切换。
8. 在深色/浅色背景下检查 launcher 和 App 内 Echo 图标，确认没有不透明方形底。
9. 触发一次后端工具调用，确认对话气泡中能展示运行中、成功、失败状态，可展开详情，并且结果仍嵌入对话流。
