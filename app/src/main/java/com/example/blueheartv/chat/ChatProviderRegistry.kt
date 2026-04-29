package com.example.blueheartv.chat

object ChatProviderRegistry {
    @Volatile
    private var providerFactory: () -> ChatProvider = {
        AgentServerChatProvider(
            AgentServerClient(configProvider = { AgentServerConfigStore.snapshot() }),
        )
    }

    fun createProvider(): ChatProvider = providerFactory()

    fun overrideFactory(factory: () -> ChatProvider) {
        providerFactory = factory
    }
}
