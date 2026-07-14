package me.kafuuneko.rpclient.feature.characterlist.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** 角色列表渲染所需的最小快照。 */
data class CharacterListItem(
    val id: Long,
    val name: String,
    val description: String,
    val tags: List<String>,
    val avatarText: String,
    val avatarColor: Color,
    val avatarImage: ImageBitmap? = null
)
