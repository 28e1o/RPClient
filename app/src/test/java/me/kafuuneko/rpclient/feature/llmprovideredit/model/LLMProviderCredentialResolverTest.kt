package me.kafuuneko.rpclient.feature.llmprovideredit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LLMProviderCredentialResolverTest {
    @Test
    fun keepExisting_preservesStoredValues() {
        val resolved = resolve(LLMProviderEditForm())

        assertEquals("stored-key", resolved?.apiKey)
        assertEquals("{\"Stored\":\"header\"}", resolved?.customHeadersJson)
    }

    @Test
    fun replace_usesConfirmedValuesForBothFields() {
        val resolved = resolve(
            LLMProviderEditForm(
                apiKeyEditMode = CredentialEditMode.Replace,
                customHeadersEditMode = CredentialEditMode.Replace
            ),
            apiReplacement = "new-key",
            headersReplacement = "{\"New\":\"header\"}"
        )

        assertEquals("new-key", resolved?.apiKey)
        assertEquals("{\"New\":\"header\"}", resolved?.customHeadersJson)
    }

    @Test
    fun clear_returnsEmptyValues() {
        val resolved = resolve(
            LLMProviderEditForm(
                apiKeyEditMode = CredentialEditMode.Clear,
                customHeadersEditMode = CredentialEditMode.Clear
            )
        )

        assertEquals("", resolved?.apiKey)
        assertEquals("", resolved?.customHeadersJson)
    }

    @Test
    fun replace_withoutConfirmedPrivateValue_isRejected() {
        assertNull(
            resolve(
                LLMProviderEditForm(apiKeyEditMode = CredentialEditMode.Replace),
                apiReplacement = null
            )
        )
    }

    private fun resolve(
        form: LLMProviderEditForm,
        apiReplacement: String? = null,
        headersReplacement: String? = null
    ): ResolvedProviderCredentials? {
        return LLMProviderCredentialResolver.resolve(
            form = form,
            initialApiKey = "stored-key",
            initialCustomHeaders = "{\"Stored\":\"header\"}",
            apiKeyReplacement = apiReplacement,
            customHeadersReplacement = headersReplacement
        )
    }
}
