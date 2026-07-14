package me.kafuuneko.rpclient.feature.llmprovideredit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderCredentialResolver
import me.kafuuneko.rpclient.feature.llmprovideredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.llmprovideredit.model.toEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditDialogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 模型供应商编辑页状态持有者，负责表单校验、连接测试与配置持久化。 */
class LLMProviderEditViewModel :
    CoreViewModelWithEvent<LLMProviderEditUiIntent, LLMProviderEditUiState>(
        LLMProviderEditUiState.None
    ), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLLMClientFactory by inject<LLMClientFactory>()
    /** 当前连接测试任务；重复测试或离开页面时用于取消旧请求。 */
    private var mTestJob: Job? = null
    private var mApiKeyReplacement: String? = null
    private var mCustomHeadersReplacement: String? = null
    private var mInitialApiKey = ""
    private var mInitialCustomHeaders = ""

    @UiIntentObserver(LLMProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: LLMProviderEditUiIntent.Init) {
        if (!isStateOf<LLMProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mLLMRepository.getProviderById(it) }
        mInitialApiKey = provider?.apiKey.orEmpty()
        mInitialCustomHeaders = provider?.customHeadersJson.orEmpty()
        LLMProviderEditUiState.Normal(
            mode = if (provider == null) LLMProviderEditMode.Create else LLMProviderEditMode.Edit,
            form = provider?.toEditForm() ?: LLMProviderEditForm()
        ).setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.loadState is LLMProviderEditLoadState.Saving) return
        cancelTest()
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(
                testState = LLMProviderEditTestState.None,
                dialogState = LLMProviderEditDialogState.UnsavedChangesConfirm
            ).setup()
            return
        }
        finishPage()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: LLMProviderEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProviderType::class)
    private fun onChangeProviderType(intent: LLMProviderEditUiIntent.ChangeProviderType) =
        updateForm { copy(providerType = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProtocol::class)
    private fun onChangeProtocol(intent: LLMProviderEditUiIntent.ChangeProtocol) =
        updateForm {
            val capabilities = LLMProviderCapabilities.forProtocol(intent.value)
            copy(
                protocol = intent.value,
                sendTemperature = capabilities.defaultSendTemperature,
                sendTopP = capabilities.defaultSendTopP
            )
        }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: LLMProviderEditUiIntent.ChangeBaseUrl) =
        updateForm { copy(baseUrl = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ShowApiKeyEditor::class)
    private fun onShowApiKeyEditor() = showDialog(LLMProviderEditDialogState.ApiKeyEditor)

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmApiKeyReplacement::class)
    private fun onConfirmApiKeyReplacement(
        intent: LLMProviderEditUiIntent.ConfirmApiKeyReplacement
    ) {
        if (intent.value.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.api_key_required).tryEmit()
            return
        }
        mApiKeyReplacement = intent.value
        updateForm { copy(apiKeyEditMode = CredentialEditMode.Replace) }
        closeDialog()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ClearApiKey::class)
    private fun onClearApiKey() {
        mApiKeyReplacement = null
        updateForm { copy(apiKeyEditMode = CredentialEditMode.Clear) }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.KeepExistingApiKey::class)
    private fun onKeepExistingApiKey() {
        mApiKeyReplacement = null
        updateForm { copy(apiKeyEditMode = CredentialEditMode.KeepExisting) }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: LLMProviderEditUiIntent.ChangeModel) =
        updateForm { copy(model = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ShowCustomHeadersEditor::class)
    private fun onShowCustomHeadersEditor() =
        showDialog(LLMProviderEditDialogState.CustomHeadersEditor)

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement::class)
    private fun onConfirmCustomHeadersReplacement(
        intent: LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement
    ) {
        val isObject = runCatching {
            JsonParser.parseString(intent.value).isJsonObject
        }.getOrDefault(false)
        if (!isObject) {
            AppViewEvent.PopupToastMessageByResId(R.string.custom_headers_json_invalid).tryEmit()
            return
        }
        mCustomHeadersReplacement = intent.value
        updateForm { copy(customHeadersEditMode = CredentialEditMode.Replace) }
        closeDialog()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ClearCustomHeaders::class)
    private fun onClearCustomHeaders() {
        mCustomHeadersReplacement = null
        updateForm { copy(customHeadersEditMode = CredentialEditMode.Clear) }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.KeepExistingCustomHeaders::class)
    private fun onKeepExistingCustomHeaders() {
        mCustomHeadersReplacement = null
        updateForm { copy(customHeadersEditMode = CredentialEditMode.KeepExisting) }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTemperature::class)
    private fun onChangeTemperature(intent: LLMProviderEditUiIntent.ChangeTemperature) =
        updateForm { copy(temperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTopP::class)
    private fun onChangeTopP(intent: LLMProviderEditUiIntent.ChangeTopP) =
        updateForm { copy(topP = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeMaxTokens::class)
    private fun onChangeMaxTokens(intent: LLMProviderEditUiIntent.ChangeMaxTokens) =
        updateForm { copy(maxTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeContextTokens::class)
    private fun onChangeContextTokens(intent: LLMProviderEditUiIntent.ChangeContextTokens) =
        updateForm { copy(contextTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTemperature::class)
    private fun onToggleSendTemperature(intent: LLMProviderEditUiIntent.ToggleSendTemperature) =
        updateForm { copy(sendTemperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTopP::class)
    private fun onToggleSendTopP(intent: LLMProviderEditUiIntent.ToggleSendTopP) =
        updateForm { copy(sendTopP = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SelectPostProcessingMode::class)
    private fun onSelectPostProcessingMode(
        intent: LLMProviderEditUiIntent.SelectPostProcessingMode
    ) = updateForm { copy(promptPostProcessingMode = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleEnabled::class)
    private fun onToggleEnabled(intent: LLMProviderEditUiIntent.ToggleEnabled) =
        updateForm { copy(isEnabled = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        cancelTest()
        uiState.copy(loadState = LLMProviderEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) { mLLMRepository.saveProvider(provider) }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == LLMProviderEditMode.Create) R.string.model_created else R.string.model_saved
        ).tryEmit()
        finishPage()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.TestClick::class)
    private fun onTestClick() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        if (mTestJob?.isActive == true) return
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        uiState.copy(testState = LLMProviderEditTestState.Testing).setup()
        mTestJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val response = withContext(Dispatchers.IO) {
                    mLLMClientFactory.create(provider.toConfig()).generate(
                        "Please reply with a short English sentence: Model test successful."
                    )
                }
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    testState = LLMProviderEditTestState.Success(
                        response.content.ifBlank { "Model test successful" }
                    )
                ).setup()
            } catch (_: CancellationException) {
                // Cancellation is an expected user action and should not be shown as a failure.
            } catch (_: Throwable) {
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    testState = LLMProviderEditTestState.Failed
                ).setup()
            } finally {
                if (mTestJob === runningJob) mTestJob = null
            }
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.CancelTest::class)
    private fun onCancelTest() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        cancelTest()
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.testState is LLMProviderEditTestState.Testing) {
            uiState.copy(testState = LLMProviderEditTestState.None).setup()
        }
    }

    private fun cancelTest() {
        mTestJob?.cancel()
        mTestJob = null
    }

    override fun onCleared() {
        cancelTest()
        clearSensitiveDrafts()
        super.onCleared()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        cancelTest()
        finishPage()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        closeDialog()
    }

    /**
     * 统一更新表单字段，并清理测试结果。
     */
    private fun updateForm(block: LLMProviderEditForm.() -> LLMProviderEditForm) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        cancelTest()
        uiState.copy(
            form = uiState.form.block(),
            testState = LLMProviderEditTestState.None
        ).setup()
    }

    /**
     * 校验表单并转换为数据库实体，失败时给出对应提示。
     */
    private fun LLMProviderEditForm.toProviderOrNullWithToast(): LLMProvider? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_name_empty).tryEmit()
            return null
        }
        if (baseUrl.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.base_url_empty).tryEmit()
            return null
        }
        if (model.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_name_required).tryEmit()
            return null
        }
        val credentials = LLMProviderCredentialResolver.resolve(
            form = this,
            initialApiKey = mInitialApiKey,
            initialCustomHeaders = mInitialCustomHeaders,
            apiKeyReplacement = mApiKeyReplacement,
            customHeadersReplacement = mCustomHeadersReplacement
        ) ?: return null
        val provider = toProviderOrNull(
            apiKey = credentials.apiKey,
            customHeadersJson = credentials.customHeadersJson
        )
        if (provider == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
        }
        return provider
    }

    private fun showDialog(dialogState: LLMProviderEditDialogState) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = dialogState).setup()
    }

    private fun closeDialog() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = LLMProviderEditDialogState.None).setup()
    }

    private fun finishPage() {
        clearSensitiveDrafts()
        LLMProviderEditUiState.finished(uiStateFlow.value).setup()
    }

    private fun clearSensitiveDrafts() {
        mApiKeyReplacement = null
        mCustomHeadersReplacement = null
        mInitialApiKey = ""
        mInitialCustomHeaders = ""
    }

}
