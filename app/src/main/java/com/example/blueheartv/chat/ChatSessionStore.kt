package com.example.blueheartv.chat

import android.content.Context
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.ToolCall
import org.json.JSONArray
import org.json.JSONObject

data class StoredChatSession(
    val id: String,
    val title: String,
    val updatedAtMillis: Long,
    val messages: List<Message>,
)

data class ChatSessionsSnapshot(
    val activeSessionId: String?,
    val sessions: List<StoredChatSession>,
)

object ChatSessionStore {
    private const val PREFS_NAME = "blueheartv_chat_sessions"
    private const val KEY_SNAPSHOT = "snapshot_v1"
    private const val MAX_SESSIONS = 20
    private const val MAX_MESSAGES_PER_SESSION = 300

    fun load(context: Context?): ChatSessionsSnapshot {
        if (context == null) return ChatSessionsSnapshot(activeSessionId = null, sessions = emptyList())

        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null)
            ?: return ChatSessionsSnapshot(activeSessionId = null, sessions = emptyList())

        return runCatching {
            val root = JSONObject(raw)
            val activeSessionId = root.optString("activeSessionId").takeIf { it.isNotBlank() }
            val sessionsJson = root.optJSONArray("sessions") ?: JSONArray()

            val sessions = buildList {
                for (index in 0 until sessionsJson.length()) {
                    val item = sessionsJson.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue

                    val title = item.optString("title").ifBlank { "当前对话" }
                    val updatedAtMillis = item.optLong("updatedAtMillis", 0L)
                    val messagesJson = item.optJSONArray("messages") ?: JSONArray()

                    val messages = buildList {
                        for (messageIndex in 0 until messagesJson.length()) {
                            val messageJson = messagesJson.optJSONObject(messageIndex) ?: continue
                            val messageId = messageJson.optString("id")
                            if (messageId.isBlank()) continue
                            val content = messageJson.optString("content")
                            val isUser = messageJson.optBoolean("isUser")
                            val deliveryState = parseDeliveryState(
                                messageJson.optString("deliveryState"),
                            )
                            val errorMessage = messageJson.optString("errorMessage")
                                .takeIf { it.isNotBlank() }

                            val toolCalls = messageJson.optJSONArray("toolCalls")?.let { toolArray ->
                                buildList {
                                    for (toolIndex in 0 until toolArray.length()) {
                                        val toolJson = toolArray.optJSONObject(toolIndex) ?: continue
                                        val label = toolJson.optString("label")
                                        if (label.isBlank()) continue
                                        add(
                                            ToolCall(
                                                label = label,
                                                isComplete = toolJson.optBoolean("isComplete"),
                                            ),
                                        )
                                    }
                                }.takeIf { it.isNotEmpty() }
                            }

                            add(
                                Message(
                                    id = messageId,
                                    content = content,
                                    isUser = isUser,
                                    deliveryState = deliveryState,
                                    toolCalls = toolCalls,
                                    errorMessage = errorMessage,
                                ),
                            )
                        }
                    }

                    add(
                        StoredChatSession(
                            id = id,
                            title = title,
                            updatedAtMillis = updatedAtMillis,
                            messages = messages,
                        ),
                    )
                }
            }

            ChatSessionsSnapshot(
                activeSessionId = activeSessionId,
                sessions = sessions,
            )
        }.getOrElse {
            ChatSessionsSnapshot(activeSessionId = null, sessions = emptyList())
        }
    }

    fun save(
        context: Context?,
        snapshot: ChatSessionsSnapshot,
    ) {
        if (context == null) return

        val normalizedSessions = snapshot.sessions
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_SESSIONS)
            .map { session ->
                session.copy(messages = session.messages.takeLast(MAX_MESSAGES_PER_SESSION))
            }

        val root = JSONObject().apply {
            put("activeSessionId", snapshot.activeSessionId ?: JSONObject.NULL)
            put(
                "sessions",
                JSONArray().apply {
                    normalizedSessions.forEach { session ->
                        put(
                            JSONObject().apply {
                                put("id", session.id)
                                put("title", session.title)
                                put("updatedAtMillis", session.updatedAtMillis)
                                put(
                                    "messages",
                                    JSONArray().apply {
                                        session.messages.forEach { message ->
                                            put(
                                                JSONObject().apply {
                                                    put("id", message.id)
                                                    put("content", message.content)
                                                    put("isUser", message.isUser)
                                                    put(
                                                        "deliveryState",
                                                        message.deliveryState.name,
                                                    )
                                                    put(
                                                        "errorMessage",
                                                        message.errorMessage ?: JSONObject.NULL,
                                                    )
                                                    put(
                                                        "toolCalls",
                                                        message.toolCalls?.let { toolCalls ->
                                                            JSONArray().apply {
                                                                toolCalls.forEach { toolCall ->
                                                                    put(
                                                                        JSONObject().apply {
                                                                            put("label", toolCall.label)
                                                                            put("isComplete", toolCall.isComplete)
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        } ?: JSONObject.NULL,
                                                    )
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, root.toString())
            .apply()
    }

    private fun parseDeliveryState(value: String?): MessageDeliveryState {
        return runCatching { MessageDeliveryState.valueOf(value.orEmpty()) }
            .getOrDefault(MessageDeliveryState.COMPLETED)
    }
}
