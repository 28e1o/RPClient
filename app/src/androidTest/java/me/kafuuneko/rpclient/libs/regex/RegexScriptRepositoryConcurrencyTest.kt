package me.kafuuneko.rpclient.libs.regex

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RegexScriptRepositoryConcurrencyTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RegexScriptRepository
    private var previousGlobalJson = "[]"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Kotpref.init(context)
        previousGlobalJson = AppModel.globalRegexScriptsJson
        AppModel.globalRegexScriptsJson = "[]"
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val gson = Gson()
        repository = RegexScriptRepository(
            context,
            gson,
            CharacterRepository(database, gson),
            RegexScriptCodec(gson)
        )
    }

    @After
    fun tearDown() {
        database.close()
        AppModel.globalRegexScriptsJson = previousGlobalJson
    }

    @Test
    fun concurrentMutations_keepEveryCommittedUpdate() = runBlocking {
        val target = RegexScriptTarget(RegexScriptScope.Global)

        coroutineScope {
            (0 until 40).map { index ->
                async(Dispatchers.Default) {
                    repository.updateScripts(target) { current ->
                        current + script("script-$index")
                    }
                }
            }.awaitAll()
        }

        val scripts = repository.getScripts(target)
        assertEquals(40, scripts.size)
        assertEquals((0 until 40).map { "script-$it" }.toSet(), scripts.map { it.id }.toSet())
    }

    @Test
    fun importConflict_isResolvedAgainstLatestListAtCommitTime() = runBlocking {
        val target = RegexScriptTarget(RegexScriptScope.Global)
        repository.updateScripts(target) { listOf(script("shared-id")) }
        val imported = script("shared-id")

        val committed = repository.updateScripts(target) { current ->
            val existingIds = current.map { it.id }.toMutableSet()
            val normalized = if (!existingIds.add(imported.id)) {
                imported.copy(id = UUID.randomUUID().toString())
            } else {
                imported
            }
            current + normalized
        }

        assertEquals(2, committed.size)
        assertNotEquals(committed[0].id, committed[1].id)
    }

    @Test
    fun characterMutation_preservesUnrelatedExtensions() = runBlocking {
        val characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
                extensionsJson = "{\"vendor_extension\":{\"enabled\":true}}"
            )
        )
        val target = RegexScriptTarget(RegexScriptScope.Character, characterId)

        repository.updateScripts(target) { current -> current + script("embedded") }

        val character = database.getCharacterDao().getCharacterById(characterId)
        assertTrue(character?.extensionsJson.orEmpty().contains("vendor_extension"))
        assertEquals(listOf("embedded"), repository.getScripts(target).map { it.id })
    }

    private fun script(id: String) = RegexScript(
        id = id,
        scriptName = id,
        findRegex = "x",
        replaceString = "y",
        placement = listOf(RegexPlacement.AiResponse.value)
    )
}
