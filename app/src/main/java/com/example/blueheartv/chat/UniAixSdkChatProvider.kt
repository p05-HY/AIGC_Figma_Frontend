package com.example.blueheartv.chat

import android.content.Context
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

class UniAixSdkChatProvider(
    private val contextProvider: () -> Context?,
    private val credentialsProvider: () -> UniAixCredentials,
) : ChatProvider {

    override suspend fun streamReply(
        prompt: String,
        onEvent: (ChatStreamEvent) -> Unit,
    ) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty()) {
            onEvent(ChatStreamEvent.Error("输入为空", retryable = false))
            return
        }

        val context = contextProvider()
        if (context == null) {
            onEvent(ChatStreamEvent.Error("uni-ai-x 初始化失败：Context 不可用"))
            return
        }

        val sdkClass = runCatching { Class.forName(SDK_CLASS_NAME) }
            .getOrElse {
                onEvent(ChatStreamEvent.Error("uni-ai-x SDK 未就绪，请先完成模块集成"))
                return
            }

        val callbackClass = runCatching { Class.forName(CALLBACK_CLASS_NAME) }
            .getOrElse {
                onEvent(ChatStreamEvent.Error("uni-ai-x 回调接口未找到，请检查 SDK 版本"))
                return
            }

        val sdkInstance = runCatching { createSdkInstance(sdkClass, context) }
            .getOrElse {
                onEvent(ChatStreamEvent.Error("uni-ai-x 初始化失败：${it.message ?: "未知错误"}"))
                return
            }

        runCatching {
            applyCredentials(
                sdkClass = sdkClass,
                sdkInstance = sdkInstance,
                credentials = credentialsProvider(),
            )
        }.onFailure {
            onEvent(ChatStreamEvent.Error("uni-ai-x 鉴权配置失败：${it.message ?: "未知错误"}"))
            return
        }

        val sendMethod = sdkClass.methods.firstOrNull { method ->
            method.name == "sendMessage" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].isAssignableFrom(callbackClass)
        }

        if (sendMethod == null) {
            onEvent(ChatStreamEvent.Error("uni-ai-x API 不兼容：未找到 sendMessage(String, Callback)"))
            return
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            val finished = AtomicBoolean(false)
            val hasDelta = AtomicBoolean(false)

            fun finish() {
                if (finished.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }

            val callbackProxy = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { _, method, args ->
                when (method.name) {
                    "onStreamChunk" -> {
                        val chunk = args?.firstOrNull()?.toString().orEmpty()
                        if (chunk.isNotEmpty()) {
                            hasDelta.set(true)
                            onEvent(ChatStreamEvent.TextDelta(chunk))
                        }
                    }

                    "onComplete" -> {
                        val fullMessage = args?.firstOrNull()?.toString().orEmpty()
                        if (!hasDelta.get() && fullMessage.isNotEmpty()) {
                            onEvent(ChatStreamEvent.TextDelta(fullMessage))
                        }
                        onEvent(ChatStreamEvent.Completed)
                        finish()
                    }

                    "onError" -> {
                        val errorMessage = args?.firstOrNull()?.toString().orEmpty()
                            .ifBlank { "uni-ai-x 请求失败" }
                        onEvent(ChatStreamEvent.Error(errorMessage))
                        finish()
                    }
                }
                null
            }

            runCatching {
                sendMethod.invoke(sdkInstance, normalizedPrompt, callbackProxy)
            }.onFailure {
                onEvent(ChatStreamEvent.Error("uni-ai-x 调用失败：${it.message ?: "未知错误"}"))
                finish()
            }

            continuation.invokeOnCancellation {
                finished.set(true)
            }
        }
    }

    private fun createSdkInstance(sdkClass: Class<*>, context: Context): Any {
        val contextCtor = sdkClass.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 1 && Context::class.java.isAssignableFrom(params[0])
        }
        if (contextCtor != null) {
            return contextCtor.newInstance(context)
        }

        val emptyCtor = sdkClass.constructors.firstOrNull { constructor ->
            constructor.parameterTypes.isEmpty()
        } ?: throw IllegalStateException("缺少可用构造函数")

        return emptyCtor.newInstance()
    }

    private fun applyCredentials(
        sdkClass: Class<*>,
        sdkInstance: Any,
        credentials: UniAixCredentials,
    ) {
        credentials.token?.let { token ->
            sdkClass.methods.firstOrNull { method ->
                method.name == "setToken" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }?.invoke(sdkInstance, token)
        }

        credentials.apiKey?.let { apiKey ->
            sdkClass.methods.firstOrNull { method ->
                method.name == "setApiKey" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }?.invoke(sdkInstance, apiKey)
        }
    }

    companion object {
        private const val SDK_CLASS_NAME = "uni.ai.x.sdk.AIChatSDK"
        private const val CALLBACK_CLASS_NAME = "uni.ai.x.sdk.models.MessageCallback"

        fun createOrNull(): ChatProvider? {
            val sdkAvailable = runCatching {
                Class.forName(SDK_CLASS_NAME)
                Class.forName(CALLBACK_CLASS_NAME)
            }.isSuccess

            if (!sdkAvailable) return null

            return UniAixSdkChatProvider(
                contextProvider = { AppContextHolder.getOrNull() },
                credentialsProvider = { UniAixCredentialsStore.snapshot() },
            )
        }
    }
}
