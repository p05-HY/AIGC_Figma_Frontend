package com.example.blueheartv.di

import com.example.blueheartv.chat.ChatProviderRegistry
import com.example.blueheartv.viewmodel.ChatSessionRepository
import com.example.blueheartv.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {

    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    single { ChatSessionRepository() }

    single {
        ChatViewModel(
            chatProvider = ChatProviderRegistry.createProvider(),
            repo = get(),
        )
    }
}
