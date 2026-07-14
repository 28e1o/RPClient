package me.kafuuneko.rpclient.feature.groupchat.model

/** 群聊设置中可添加角色的最小展示信息。 */
data class GroupChatAvailableCharacterItem(
    val id: Long,
    val name: String,
    val alreadyMember: Boolean
)
