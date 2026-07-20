package me.kafuuneko.rpclient.libs.character

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookImportPolicyTest {
    @Test
    fun `only warns for positive fixed budgets below one hundred`() {
        assertFalse(LorebookImportPolicy.requiresLowBudgetConfirmation(importWithBudget(0)))
        assertTrue(LorebookImportPolicy.requiresLowBudgetConfirmation(importWithBudget(1)))
        assertTrue(LorebookImportPolicy.requiresLowBudgetConfirmation(importWithBudget(99)))
        assertFalse(LorebookImportPolicy.requiresLowBudgetConfirmation(importWithBudget(100)))
    }

    @Test
    fun `confirmed global budget is stored as zero`() {
        val resolved = LorebookImportPolicy.resolveBudget(importWithBudget(25), followGlobal = true)

        assertEquals(0, resolved.lorebook.tokenBudget)
    }

    @Test
    fun `rejected global budget keeps imported data`() {
        val imported = importWithBudget(25)

        assertSame(imported, LorebookImportPolicy.resolveBudget(imported, followGlobal = false))
    }

    @Test
    fun `character import warns for a low embedded lorebook budget`() {
        val characterImport = CharacterCardImport(
            character = character(),
            embeddedLorebook = importWithBudget(25)
        )

        assertTrue(LorebookImportPolicy.requiresLowBudgetConfirmation(characterImport))
        assertEquals(
            0,
            requireNotNull(
                LorebookImportPolicy.resolveBudget(characterImport, followGlobal = true)
                    .embeddedLorebook
            ).lorebook.tokenBudget
        )
    }

    @Test
    fun `character import without an embedded lorebook does not warn`() {
        val characterImport = CharacterCardImport(
            character = character(),
            embeddedLorebook = null
        )

        assertFalse(LorebookImportPolicy.requiresLowBudgetConfirmation(characterImport))
    }

    private fun importWithBudget(tokenBudget: Int): CharacterBookImport {
        return CharacterBookImport(
            lorebook = Lorebook(name = "Book", tokenBudget = tokenBudget),
            entries = emptyList()
        )
    }

    private fun character(): Character {
        return Character(
            name = "Character",
            avatar = "",
            characterTags = "[]",
            description = "",
            personality = "",
            scenario = "",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = ""
        )
    }
}
