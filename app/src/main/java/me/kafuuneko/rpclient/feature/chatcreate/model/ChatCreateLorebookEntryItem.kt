package me.kafuuneko.rpclient.feature.chatcreate.model

/** 新建单聊页可选择的世界书条目快照。 */
data class ChatCreateLorebookEntryItem(
    val id: Long,
    val lorebookName: String,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val secondaryKeywords: List<String>,
    val category: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int
)
