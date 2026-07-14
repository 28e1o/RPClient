package me.kafuuneko.rpclient.libs.regex

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository

/**
 * Regex 脚本的持久化与授权仓库。
 *
 * 全局和预设脚本保存于 Kotpref；角色脚本保存于角色卡 `extensions.regex_scripts`。
 * 预设和角色卡脚本默认不执行，只有用户授权后才会由 [activeScripts] 返回。
 */
class RegexScriptRepository(
    private val mContext: Context,
    private val mGson: Gson,
    private val mCharacterRepository: CharacterRepository,
    private val mCodec: RegexScriptCodec
) {
    /** 所有脚本 read-modify-write 共用同一把锁，避免导入与编辑互相覆盖。 */
    private val mMutationMutex = Mutex()

    /** 读取全局脚本，损坏配置按空列表处理。 */
    fun getGlobalScripts(): List<RegexScript> =
        mCodec.parseList(readSafely { AppModel.globalRegexScriptsJson })

    /** 读取当前 Prompt 预设脚本。 */
    fun getPresetScripts(): List<RegexScript> =
        mCodec.parseList(readSafely { AppModel.presetRegexScriptsJson })

    /** 判断预设脚本是否已由用户显式授权。 */
    fun isPresetAuthorized(): Boolean =
        runCatching { AppModel.presetRegexScriptsAuthorized }.getOrDefault(false)

    /** 更新预设脚本授权状态。 */
    fun setPresetAuthorized(authorized: Boolean) {
        AppModel.presetRegexScriptsAuthorized = authorized
    }

    /** 从角色扩展字段读取内嵌脚本，不改变角色卡原始其他扩展。 */
    fun getCharacterScripts(character: Character): List<RegexScript> {
        val extensions = parseExtensions(character.extensionsJson)
        return extensions.get("regex_scripts")
            ?.takeIf { it.isJsonArray }
            ?.let { mCodec.parseList(mGson.toJson(it)) }
            .orEmpty()
    }

    /** 读取稳定目标的最新脚本。 */
    suspend fun getScripts(target: RegexScriptTarget): List<RegexScript> {
        return when (target.scope) {
            RegexScriptScope.Global -> getGlobalScripts()
            RegexScriptScope.Preset -> getPresetScripts()
            RegexScriptScope.Character -> mCharacterRepository
                .getCharacterById(requireNotNull(target.characterId))
                ?.let(::getCharacterScripts)
                .orEmpty()
        }
    }

    /**
     * 对稳定目标执行原子 read-modify-write，并返回提交后的权威列表。
     *
     * 文件读取和 JSON 解析应在调用前完成；ID 冲突等依赖最新列表的决策放在 [transform] 中。
     */
    suspend fun updateScripts(
        target: RegexScriptTarget,
        transform: (List<RegexScript>) -> List<RegexScript>
    ): List<RegexScript> = mMutationMutex.withLock {
        when (target.scope) {
            RegexScriptScope.Global -> {
                val updated = transform(getGlobalScripts()).toList()
                AppModel.globalRegexScriptsJson = mCodec.toJson(updated)
                updated
            }

            RegexScriptScope.Preset -> {
                val updated = transform(getPresetScripts()).toList()
                AppModel.presetRegexScriptsJson = mCodec.toJson(updated)
                updated
            }

            RegexScriptScope.Character -> updateCharacterScripts(
                requireNotNull(target.characterId),
                transform
            )
        }
    }

    /** 判断指定角色卡的内嵌脚本是否获准执行。 */
    fun isCharacterAuthorized(characterId: Long): Boolean =
        characterId in authorizedCharacterIds()

    /** 增删角色授权集合；授权状态与角色卡内容分离保存。 */
    fun setCharacterAuthorized(characterId: Long, authorized: Boolean) {
        val ids = authorizedCharacterIds().toMutableSet()
        if (authorized) ids += characterId else ids -= characterId
        AppModel.authorizedCharacterRegexIdsJson = mGson.toJson(ids.sorted())
    }

    /**
     * 收集本轮可执行脚本，并生成全局、预设、角色卡的稳定顺序。
     *
     * 未授权的预设或角色脚本仍被原样保存，但不会出现在返回列表中。
     */
    fun activeScripts(characters: List<Character>): List<ScopedRegexScript> {
        return buildList {
            getGlobalScripts().forEachIndexed { index, script ->
                add(
                    ScopedRegexScript(
                        script = script,
                        scope = RegexScriptScope.Global,
                        ownerName = "Global",
                        order = index
                    )
                )
            }
            if (isPresetAuthorized()) {
                getPresetScripts().forEachIndexed { index, script ->
                    add(
                        ScopedRegexScript(
                            script = script,
                            scope = RegexScriptScope.Preset,
                            ownerName = "Prompt Preset",
                            order = index
                        )
                    )
                }
            }
            var characterOrder = 0
            characters.distinctBy { it.id }.forEach { character ->
                if (!isCharacterAuthorized(character.id)) return@forEach
                getCharacterScripts(character).forEach { script ->
                    add(
                        ScopedRegexScript(
                            script = script,
                            scope = RegexScriptScope.Character,
                            ownerId = character.id.toString(),
                            ownerName = character.name,
                            order = characterOrder++
                        )
                    )
                }
            }
        }
    }

    /** 解析外部 JSON 文件中的脚本。 */
    fun importScripts(json: String): List<RegexScript> = mCodec.parseList(json)

    /** 导出带缩进的脚本 JSON，供文件分享或迁移。 */
    fun exportScripts(scripts: List<RegexScript>): String = mCodec.toJson(scripts, pretty = true)

    /** 从文档 URI 读取并解析脚本。 */
    fun importFromUri(uri: Uri): List<RegexScript> {
        val json = mContext.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Cannot read regex script file")
        return importScripts(json)
    }

    /** 将脚本直接写入用户选择的文档 URI。 */
    fun exportToUri(uri: Uri, scripts: List<RegexScript>) {
        mContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(exportScripts(scripts))
        } ?: error("Cannot open regex script export destination")
    }

    /** 容错解析已授权角色 ID 集合。 */
    private fun authorizedCharacterIds(): Set<Long> {
        val json = readSafely { AppModel.authorizedCharacterRegexIdsJson }
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull {
                runCatching { it.asLong }.getOrNull()
            }.toSet()
        }.getOrDefault(emptySet())
    }

    /** 解析角色扩展对象；损坏数据以空对象兜底，避免管理页崩溃。 */
    private fun parseExtensions(json: String): JsonObject {
        return runCatching {
            JsonParser.parseString(json).asJsonObject.deepCopy()
        }.getOrDefault(JsonObject())
    }

    private fun readSafely(block: () -> String): String =
        runCatching(block).getOrDefault("[]")

    private suspend fun updateCharacterScripts(
        characterId: Long,
        transform: (List<RegexScript>) -> List<RegexScript>
    ): List<RegexScript> {
        var authoritative = emptyList<RegexScript>()
        val updated = mCharacterRepository.updateCharacterExtensions(characterId) { extensionsJson ->
            val extensions = parseExtensions(extensionsJson)
            val current = extensions.get("regex_scripts")
                ?.takeIf { it.isJsonArray }
                ?.let { mCodec.parseList(mGson.toJson(it)) }
                .orEmpty()
            authoritative = transform(current).toList()
            extensions.add(
                "regex_scripts",
                JsonParser.parseString(mCodec.toJson(authoritative)).asJsonArray
            )
            mGson.toJson(extensions)
        }
        return if (updated == null) emptyList() else authoritative
    }
}
