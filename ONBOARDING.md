# Onboarding Guide: BlueHeartV

## Overview

BlueHeartV is the Android Kotlin project for Echo, an AI agent frontend client. The app handles mobile UI, permissions, local device capabilities, and WebSocket tool channels while a Python LangGraph Agent Server owns model interaction, thread state, tool execution planning, and streaming responses.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| Build | Gradle Kotlin DSL, Android Gradle Plugin |
| UI | Jetpack Compose, Material3 |
| Architecture | MVVM, ViewModel, StateFlow |
| Dependency injection | Koin |
| Network | OkHttp HTTP/SSE/WebSocket |
| Local persistence | SharedPreferences, Room |
| Device control | Shizuku, AIDL, AccessibilityService, foreground services |
| Tests | JUnit, coroutine test, AndroidX instrumentation, Compose UI tests |

## Architecture

The app is a single Android application module, `:app`.

`MainActivity` initializes default Agent Server configuration, starts background WebSocket services when configured, checks required permissions, and mounts the Compose navigation graph. `BlueHeartVApplication` initializes Koin, notification channels, and app-level crash logging.

Chat input and streaming state flow through the UI and `ChatViewModel` into a `ChatProvider`. The Agent Server provider talks to LangGraph Agent Server APIs and maps streaming events into UI state. Device-control tools are separate: the Python agent connects to Android through ADB and system WebSocket protocols handled by the `control` and `system` packages.

## Key Entry Points

- `settings.gradle.kts`: includes `:app` and configures repositories.
- `build.gradle.kts`: root plugin versions.
- `app/build.gradle.kts`: Android SDK levels, BuildConfig defaults, dependencies, Compose, AIDL, KSP.
- `app/src/main/AndroidManifest.xml`: permissions, services, provider, accessibility metadata.
- `app/src/main/java/com/example/blueheartv/MainActivity.kt`: app launch and Compose root.
- `app/src/main/java/com/example/blueheartv/BlueHeartVApplication.kt`: application initialization.
- `app/src/main/java/com/example/blueheartv/navigation/NavGraph.kt`: navigation graph.

## Request Lifecycle

1. User sends text or image input from Compose UI.
2. `ChatViewModel` updates local UI state and delegates to the active `ChatProvider`.
3. `AgentServerChatProvider` and `AgentServerClient` call the LangGraph Agent Server.
4. SSE stream events are parsed from `messages-tuple`, `updates`, `tasks`, and `custom` stream modes.
5. `ChatViewModel` merges text deltas, node state, and tool status into observable UI state.
6. If the agent needs phone-control tools, it uses the ADB WebSocket channel implemented in `AdbWebSocketService`.

## Common Tasks

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` requires a connected emulator or physical device. Device-control verification also requires Shizuku authorization and the app accessibility service to be enabled.

## Local Configuration

Use `local.properties` for machine-local values:

```properties
sdk.dir=/path/to/android/sdk
AGENT_SERVER_BASE_URL=http://127.0.0.1:8124
AGENT_SERVER_API_KEY=your-local-key
DEVICE_ID_IN_PATH=true
```

`local.properties` is ignored by Git and must not contain shared secrets.

## Where To Look

| Goal | Start Here |
| --- | --- |
| Change chat networking | `app/src/main/java/com/example/blueheartv/chat` |
| Change streaming UI state | `app/src/main/java/com/example/blueheartv/viewmodel/ChatViewModel.kt` |
| Add or modify a Compose screen | `app/src/main/java/com/example/blueheartv/ui/screens` |
| Add a reusable UI component | `app/src/main/java/com/example/blueheartv/ui/components` |
| Change ADB tool behavior | `app/src/main/java/com/example/blueheartv/control` |
| Change system capability behavior | `app/src/main/java/com/example/blueheartv/system` |
| Update dependency wiring | `app/src/main/java/com/example/blueheartv/di/AppModule.kt` |
| Add local tests | `app/src/test/java/com/example/blueheartv` |
| Add device/UI tests | `app/src/androidTest/java/com/example/blueheartv` |

