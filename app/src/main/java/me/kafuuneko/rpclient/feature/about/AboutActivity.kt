package me.kafuuneko.rpclient.feature.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.feature.about.ui.AboutLayout
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** 关于页面宿主，负责提供版本和项目联系信息。 */
class AboutActivity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        val uiState = remember {
            AboutUiState(
                appVersionName = packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName
                    ?: getString(R.string.unknown_version),
                githubRepoUrl = AppModel.GITHUB_REPO,
                githubRepoName = "KafuuNeko/RPClient",
                developerEmail = AppModel.EMAIL
            )
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            AboutLayout(
                uiState = uiState,
                onBack = { finish() },
                onCopyDeveloperEmail = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            getString(R.string.developer_contact),
                            uiState.developerEmail
                        )
                    )
                    Toast.makeText(
                        this@AboutActivity,
                        R.string.copied_to_clipboard,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onOpenRepository = {
                    startActivity(repositoryIntent(uiState.githubRepoUrl))
                }
            )
        }
    }
}

/** 构造只包含公开仓库 URI 的外部浏览 Intent。 */
internal fun repositoryIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, url.toUri())
