package me.kafuuneko.rpclient.feature.about

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutActivityIntentTest {
    @Test
    fun repositoryIntentUsesActionViewAndConfiguredUri() {
        val url = "https://github.com/KafuuNeko/RPClient"

        val intent = repositoryIntent(url)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
    }
}
