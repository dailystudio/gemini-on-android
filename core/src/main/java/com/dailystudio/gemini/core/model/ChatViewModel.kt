package com.dailystudio.gemini.core.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailystudio.gemini.core.repository.VertexAIRepository
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.repository.GeminiAIRepository
import com.dailystudio.gemini.core.repository.GeminiNanoRepository
import com.dailystudio.gemini.core.repository.GemmaAIRepository
import com.dailystudio.gemini.core.repository.GenerationStream
import com.dailystudio.gemini.core.repository.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    Idle,
    InProgress,
    Done,
    Error
}

data class UiState(
    val engine: AIEngine = ResumeViewModel.DEFAULT_AI_ENGINE,
    val fullResp: String = "",
    val text: String? = null,
    val file: String? = null,
    val mimeType: String? = null,
    val status: UiStatus = UiStatus.Idle,
    val countOfChar: Int = 0,
    val errorMessage: String? = null,
)

class ResumeViewModel(application: Application): AndroidViewModel(application) {

    companion object {
        val DEFAULT_AI_ENGINE = AIEngine.GEMINI_NANO

        const val JSON_SCHEMA_RESUME = """
            {
              "name": string,
              "gender": string,
              "age": string,
              "title": string,
              "education": [
                {
                  "start": string,
                  "end": string,
                  "school": string,
                  "degree": string
                }
              ],
              "workExperience": [
                {
                  "start": string,
                  "end": string,
                  "company": string,
                  "title": string,
                  "department": string,
                  "jobDescription": string
                }
              ],
              "projects": [
                  {
                  "name": string,
                  "start": string,
                  "end": string,
                  "brief": string,
                  "techs": [string]
                }
              ]
            }
        """
        const val JSON_SCHEMA_RESUME_NANO = """
            {
              "name": string,
              "gender": string,
              "age": string,
              "title": string,
              "education": [
                {
                  "start": string,
                  "end": string,
                  "school": string,
                  "degree": string
                }
              ],
              "workExperience": [
                {
                  "start": string,
                  "end": string,
                  "company": string,
                  "title": string,
                  "department": string,
                  "jobDescription": string
                }
              ]
            }
        """
    }


    private val repos = mapOf(
        AIEngine.GEMINI to GeminiAIRepository(application, Dispatchers.IO),
        AIEngine.GEMINI_NANO to GeminiNanoRepository(application, Dispatchers.IO),
        AIEngine.VERTEX to VertexAIRepository(application, Dispatchers.IO),
        AIEngine.GEMMA to GemmaAIRepository(application, Dispatchers.IO),
    )

    private val _results = repos.map { (engine, _) ->
        engine to MutableStateFlow<String?>(null)
    }.toMap()

    val results = _results.map { (engine, flow) ->
        engine to flow.asStateFlow()
    }.toMap()

    private val _resultsAsync =  repos.map { (engine, repository) ->
        engine to repository.generationStream
    }.toMap()

    val resultsAsync: Map<AIEngine, SharedFlow<GenerationStream>> = _resultsAsync

    val readyOfRepos = repos.map { (engine, repository) ->
        engine to repository.ready
    }.toMap()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repos.forEach { (engine, repo) ->
                launch {
                    repo.generationStream.collect { generationStream ->
                        val newState = when (generationStream.status) {
                            Status.DONE -> UiStatus.Done
                            Status.ERROR -> UiStatus.Error
                            else -> UiStatus.InProgress
                        }

                        val fullResp = _uiState.value.fullResp + (generationStream.text ?: "")
                        Logger.debug("[AI: ${engine}] [state: $newState], resp text = $fullResp, len = ${fullResp.length}")

                        _uiState.value = _uiState.value.copy(
                            engine = engine,
                            fullResp = fullResp,
                            status = newState,
                            countOfChar = fullResp.length,
                            errorMessage = generationStream.errorMessage
                        )
                    }
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState()
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

            _results[engine]?.value = result

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
        super.onCleared()

        viewModelScope.launch {
            closeRepos()
        }
    }

    suspend fun invalidateRepos(selectRepos: Array<AIEngine>? = null) {
        closeRepos(selectRepos)
    }

    suspend fun prepareRepos(selectRepos: Array<AIEngine>? = null) {
        withContext(Dispatchers.IO) {
            repos.forEach { (engine, repo) ->
                if (selectRepos == null || selectRepos.contains(engine)) {
                    Logger.debug("[MODEL: ${engine}] prepare repo")

                    repo.checkAndPrepare()
                }
            }
        }
    }

    suspend fun closeRepos(selectRepos: Array<AIEngine>? = null) {
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