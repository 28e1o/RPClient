package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 世界书级配置。
 *
 * [scanDepth]、[tokenBudget] 和 [recursiveScanning] 为条目默认行为；[tokenBudget] 是固定
 * Token 上限，为 0 时跟随全局世界书预算，不对本书增加额外限制。
 * [extensionsJson] 保留当前应用尚未识别的导入字段。
 */
@Entity(
    tableName = "lorebooks"
)
data class Lorebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val scanDepth: Int = 2,
    val tokenBudget: Int = 0,
    val recursiveScanning: Boolean = false,
    val extensionsJson: String = "{}"
)
