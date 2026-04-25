package com.example.blueheartv.di

import com.example.blueheartv.chat.ChatProviderRegistry
import com.example.blueheartv.db.AppDatabase
import com.example.blueheartv.db.SharedPrefsMigrator
import com.example.blueheartv.viewmodel.ChatSessionRepository
import com.example.blueheartv.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    single { AppDatabase.get(androidContext()) }

    single { get<AppDatabase>().chatDao() }

    single {
        val dao = get<com.example.blueheartv.db.ChatDao>()
        runBlocking { SharedPrefsMigrator.migrateIfNeeded(androidContext(), dao) }
        ChatSessionRepository(dao = dao, scope = get())
    }

    single { com.example.blueheartv.control.AdbController(androidContext()) }

    single {
        ChatViewModel(
            chatProvider = ChatProviderRegistry.createProvider(),
            repo = get(),
            adbController = get(),
        )
    }
}
