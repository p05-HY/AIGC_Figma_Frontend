package com.example.blueheartv.chat

/**
 * Agent Server 接口路径与协议常量的集中定义。
 *
 * 此前这些字面量散落在 [AgentServerClient]、WebSocket 客户端等处，难以统一维护。
 * 全部收敛到此处，便于后端路径调整时单点修改。BaseUrl 仍由 [AgentServerConfigStore] 动态提供。
 */
object ApiPaths {
    // ---- REST 路径段（拼接到 BaseUrl 之后）----
    const val THREADS = "threads"
    const val SEARCH = "search"
    const val RUNS = "runs"
    const val STREAM = "stream"
    const val STATE = "state"

    // ---- WebSocket 路径段 ----
    const val ADB_WS = "adb"
    const val SYSTEM_WS = "system"

    // ---- LangGraph 流模式 ----
    const val STREAM_MODE_MESSAGES = "messages-tuple"
    const val STREAM_MODE_UPDATES = "updates"
    const val STREAM_MODE_TASKS = "tasks"
    const val STREAM_MODE_CUSTOM = "custom"

    /** streamRun 请求使用的流模式集合（顺序与后端约定一致）。 */
    val STREAM_MODES = listOf(
        STREAM_MODE_MESSAGES,
        STREAM_MODE_UPDATES,
        STREAM_MODE_TASKS,
        STREAM_MODE_CUSTOM,
    )
}
