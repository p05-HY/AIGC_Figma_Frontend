# Project Instructions

## Scope

These instructions apply to the whole repository.

This is an Android Kotlin project for Echo, an AI agent frontend client. The app connects to a Python LangGraph Agent Server for chat, streaming responses, tool status, and thread history. Device-control capabilities are exposed back to the agent through WebSocket services.

## Tech Stack

- Kotlin with Gradle Kotlin DSL.
- Android application module: `:app`.
- Jetpack Compose and Material3 for UI.
- MVVM with ViewModel and StateFlow.
- Koin for dependency injection.
- OkHttp for HTTP, SSE, and WebSocket.
- Room remains in the codebase for local entities/tests, but long-term chat history is expected to come from Agent Server thread APIs.
- Shizuku, AIDL, AccessibilityService, and foreground services support phone-control workflows.

## Key Entry Points

- `app/src/main/java/com/example/blueheartv/BlueHeartVApplication.kt`: application startup, Koin, notification channels, crash logging.
- `app/src/main/java/com/example/blueheartv/MainActivity.kt`: Agent Server default config, service startup, permission prompts, and Compose root.
- `app/src/main/java/com/example/blueheartv/navigation/NavGraph.kt`: top-level navigation.
- `app/src/main/java/com/example/blueheartv/chat/AgentServerChatProvider.kt`: Agent Server chat integration.
- `app/src/main/java/com/example/blueheartv/chat/AgentServerClient.kt`: HTTP/SSE client behavior.
- `app/src/main/java/com/example/blueheartv/viewmodel/ChatViewModel.kt`: chat UI state and streaming lifecycle.
- `app/src/main/java/com/example/blueheartv/control/AdbWebSocketService.kt`: ADB tool WebSocket client.
- `app/src/main/java/com/example/blueheartv/system/SystemService.kt`: system capability WebSocket client.
- `app/src/main/AndroidManifest.xml`: app permissions, services, Shizuku provider, accessibility service.

## Directory Map

- `app/src/main/java/com/example/blueheartv/chat`: Agent Server configuration, providers, client, protocol event mapping.
- `app/src/main/java/com/example/blueheartv/viewmodel`: conversation state, session cache, repositories, ViewModels.
- `app/src/main/java/com/example/blueheartv/ui`: Compose screens, components, and theme.
- `app/src/main/java/com/example/blueheartv/control`: phone-control protocol, WebSocket service, Shizuku shell execution, screen snapshots, accessibility.
- `app/src/main/java/com/example/blueheartv/system`: system API protocol and WebSocket integration.
- `app/src/main/java/com/example/blueheartv/floating`: floating ball service and floating chat UI.
- `app/src/main/java/com/example/blueheartv/db`: Room entities/DAO/mappers.
- `app/src/test`: local JVM tests.
- `app/src/androidTest`: instrumentation and Compose UI tests.

## Development Rules

- Prefer existing module boundaries. Chat code should translate Agent Server events and should not directly execute device actions.
- Device actions should flow through the ADB WebSocket protocol and `control` package.
- System capability changes should update `system` package code and the corresponding protocol documentation when present.
- Keep Compose UI state in ViewModels or local composable state according to existing patterns; use `StateFlow` for shared async state.
- Use Koin module wiring in `app/src/main/java/com/example/blueheartv/di/AppModule.kt` for injectable app dependencies.
- Do not commit secrets. Keep `local.properties`, API keys, SDK paths, signing files, and `.env*` files local.
- When adding Agent Server defaults, read from Gradle `BuildConfig` fields or runtime settings rather than hard-coding secrets.
- Keep Android permissions intentional and update `AndroidManifest.xml` only when the feature requires it.

## Commands

Run commands from the repository root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Use `:app:connectedDebugAndroidTest` only when a device or emulator is connected.

## Verification Expectations

- For chat, protocol parsing, repositories, and ViewModels, add or update local tests under `app/src/test`.
- For Compose navigation or user-visible flow changes, add or update instrumentation tests under `app/src/androidTest` when practical.
- Before handing off a code change, run the narrowest relevant Gradle task. If Android SDK, Gradle download, or device availability blocks verification, report the exact blocker.

