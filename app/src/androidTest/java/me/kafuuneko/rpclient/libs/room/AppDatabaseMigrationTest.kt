package me.kafuuneko.rpclient.libs.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_removesHistoricalLogsAndKeepsBusinessRows() {
        migrationHelper.createDatabase(DatabaseName, 1).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', 'description', 'personality',
                    'scenario', '[]', '', '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, characterId, createTime, latestTime, lorebookEntrySet, title, userNote,
                    userName, userDescription, worldInfoStateJson, autoSummaryPaused
                ) VALUES (202, 101, 1, 2, '[]', 'session', '', 'user', '', '{}', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_request_logs (
                    id, createTime, providerName, providerType, protocol, model, isStreaming,
                    requestJson, responseJson
                ) VALUES (303, 3, 'provider', 'Custom', 'OpenAICompatible', 'model', 0,
                    '{"prompt":"PRIVATE_SENTINEL_92f1"}',
                    '{"content":"PRIVATE_SENTINEL_92f1"}')
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DatabaseName,
            2,
            true
        )

        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tableNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertFalse(tableNames.contains("llm_request_logs"))
        }
        migrated.query("SELECT name FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("character", cursor.getString(0))
        }
        migrated.query("SELECT title, latestTime FROM chat_sessions WHERE id = 202").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("session", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
        }
    }

    private companion object {
        const val DatabaseName = "app-migration-test"
    }
}
