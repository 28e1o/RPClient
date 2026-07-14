package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LLMResponseValidatorTest {
    @Test
    fun nonStreamingEmptyResponseUsesFixedSafeMessage() {
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            LLMGenerationResponse(
                content = "",
                model = "routed-model",
                provider = LLMProviderType.Custom,
                finishReason = "stop",
                rawResponse = "{}"
            ).requireNonEmptyContent()
        }

        assertEquals("The model returned an empty response", error.message)
    }

    @Test
    fun streamingEmptyResponseThrowsAfterFinishedEvent() {
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            runBlocking {
                flowOf(
                    LLMStreamEvent.Finished(
                        finishReason = "stop",
                        model = "routed-model"
                    )
                )
                    .requireNonEmptyContent()
                    .toList()
            }
        }

        assertEquals("The model returned an empty response", error.message)
    }

    @Test
    fun streamingContentPassesThroughUnchanged() = runBlocking {
        val events = listOf(
            LLMStreamEvent.Delta("Hello", "chunk"),
            LLMStreamEvent.Finished(finishReason = "stop")
        )

        val result = flowOf(*events.toTypedArray())
            .requireNonEmptyContent()
            .toList()

        assertEquals(events, result)
    }
}
