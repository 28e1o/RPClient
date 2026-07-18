package me.kafuuneko.rpclient.feature.worldbookedit.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldBookEditFormTest {
    @Test
    fun newLorebookFollowsGlobalBudget() {
        val form = WorldBookEditForm()

        assertEquals(WorldBookBudgetMode.FollowGlobal, form.tokenBudgetMode)
        assertEquals(0, form.resolvedTokenBudget)
    }

    @Test
    fun renamingLorebookPreservesAdvancedFields() {
        val lorebook = Lorebook(
            id = 12L,
            name = "Imported",
            description = "Imported description",
            scanDepth = 9,
            tokenBudget = 384,
            recursiveScanning = true,
            extensionsJson = """{"custom":{"enabled":true}}"""
        )

        val saved = WorldBookEditForm.from(lorebook, emptyList())
            .copy(name = " Renamed ")
            .toLorebook()

        assertEquals(
            WorldBookBudgetMode.FixedTokens,
            WorldBookEditForm.from(lorebook, emptyList()).tokenBudgetMode
        )
        assertEquals(lorebook.copy(name = "Renamed"), saved)
    }

    @Test
    fun smallStandardTokenBudgetRemainsFixedTokens() {
        val form = WorldBookEditForm.from(
            Lorebook(id = 1L, name = "Book", tokenBudget = 25),
            emptyList()
        )

        assertEquals(WorldBookBudgetMode.FixedTokens, form.tokenBudgetMode)
        assertEquals("25", form.tokenBudgetInput)
        assertEquals(25, form.resolvedTokenBudget)
        assertEquals(25, form.toLorebook().tokenBudget)
    }

    @Test
    fun emptyFixedTokenDraftStaysInFixedModeUntilValidation() {
        val form = WorldBookEditForm(
            tokenBudgetMode = WorldBookBudgetMode.FixedTokens,
            tokenBudgetInput = ""
        )

        assertEquals(WorldBookBudgetMode.FixedTokens, form.tokenBudgetMode)
        assertEquals(null, form.resolvedTokenBudget)
    }
}
