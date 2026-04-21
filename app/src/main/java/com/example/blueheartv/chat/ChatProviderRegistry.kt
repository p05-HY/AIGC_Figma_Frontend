package com.example.blueheartv.chat

object ChatProviderRegistry {
    @Volatile
    private var providerFactory: () -> ChatProvider = {
        SiliconFlowChatProvider.createOrNull()
            ?: UniAixSdkChatProvider.createOrNull()
            ?: FakeStreamingChatProvider()
    }

    fun createProvider(): ChatProvider = providerFactory()

    fun overrideFactory(factory: () -> ChatProvider) {
        providerFactory = factory
    }
}
