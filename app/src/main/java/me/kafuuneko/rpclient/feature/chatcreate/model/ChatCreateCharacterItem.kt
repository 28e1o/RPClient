package me.kafuuneko.rpclient.feature.chatcreate.model

/** 新建单聊页角色选择器所需的最小快照。 */
data class ChatCreateCharacterItem(
    val id: Long,
    val name: String,
    val description: String,
    val tags: List<String>
)
