package me.kafuuneko.rpclient

import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication

class RPClientAppKoinTest {
    @Test
    fun promptBuildersResolveFromApplicationModule() {
        val application = koinApplication {
            modules(appModules)
        }
        try {
            assertNotNull(application.koin.get<ChatPromptBuilder>())
            assertNotNull(application.koin.get<GroupChatPromptBuilder>())
        } finally {
            application.close()
        }
    }
}
