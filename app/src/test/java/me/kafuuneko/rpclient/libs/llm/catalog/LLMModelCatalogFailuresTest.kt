package me.kafuuneko.rpclient.libs.llm.catalog

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.llm.LLMHttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class LLMModelCatalogFailuresTest {
    @Test
    fun unsupportedHttpStatusesUseManualInputFallback() {
        assertEquals(
            LLMModelCatalogFailure.UnsupportedEndpoint,
            classifyModelCatalogFailure(LLMHttpStatusException(404, "private"))
        )
        assertEquals(
            LLMModelCatalogFailure.UnsupportedEndpoint,
            classifyModelCatalogFailure(LLMHttpStatusException(405, "private"))
        )
    }

    @Test
    fun sensitiveExceptionDetailsAreNotCopiedIntoFailure() {
        assertEquals(
            LLMModelCatalogFailure.Unauthorized,
            classifyModelCatalogFailure(
                LLMHttpStatusException(401, "credential detail")
            )
        )
        assertEquals(
            LLMModelCatalogFailure.Network,
            classifyModelCatalogFailure(IOException("private host"))
        )
        assertEquals(
            LLMModelCatalogFailure.InvalidResponse,
            classifyModelCatalogFailure(
                LLMModelCatalogInvalidResponseException()
            )
        )
    }

    @Test
    fun cancellationDoesNotBecomeVisibleFailure() {
        assertNull(
            classifyModelCatalogFailure(CancellationException("cancelled"))
        )
    }
}
