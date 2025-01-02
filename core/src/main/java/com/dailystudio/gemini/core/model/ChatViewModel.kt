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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
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


    private val repos = mapOf(
        AIEngine.GEMINI to GeminiAIRepository(application, Dispatchers.IO),
        AIEngine.GEMINI_NANO to GeminiNanoRepository(application, Dispatchers.IO),
        AIEngine.VERTEX to VertexAIRepository(application, Dispatchers.IO),
        AIEngine.GEMMA to GemmaAIRepository(application, Dispatchers.IO),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var engine: AIEngine = AppSettingsPrefs.instance.getAIEngine()

    init {
        viewModelScope.launch {
            prepareRepo(engine)
        }

        viewModelScope.launch {
            try {
                AppSettingsPrefs.instance.prefsChanges.collectLatest { it ->
                    Logger.debug("[MODEL] pref changed: ${it.prefKey}")
                    if (it.prefKey == AppSettingsPrefs.PREF_ENGINE) {
                        Logger.debug("[MODEL] engine changed: new = ${AppSettingsPrefs.instance.engine}")
                        val oldAIEngine = engine
                        closeRepo(oldAIEngine)

                        engine = AppSettingsPrefs.instance.getAIEngine()

                        prepareRepo(engine)
                    } else if (it.prefKey == AppSettingsPrefs.PREF_MODEL) {
                        Logger.debug("[MODEL] model changed: new = ${AppSettingsPrefs.instance.model}")
                        when (engine) {
                            AIEngine.GEMINI, AIEngine.VERTEX -> {
                                invalidateRepo(engine)
                            }

                            else -> {}
                        }
                    } else if (it.prefKey == AppSettingsPrefs.PREF_TEMPERATURE
                        || it.prefKey == AppSettingsPrefs.PREF_TOP_K
                    ) {
                        Logger.debug("[MODEL] temperature changed: new = ${AppSettingsPrefs.instance.temperature}")
                        Logger.debug("[MODEL] topK changed: new = ${AppSettingsPrefs.instance.topK}")

                        invalidateRepo(engine)
                    }
                }
            } catch (e: CancellationException) {
                Logger.debug("[MODEL] collect perchanges cancelled")
            }  finally {
                Logger.debug("[MODEL] scope is active: $isActive")
                Logger.debug("[MODEL] scope is cancelled.")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState()
    }

    fun clearRespText() {
        _uiState.value = _uiState.value.copy(
            text = "",
            fullResp = "",
        )
    }

    fun generate(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null,
        engine: AIEngine = DEFAULT_AI_ENGINE
    ) {
        viewModelScope.launch {
            val repo = repos[engine] ?: return@launch

            _uiState.value = _uiState.value.copy(
                status = UiStatus.InProgress,
                text = prompt,
                file = fileUri,
                mimeType = mimeType,
                fullResp = "")

            val result = repo.generate(prompt, fileUri, mimeType)
            Logger.debug("[AI: ${engine}] generate: result len = ${result?.length ?: 0}")

            _uiState.value = _uiState.value.copy(
                status = UiStatus.Done,
                text = prompt,
                file = fileUri,
                mimeType = mimeType,
                fullResp = result ?: "")
        }
    }

    fun generateAsync(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null,
        engine: AIEngine = DEFAULT_AI_ENGINE
    ) {
        viewModelScope.launch {
            val repo = repos[engine] ?: return@launch

            _uiState.value = _uiState.value.copy(
                status = UiStatus.InProgress,
                text = prompt,
                file = fileUri,
                mimeType = mimeType,
                fullResp = "")

            repo.generateAsync(prompt, fileUri, mimeType)
        }
    }

    override fun onCleared() {
        Logger.debug("[Model]: clear repo")
        super.onCleared()

        viewModelScope.launch {
            closeRepos()
        }
    }

    private suspend fun invalidateRepo(repo: AIEngine) {
        invalidateRepos(arrayOf(repo))
    }

    private suspend fun invalidateRepos(selectRepos: Array<AIEngine>? = null) {
        closeRepos(selectRepos)
    }

    private suspend fun prepareRepo(repo: AIEngine) {
        prepareRepos(arrayOf(repo))
    }

    private suspend fun prepareRepos(selectRepos: Array<AIEngine>? = null) {
        withContext(Dispatchers.IO) {
            repos.forEach { (engine, repo) ->
                if (selectRepos == null || selectRepos.contains(engine)) {
                    Logger.debug("[MODEL: ${engine}] prepare repo")

                    repo.checkAndPrepare()
                    bindRepo(repo)
                }
            }
        }
    }

    private fun bindRepo(repo: BaseAIRepository) {
        Logger.debug("[MODEL] bind repo: $repo")

        viewModelScope.launch {
            repo.ready.collect { ready ->
                Logger.debug("[MODEL] change ready: $ready")
                Logger.debug("[MODEL] current state: ${_uiState.value}")

                _uiState.value = _uiState.value.copy(
                    engine = engine,
                    status = if (ready) UiStatus.Idle else UiStatus.Preparing
                )

                Logger.debug("[MODEL] now state: ${_uiState.value}")

            }
        }

        viewModelScope.launch {
            repo.generationStream.collect { stream ->
                val newState = when (stream.status) {
                    Status.DONE -> UiStatus.Done
                    Status.ERROR -> UiStatus.Error
                    else -> UiStatus.InProgress
                }

                val fullResp = _uiState.value.fullResp + (stream.text ?: "")
                Logger.debug("[AI: ${engine}] [state: $newState], resp text = $fullResp, len = ${fullResp.length}")

                _uiState.value = _uiState.value.copy(
                    engine = engine,
                    fullResp = fullResp,
                    status = newState,
                    countOfChar = fullResp.length,
                    errorMessage = stream.errorMessage
                )
            }
        }
    }

    private suspend fun closeRepo(engine: AIEngine) {
        closeRepos(arrayOf(engine))
    }

    private suspend fun closeRepos(selectRepos: Array<AIEngine>? = null) {
        withContext(Dispatchers.IO) {
            repos.forEach { (engine, repo) ->
                if (selectRepos == null || selectRepos.contains(engine)) {
                    Logger.debug("[MODEL: ${engine}] close repo")

                    repo.close()
                }
            }
        }
    }

}