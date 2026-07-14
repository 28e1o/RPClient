package me.kafuuneko.rpclient.libs.groupchat.model

/** 群聊发言人选择策略。 */
enum class GroupChatActivationStrategy { Manual, Natural, List, Pooled }

/** 多角色卡进入 Prompt 时采用的组合模式。 */
enum class GroupChatCharacterCardMode { Swap, Join }

/** 群聊消息在展示层中的来源。 */
enum class GroupChatMessageSource { User, Character, System }

/** 群聊页面共享的世界书分组及其会话启用状态。 */
data class GroupChatLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val entries: List<GroupChatLorebookEntryItem>
) {
    val enabledCount: Int
        get() = entries.count { it.enabled }

    val totalCount: Int
        get() = entries.size

    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}

/** 群聊页面共享的世界书条目展示数据。 */
data class GroupChatLorebookEntryItem(
    val id: Long,
    val lorebookId: Long,
    val lorebookName: String,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val secondaryKeywords: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int,
    val enabled: Boolean
)
