package me.kafuuneko.rpclient.libs.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatModelMappingsTest {
    @Test
    fun activationStrategiesRoundTripWithoutNameLookup() {
        GroupChatSession.ActivationStrategy.entries.forEach { entity ->
            assertEquals(entity, entity.toGroupChatActivationStrategy().toEntity())
        }
        GroupChatActivationStrategy.entries.forEach { model ->
            assertEquals(model, model.toEntity().toGroupChatActivationStrategy())
        }
    }

    @Test
    fun characterCardModesRoundTripWithoutNameLookup() {
        GroupChatSession.CharacterCardMode.entries.forEach { entity ->
            assertEquals(entity, entity.toGroupChatCharacterCardMode().toEntity())
        }
        GroupChatCharacterCardMode.entries.forEach { model ->
            assertEquals(model, model.toEntity().toGroupChatCharacterCardMode())
        }
    }

    @Test
    fun messageSourcesRoundTripWithoutNameLookup() {
        GroupChatMessage.Source.entries.forEach { entity ->
            assertEquals(entity, entity.toGroupChatMessageSource().toEntity())
        }
        GroupChatMessageSource.entries.forEach { model ->
            assertEquals(model, model.toEntity().toGroupChatMessageSource())
        }
    }
}
