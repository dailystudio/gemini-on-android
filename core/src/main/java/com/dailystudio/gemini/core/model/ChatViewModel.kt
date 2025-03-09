package com.dailystudio.gemini.core.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailystudio.gemini.core.repository.VertexAIRepository
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.getAIEngine
import com.dailystudio.gemini.core.repository.BaseAIRepository
import com.dailystudio.gemini.core.repository.GeminiAIRepository
import com.dailystudio.gemini.core.repository.GeminiNanoRepository
import com.dailystudio.gemini.core.repository.GemmaAIRepository
import com.dailystudio.gemini.core.repository.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val fullResp: String = "",
    val text: String? = null,
    val file: String? = null,
    val mimeType: String? = null,
    val status: UiStatus = UiStatus.Preparing,
    val countOfChar: Int = 0,
    val errorMessage: String? = null,
)

class ChatViewModel(application: Application): AndroidViewModel(application) {

    companion object {
        val DEFAULT_AI_ENGINE = AIEngine.GEMINI_NANO
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var engine: MutableStateFlow<AIEngine> = MutableStateFlow(
        AppSettingsPrefs.instance.getAIEngine()
    )

    private var repo: BaseAIRepository? = null
    private var repoJob: Job? = null
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
                when (it.prefKey) {
                    AppSettingsPrefs.PREF_ENGINE -> {
                        Logger.debug("[MODEL] engine changed: new = ${AppSettingsPrefs.instance.engine}")
                        engine.value = AppSettingsPrefs.instance.getAIEngine()
                    }

                    AppSettingsPrefs.PREF_MODEL -> {
                        Logger.debug("[MODEL] model changed: new = ${AppSettingsPrefs.instance.model}")
                        when (engine.value) {
                            AIEngine.GEMINI, AIEngine.VERTEX -> {
                                invalidateRepo()
                            }

                            else -> {}
                        }
                    }

                    AppSettingsPrefs.PREF_TEMPERATURE, AppSettingsPrefs.PREF_TOP_K -> {
                        Logger.debug("[MODEL] temperature changed: new = ${AppSettingsPrefs.instance.temperature}")
                        Logger.debug("[MODEL] topK changed: new = ${AppSettingsPrefs.instance.topK}")

                        invalidateRepo()
                    }
                }
            }
        }
    }

    fun clearRespText() {
        _uiState.update { currentState ->
            currentState.copy(
                text = "",
                fullResp = "",
            )
        }
    }

    fun generate(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null,
        engine: AIEngine = DEFAULT_AI_ENGINE
    ) {
        val repo = repo ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    status = UiStatus.InProgress,
                    text = prompt,
                    file = fileUri,
                    mimeType = mimeType,
                    fullResp = ""
                )
            }

            val result = repo.generate(prompt, fileUri, mimeType)
            Logger.debug("[AI: ${engine}] generate: result len = ${result?.length ?: 0}")

            _uiState.update { currentState ->
                currentState.copy(
                    status = UiStatus.Done,
                    text = prompt,
                    file = fileUri,
                    mimeType = mimeType,
                    fullResp = result ?: ""
                )
            }
        }
    }

    fun generateAsync(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null,
        engine: AIEngine = DEFAULT_AI_ENGINE
    ) {
        val repo = repo ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    status = UiStatus.InProgress,
                    text = prompt,
                    file = fileUri,
                    mimeType = mimeType,
                    fullResp = ""
                )
            }

            repo.generateAsync(prompt, fileUri, mimeType)
        }
    }

    override fun onCleared() {
        Logger.debug("[Model]: clear repo")
        super.onCleared()

        closeRepo()
    }

    private fun prepareRepo() {
        Logger.debug("[MODEL: ${engine}] prepare repo in ${Thread.currentThread().name}")

        repo?.let {
            repoJob = createRepoJob(it)
            viewModelScope.launch(Dispatchers.IO) {
                it.checkAndPrepare()
            }
        }
    }

    private fun invalidateRepo() {
        closeRepo()
    }

    private fun closeRepo() {
        repo?.let {
            viewModelScope.launch (Dispatchers.IO) {
                Logger.debug("[MODEL: ${engine}] close repo in ${Thread.currentThread().name}")

                it.close()
            }
        }

        repoJob?.cancel()
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
                    val newState = when (stream.status) {
                        Status.DONE -> UiStatus.Done
                        Status.ERROR -> UiStatus.Error
                        else -> UiStatus.InProgress
                    }

                    val fullResp = _uiState.value.fullResp + (stream.text ?: "")
                    Logger.debug("[AI: ${engine}] [state: $newState], resp text = $fullResp, len = ${fullResp.length}")

                    _uiState.update { currentState ->
                        currentState.copy(
                            engine = engine.value,
                            fullResp = fullResp,
                            status = newState,
                            countOfChar = fullResp.length,
                            errorMessage = stream.errorMessage
                        )
                    }
                }
            }
        }
    }

}