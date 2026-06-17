package com.example.blueheartv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.AgentServerStatusClient
import com.example.blueheartv.chat.AgentServerStatusSnapshot
import com.example.blueheartv.control.AdbWebSocketService
import com.example.blueheartv.system.SystemService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AgentServerDetailUiState(
    val status: AgentServerStatusSnapshot? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
)

class SettingsDetailViewModel(
    private val statusClientFactory: () -> AgentServerStatusClient = { AgentServerStatusClient() },
) : ViewModel() {

    private val refreshMutex = Mutex()
    private val _uiState = MutableStateFlow(AgentServerDetailUiState())
    val uiState: StateFlow<AgentServerDetailUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            AgentServerConfigStore.config.collectLatest { config ->
                pollingJob?.cancel()
                pollingJob = null

                _uiState.update {
                    it.copy(
                        status = null,
                        isLoading = false,
                        errorMessage = null,
                        isConfigured = config.isConfigured,
                    )
                }

                if (!config.isConfigured) {
                    return@collectLatest
                }

                refreshStatus(showLoading = true)

                pollingJob = launch {
                    while (isActive) {
                        delay(POLL_INTERVAL_MS)
                        refreshStatus(showLoading = false)
                    }
                }
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshStatus(showLoading = true)
        }
    }

    suspend fun updateNetwork(connected: Boolean): Boolean {
        val updated = refreshMutex.withLock {
            if (!uiState.value.isConfigured) {
                return false
            }

            _uiState.update {
                it.copy(isLoading = true, errorMessage = null)
            }

            val network = runCatching {
                statusClientFactory().updateNetworkConnected(connected)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "网络状态更新失败",
                    )
                }
                return false
            }

            val current = uiState.value.status
            if (current != null) {
                _uiState.update {
                    it.copy(
                        status = current.copy(network = network),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }
            true
        }

        if (updated) {
            refreshStatus(showLoading = false)
        }
        return updated
    }

    fun saveAndConnect(context: Context, baseUrl: String, apiKey: String) {
        val appContext = context.applicationContext
        AgentServerConfigStore.update(appContext, baseUrl, apiKey)
        AdbWebSocketService.start(appContext)
        SystemService.start(appContext, baseUrl)
    }

    private suspend fun refreshStatus(showLoading: Boolean) {
        refreshMutex.withLock {
            if (!uiState.value.isConfigured) {
                return
            }

            if (showLoading) {
                _uiState.update {
                    it.copy(isLoading = true, errorMessage = null)
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = null)
                }
            }

            val snapshot = runCatching {
                statusClientFactory().fetchAll()
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "状态查询失败",
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    status = snapshot,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
    }
}