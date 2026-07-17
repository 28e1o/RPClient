package me.kafuuneko.rpclient.libs.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptRetentionPolicyTest {
    @Test
    fun historyPriorityDoesNotGrowWithMessageCount() {
        val priorities = List(500) { PromptRetentionPolicy.HISTORY }

        assertEquals(1, priorities.distinct().size)
        assertTrue(PromptRetentionPolicy.HISTORY < 300)
    }

    @Test
    fun pinnedExamplesOutrankHistoryWhileNormalExamplesDoNot() {
        val normal = PromptRetentionPolicy.examplePriority(ExampleDialogueBehavior.Normal)
        val pinned = PromptRetentionPolicy.examplePriority(ExampleDialogueBehavior.Pinned)

        assertTrue(normal < PromptRetentionPolicy.HISTORY)
        assertTrue(pinned > PromptRetentionPolicy.HISTORY)
    }

    @Test
    fun persistedExampleBehaviorFallsBackSafely() {
        assertEquals(
            ExampleDialogueBehavior.Pinned,
            ExampleDialogueBehavior.fromPersistedValue(1)
        )
        assertEquals(
            ExampleDialogueBehavior.default,
            ExampleDialogueBehavior.fromPersistedValue(Int.MAX_VALUE)
        )
    }
}
