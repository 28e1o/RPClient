package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LLMRequestLogRepositoryTest {
    private lateinit var database: RequestLogDatabase
    private lateinit var repository: LLMRequestLogRepository
    private var previousDebugMode = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Kotpref.init(context)
        previousDebugMode = AppModel.debugModeEnabled
        AppModel.debugModeEnabled = true
        database = Room.inMemoryDatabaseBuilder(context, RequestLogDatabase::class.java).build()
        repository = LLMRequestLogRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        AppModel.debugModeEnabled = previousDebugMode
    }

    @Test
    fun saveReadAndDelete_persistsRawRequestAndResponse() = runBlocking {
        val sentinel = "PRIVATE_SENTINEL_92f1"
        val requestJson = """{"messages":[{"role":"user","content":"$sentinel"}]}"""
        val responseJson = """{"choices":[{"message":{"content":"$sentinel"}}]}"""
        val id = repository.saveLog(
            provider = LLMProviderConfig(
                name = "Local",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://example.invalid",
                model = "model"
            ),
            model = "model",
            isStreaming = false,
            requestJson = requestJson,
            responseJson = responseJson
        )

        val logs = repository.getAllLogs()
        assertTrue(id > 0)
        assertEquals(1, logs.size)
        assertEquals(requestJson, logs.single().requestJson)
        assertEquals(responseJson, logs.single().responseJson)

        repository.deleteAll()
        assertTrue(repository.getAllLogs().isEmpty())
    }

    @Test
    fun saveLog_doesNotPersistWhenDebugModeIsDisabled() = runBlocking {
        AppModel.debugModeEnabled = false

        val id = repository.saveLog(
            provider = LLMProviderConfig(
                name = "Local",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://example.invalid",
                model = "model"
            ),
            model = "model",
            isStreaming = false,
            requestJson = "raw request",
            responseJson = "raw response"
        )

        assertEquals(0L, id)
        assertTrue(repository.getAllLogs().isEmpty())
    }
}
