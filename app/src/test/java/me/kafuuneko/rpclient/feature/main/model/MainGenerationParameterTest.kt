package me.kafuuneko.rpclient.feature.main.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainGenerationParameterTest {
    private val provider = LLMProvider(
        name = "Test",
        providerType = LLMProviderType.Custom,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://example.invalid",
        model = "test",
        maxTokens = 1200,
        contextTokens = 8192
    )

    @Test
    fun updateProviderOrNull_updatesValidValues() {
        val updated = MainGenerationParameter.Temperature.updateProviderOrNull(provider, "1.2")
        assertEquals(1.2f, updated?.temperature)
    }

    @Test
    fun updateProviderOrNull_rejectsInvalidTokenRelationship() {
        assertNull(MainGenerationParameter.MaxTokens.updateProviderOrNull(provider, "8192"))
        assertNull(MainGenerationParameter.ContextTokens.updateProviderOrNull(provider, "1200"))
    }

    @Test
    fun quickTokenValues_usesBinaryTokenSteps() {
        assertEquals(
            listOf(8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576),
            MainGenerationParameter.ContextTokens.quickTokenValues()
        )
    }
}
