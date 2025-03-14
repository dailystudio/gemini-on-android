package com.dailystudio.gemini.core.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailystudio.gemini.core.repository.VertexAIRepository
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.devbricksx.utils.ResourcesCompatUtils
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.R
import com.dailystudio.gemini.core.getAIEngine
import com.dailystudio.gemini.core.repository.BaseAIRepository
import com.dailystudio.gemini.core.repository.GeminiAIRepository
import com.dailystudio.gemini.core.repository.GeminiNanoRepository
import com.dailystudio.gemini.core.repository.GemmaAIRepository
import com.dailystudio.gemini.core.repository.Status
import com.dailystudio.gemini.core.utils.DotsLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

enum class AIEngine: Serializable {
    GEMINI,
    GEMINI_NANO,
    VERTEX,
    GEMMA
}

enum class UiStatus {
    Preparing,
    Idle,
    InProgress,
    Done,
    Error
}

data class UiState(
    val engine: AIEngine = ChatViewModel.DEFAULT_AI_ENGINE,
    val text: String? = null,
    val file: String? = null,
    val mimeType: String? = null,
    val fullResp: String = "",
    val oldResponses: String = "",
    val status: UiStatus = UiStatus.Preparing,
    val countOfChar: Int = 0,
    val errorMessage: String? = null,
) {
    val conversation: String = when (status) {
        UiStatus.Done, UiStatus.Error -> {
            oldResponses
        }
        else -> {
            buildString {
                append(oldResponses)
                append(fullResp)
            }
        }
    }

    fun appendResponse(text: String?): UiState {
        Logger.debug("[SAVE RESP] text = [$text]")
        return copy(
            fullResp = fullResp + (text ?: ""),
            countOfChar = fullResp.length,
        )
    }

    fun commitResponses(): UiState {
        val commitText = this.fullResp
        Logger.debug("[COMMIT] text = [$commitText]")
        return copy(
            oldResponses = buildString {
                append(oldResponses)
                append(commitText)
            },
            fullResp = ""
        )
    }

}

class ChatViewModel(application: Application): AndroidViewModel(application) {

    companion object {
        val DEFAULT_AI_ENGINE = AIEngine.GEMINI_NANO

        fun colorIntToHex(color: Int): String {
            return String.format("#%06X", 0xFFFFFF and color)
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var engine: MutableStateFlow<AIEngine> = MutableStateFlow(
        AppSettingsPrefs.instance.getAIEngine()
    )

    private var repo: BaseAIRepository? = null
    private var repoJob: Job? = null

    private var settingChanges: MutableSet<String> = mutableSetOf()
    private var _settingsChanged: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val settingsChanged: StateFlow<Boolean> = _settingsChanged.asStateFlow()

    private val colorHuman: String =
        colorIntToHex(ResourcesCompatUtils.getColor(application, R.color.human))
    private val colorAI: String =
        colorIntToHex(ResourcesCompatUtils.getColor(application, R.color.ai))
    private val colorError: String =
        colorIntToHex(ResourcesCompatUtils.getColor(application, R.color.error))

    private val roleSelf: String =
        application.getString(R.string.label_myself)

    private val dotsLoader = DotsLoader(viewModelScope)

    init {
        viewModelScope.launch {
            engine.collectLatest { engine ->
                Logger.debug("[MODEL]: engine changed to: $engine")
                closeRepo()

                repo = when (engine) {
                    AIEngine.GEMINI -> GeminiAIRepository(application, Dispatchers.IO)
                    AIEngine.GEMINI_NANO -> GeminiNanoRepository(application, Dispatchers.IO)
                    AIEngine.VERTEX -> VertexAIRepository(application, Dispatchers.IO)
                    AIEngine.GEMMA -> GemmaAIRepository(application, Dispatchers.IO)
                }

                prepareRepo()
            }
        }

        viewModelScope.launch {
            AppSettingsPrefs.instance.prefsChanges.collectLatest { it ->
                Logger.debug("[MODEL]: marking change key: ${it.prefKey}")
                markSettingChange(it.prefKey)
            }
        }
    }

    private fun markSettingChange(key: String) {
        settingChanges.add(key)
        _settingsChanged.update { true }
    }

    fun commitChanges() {
        var engineChanged = false
        var repoInvalidated = false

        for (key in settingChanges) {
            when (key) {
                AppSettingsPrefs.PREF_ENGINE -> {
                    Logger.debug("[MODEL] engine changed: new = ${AppSettingsPrefs.instance.engine}")
                    engineChanged = true
                }

                AppSettingsPrefs.PREF_MODEL -> {
                    Logger.debug("[MODEL] model changed: new = ${AppSettingsPrefs.instance.model}")
                    when (engine.value) {
                        AIEngine.GEMINI, AIEngine.VERTEX -> {
                            repoInvalidated = true
                        }

                        else -> {}
                    }
                }

                AppSettingsPrefs.PREF_TEMPERATURE, AppSettingsPrefs.PREF_TOP_K -> {
                    Logger.debug("[MODEL] temperature changed: new = ${AppSettingsPrefs.instance.temperature}")
                    Logger.debug("[MODEL] topK changed: new = ${AppSettingsPrefs.instance.topK}")

                    repoInvalidated = true
                }
            }
        }

        Logger.debug("[MODEL] commit change: engineChanged = $engineChanged, repoInvalidated = $repoInvalidated")

        if (engineChanged) {
            engine.value = AppSettingsPrefs.instance.getAIEngine()
        } else if (repoInvalidated) {
            viewModelScope.launch {
                invalidateRepo()
            }
        }

        settingChanges.clear()
        _settingsChanged.update { false }
    }

    private fun startGeneration(prompt: String,
                                fileUri: String? = null,
                                mimeType: String? = null) {
        _uiState.update {
            it.copy(
                status = UiStatus.InProgress,
                text = prompt,
                file = fileUri,
                mimeType = mimeType,
            ).appendResponse(buildString {
                append("<font color='${colorHuman}'><b>${roleSelf}:</b> ")
                append("$prompt</font>")
                append("\n")
                append("<font color='${colorAI}'><b>${engine.value}:</b> ")
                append("\n")
            }).commitResponses()
        }

        dotsLoader.startLoadingDots { dots ->
            _uiState.update { currentState ->
                currentState.appendResponse(dots)
            }
        }
    }

    private fun stopGeneration() {
        _uiState.update {
            it.appendResponse(buildString {
                append("</font>")
                append("\n")
                append("\n")
            }).commitResponses()
        }
    }

    fun generate(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null,
    ) {
        val repo = repo ?: return

        viewModelScope.launch {
            startGeneration(prompt, fileUri, mimeType)

            val result = repo.generate(prompt, fileUri, mimeType)
            Logger.debug("[AI: ${engine}] generate: result len = ${result?.length ?: 0}")

            _uiState.update {
                it.copy(
                    status = UiStatus.Done,
                    text = prompt,
                    file = fileUri,
                    mimeType = mimeType,
                ).appendResponse(result)
            }

            stopGeneration()
        }
    }

    fun generateAsync(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null,
    ) {
        val repo = repo ?: return

        viewModelScope.launch {
            startGeneration(prompt, fileUri, mimeType)

            repo.generateAsync(prompt, fileUri, mimeType)
        }
    }

    override fun onCleared() {
        Logger.debug("[Model]: clear repo")
        super.onCleared()

        viewModelScope.launch {
            closeRepo()
        }
    }

    fun setEngine(newEngine: AIEngine) {
        engine.update { newEngine }
    }

    suspend fun invalidateRepo() {
        closeRepo()
        prepareRepo()
    }

    private suspend fun prepareRepo() = withContext(Dispatchers.IO) {
        Logger.debug("[MODEL: ${engine}] prepare repo in ${Thread.currentThread().name}")

        repo?.let {
            repoJob = createRepoJob(it)
            it.checkAndPrepare()
        }
    }

    private suspend fun closeRepo() = withContext(Dispatchers.IO) {
        Logger.debug("[MODEL: ${engine}] close repo in ${Thread.currentThread().name}")

        repo?.close()
        repoJob?.cancelAndJoin()
    }

    private fun createRepoJob(repo: BaseAIRepository): Job? {
        Logger.debug("[MODEL] create job for repo: $repo")

        return repo.let {
            viewModelScope.launch {
                it.ready.collect { ready ->
                    Logger.debug("[MODEL] change ready: $ready")
                    Logger.debug("[MODEL] current state: ${_uiState.value}")

                    _uiState.update { currentState ->
                        currentState.copy(
                            engine = engine.value,
                            status = if (ready) UiStatus.Idle else UiStatus.Preparing
                        )
                    }

                    Logger.debug("[MODEL] now state: ${_uiState.value}")
                }
            }

            viewModelScope.launch {
                it.generationStream.collect { stream ->
                    val status = when (stream.status) {
                        Status.DONE -> UiStatus.Done
                        Status.ERROR -> UiStatus.Error
                        else -> UiStatus.InProgress
                    }

                    if (dotsLoader.isRunning()) {
                        if (!stream.text.isNullOrEmpty()
                                || status == UiStatus.Error
                                || status == UiStatus.Done) {
                            dotsLoader.stopLoadingDots()
                            _uiState.update { currentState ->
                                currentState.copy(
                                    fullResp = "",
                                )
                            }
                        }
                    }

                    val newResp = buildString {
                        append(stream.text ?: "")

                        if (status == UiStatus.Error) {
                            Logger.debug("error: ${stream.errorMessage}")
                            append("<font color='${colorError}'>")
                            append(stream.errorMessage?.trim())
                            append("</font>")
                        }
                    }

                    _uiState.update { currentState ->
                        currentState.copy(
                            engine = engine.value,
                            status = status,
                            errorMessage = stream.errorMessage
                        ).appendResponse(
                            newResp
                        )
                    }

                    if (status == UiStatus.Done || status == UiStatus.Error) {
                        stopGeneration()
                    }
                }
            }
        }
    }

}