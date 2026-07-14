package me.kafuuneko.rpclient.feature.llmprovideredit.model

/** 仅在 ViewModel 私有内存中短暂使用的敏感字段解析结果。 */
internal data class ResolvedProviderCredentials(
    val apiKey: String,
    val customHeadersJson: String
)

/** 让连接测试和最终保存共享完全相同的 Keep/Replace/Clear 解析规则。 */
internal object LLMProviderCredentialResolver {
    fun resolve(
        form: LLMProviderEditForm,
        initialApiKey: String,
        initialCustomHeaders: String,
        apiKeyReplacement: String?,
        customHeadersReplacement: String?
    ): ResolvedProviderCredentials? {
        val apiKey = resolveValue(
            form.apiKeyEditMode,
            initialApiKey,
            apiKeyReplacement
        ) ?: return null
        val customHeaders = resolveValue(
            form.customHeadersEditMode,
            initialCustomHeaders,
            customHeadersReplacement
        ) ?: return null
        return ResolvedProviderCredentials(apiKey, customHeaders)
    }

    private fun resolveValue(
        mode: CredentialEditMode,
        initialValue: String,
        replacement: String?
    ): String? {
        return when (mode) {
            CredentialEditMode.KeepExisting -> initialValue
            CredentialEditMode.Replace -> replacement
            CredentialEditMode.Clear -> ""
        }
    }
}
