package com.example.blueheartv.di

import com.example.blueheartv.chat.ChatProviderRegistry
import com.example.blueheartv.db.AppDatabase
import com.example.blueheartv.viewmodel.ChatSessionRepository
import com.example.blueheartv.viewmodel.ChatSessionStore
import com.example.blueheartv.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {

    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    single { AppDatabase.get(get()) }

    single { get<AppDatabase>().chatDao() }

    single { ChatSessionStore(get()) }

    single {
        ChatSessionRepository(
            store = get(),
            persistScope = get(),
        )
    }

    single {
        ChatViewModel(
            chatProvider = ChatProviderRegistry.createProvider(),
            repo = get(),
        )
    }
}
