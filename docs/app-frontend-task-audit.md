# App Frontend Task Audit

Scope: Android/Kotlin frontend only. Backend repository is not modified.

## Task Status

| # | Task | Frontend status | Evidence / Notes |
|---|---|---|---|
| 1 | Unique device UUID generation and persistence | Done, strengthened | `DeviceIdStore` generates and persists `device_id`; `MainActivity` initializes it. HTTP requests, status requests, ADB WebSocket headers, and ADB connect snapshot now also carry `X-Device-Id` / `deviceId`. |
| 2 | Align frontend API paths with backend paths | Mostly done, backend contract needs confirmation | `ApiPaths` centralizes LangGraph and WebSocket path segments. `AgentServerClient` uses it for `/threads/...` and `/adb/{deviceId}`. `SystemWebSocketClient` now uses `ApiPaths.SYSTEM_WS` and preserves any baseUrl path prefix. Status HTTP paths already include deviceId, but backend route support must be confirmed. |
| 3 | Screenshot compression and downsampling | Done, strengthened | `AdbSnapshotCollector` downsamples screenshots to max long edge 1280 and compresses as WebP/PNG. Snapshot JSON now includes `screenshotMimeType` so the backend/model can decode the compressed image correctly. |
| 4 | Scale factor maintenance and coordinate restoration | Done | `AdbSnapshotCollector` updates `ScreenScaleState`; `AdbWebSocketService` converts `tap`, `swipe`, `longPress`, and `doubleTap` model coordinates back to real device pixels before shell execution. |
| 5 | Floating ball states and transition animations | Done | `FloatingBallService` has ball/input bubble/chat window/complex-task states. `FloatingBallView`, `FloatingBubbleInput`, `FloatingChatWindow`, and `FloatingBubbleNotification` implement fade/scale/snap animations. |
| 6 | Echo icon transparent background | Done | `ic_echo_face.png` and `ic_avatar_brand.png` have alpha channels. Launcher foreground `ic_echo_face_inset.xml` now references `ic_echo_face` instead of the non-alpha `appicon.png`. |
| 7 | Dialogue and tool-call integration | Done from frontend side | `AgentServerClient` parses SSE `messages`, `updates`, `tasks`, and `custom/task_progress`; `ChatViewModel` maps tool progress into `ToolCall`; `ChatBubble` renders tool status/details. |
| 8 | Frontend output optimization for collapsible/embedded tool chain | Done from frontend side | `ChatBubble` shows thinking content and collapsible tool-call detail rows with args/result/error/progress steps, keeping the result embedded in the chat stream. |

## Frontend Changes Made

- Added `screenshotMimeType` and `deviceId` to ADB snapshot JSON.
- Added `X-Device-Id` to Agent Server HTTP requests, status HTTP requests, ADB WebSocket, and System WebSocket.
- Added `deviceId` and legacy-compatible `token` to ADB connect payload.
- Made System WebSocket path construction preserve baseUrl path prefixes.
- Switched launcher foreground inset to the transparent Echo icon resource.

## Backend Follow-Up, Not Modified

These are backend contract risks found while reading both sides:

1. Frontend defaults to `/adb/{deviceId}` and `/system/{deviceId}`. If backend only registers `/adb` and `/system`, WebSocket connection will fail unless `DEVICE_ID_IN_PATH=false` is set in `local.properties`.
2. Frontend status client calls `/adb/{deviceId}/status`, `/system/{deviceId}/status`, and `/network/{deviceId}/status`. Backend must confirm these routes or frontend needs a temporary compatibility fallback.
3. Frontend screenshots can be WebP. Backend/model injection should use the new `screenshotMimeType` field, or infer the MIME type from bytes, instead of assuming PNG.

## Manual Device Verification Checklist

Run these on the real Android/Kotlin development machine:

1. Start the app once, then restart it. Confirm `device_id` stays stable in `device_identity` SharedPreferences.
2. Configure Agent Server base URL and start ADB/System services. Confirm WebSocket URLs are `/adb/{deviceId}` and `/system/{deviceId}` when `DEVICE_ID_IN_PATH=true`.
3. If backend has not implemented device-id routes yet, set `DEVICE_ID_IN_PATH=false` in `local.properties` and confirm WebSocket URLs fall back to `/adb` and `/system`.
4. Trigger an `observe` command. Confirm the ADB connect/action JSON contains `deviceId`, `screenshot`, and `screenshotMimeType`.
5. On a high-resolution device, trigger observe and confirm the reported connect `width`/`height` match the downsampled screenshot dimensions, not the original screen dimensions.
6. Ask the agent to tap a visible target near screen center. Confirm the real tap lands correctly after `ScreenScaleState` coordinate restoration.
7. Open floating ball and exercise single tap, double tap, complex-task waiting, and completion notification transitions.
8. Check launcher and in-app Echo icons against dark/light backgrounds; the Echo face should not show an opaque square background.
9. Trigger a backend tool call and confirm the chat bubble shows running/completed/failed status, expandable details, and embedded result without leaving the conversation flow.
