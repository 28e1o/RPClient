package me.kafuuneko.rpclient.libs.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 将持久化策略穷举映射为共享模型，新增枚举成员时由编译器强制补齐。 */
internal fun GroupChatSession.ActivationStrategy.toGroupChatActivationStrategy() = when (this) {
    GroupChatSession.ActivationStrategy.Manual -> GroupChatActivationStrategy.Manual
    GroupChatSession.ActivationStrategy.Natural -> GroupChatActivationStrategy.Natural
    GroupChatSession.ActivationStrategy.List -> GroupChatActivationStrategy.List
    GroupChatSession.ActivationStrategy.Pooled -> GroupChatActivationStrategy.Pooled
}

internal fun GroupChatActivationStrategy.toEntity() = when (this) {
    GroupChatActivationStrategy.Manual -> GroupChatSession.ActivationStrategy.Manual
    GroupChatActivationStrategy.Natural -> GroupChatSession.ActivationStrategy.Natural
    GroupChatActivationStrategy.List -> GroupChatSession.ActivationStrategy.List
    GroupChatActivationStrategy.Pooled -> GroupChatSession.ActivationStrategy.Pooled
}

internal fun GroupChatSession.CharacterCardMode.toGroupChatCharacterCardMode() = when (this) {
    GroupChatSession.CharacterCardMode.Swap -> GroupChatCharacterCardMode.Swap
    GroupChatSession.CharacterCardMode.Join -> GroupChatCharacterCardMode.Join
}

internal fun GroupChatCharacterCardMode.toEntity() = when (this) {
    GroupChatCharacterCardMode.Swap -> GroupChatSession.CharacterCardMode.Swap
    GroupChatCharacterCardMode.Join -> GroupChatSession.CharacterCardMode.Join
}

internal fun GroupChatMessage.Source.toGroupChatMessageSource() = when (this) {
    GroupChatMessage.Source.Character -> GroupChatMessageSource.Character
    GroupChatMessage.Source.User -> GroupChatMessageSource.User
    GroupChatMessage.Source.System -> GroupChatMessageSource.System
}

internal fun GroupChatMessageSource.toEntity() = when (this) {
    GroupChatMessageSource.Character -> GroupChatMessage.Source.Character
    GroupChatMessageSource.User -> GroupChatMessage.Source.User
    GroupChatMessageSource.System -> GroupChatMessage.Source.System
}
