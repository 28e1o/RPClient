package me.kafuuneko.rpclient.libs.regex

/** 脚本持久化目标；角色作用域必须携带稳定角色 ID。 */
data class RegexScriptTarget(
    val scope: RegexScriptScope,
    val characterId: Long? = null
) {
    init {
        require((scope == RegexScriptScope.Character) == (characterId != null)) {
            "Character scope requires exactly one character id"
        }
    }
}
