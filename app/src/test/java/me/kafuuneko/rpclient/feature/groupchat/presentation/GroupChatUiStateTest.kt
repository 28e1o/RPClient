package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatUiStateTest {
    @Test
    fun settingsDraftDoesNotChangeCommittedConversationStrategy() {
        val members = listOf(GroupChatMemberItem(1L, "A", "", muted = false))
        val state = GroupChatUiState.Normal(
            sessionId = 1L,
            title = "Group",
            members = members,
            activeActivationStrategy = GroupChatActivationStrategy.Natural,
            conversationState = GroupChatConversationState(
                messages = emptyList(),
                selectedSpeakerId = 1L
            ),
            settingsState = GroupChatSettingsState(
                activationStrategy = GroupChatActivationStrategy.Natural
            )
        )

        val edited = state.copy(
            settingsState = state.settingsState.copy(
                activationStrategy = GroupChatActivationStrategy.Manual
            )
        )

        assertEquals(GroupChatActivationStrategy.Natural, edited.activeActivationStrategy)
        assertEquals(GroupChatActivationStrategy.Manual, edited.settingsState.activationStrategy)
        assertEquals(members, edited.members)
    }
}
