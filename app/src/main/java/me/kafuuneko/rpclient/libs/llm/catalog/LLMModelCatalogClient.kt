package me.kafuuneko.rpclient.libs.llm.catalog

import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel

/**
 * 查询当前 Provider 凭据可访问的模型目录。
 *
 * 该能力与文本生成分离，因为部分兼容网关只实现生成接口，不提供模型列表端点。
 */
interface LLMModelCatalogClient {
    suspend fun listModels(): List<LLMAvailableModel>
}
