package me.kafuuneko.rpclient.libs.groupchat

import android.content.Intent
import me.kafuuneko.rpclient.R

/** 群聊入口契约，避免创建页依赖群聊页面的实现包。 */
object GroupChatNavigation {
    const val EXTRA_SESSION_ID = "extra_group_chat_session_id"

    private const val ActivityClassSuffix = ".feature.groupchat.GroupChatActivity"

    fun createIntent(sessionId: Long): Intent {
        val applicationId = R::class.java.name.substringBeforeLast('.')
        return Intent()
            .setClassName(applicationId, applicationId + ActivityClassSuffix)
            .putExtra(EXTRA_SESSION_ID, sessionId.toString())
    }
}
