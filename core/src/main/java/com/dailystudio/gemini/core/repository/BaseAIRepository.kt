package com.dailystudio.gemini.core.repository

import android.content.Context
import com.dailystudio.gemini.core.utils.StatsUtils
import com.dailystudio.devbricksx.development.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class Status {
    RUNNING,
    DONE,
    ERROR
}

data class GenerationStream (
    val text: String? = null,
    val status: Status = Status.DONE,
    val errorMessage: String? = null
)

abstract class BaseAIRepository(
    protected val context: Context,
    private val dispatcher: CoroutineDispatcher
) {

    private val _ready: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    private val _generationStream = MutableSharedFlow<GenerationStream>(replay = 0, extraBufferCapacity = 64)
    val generationStream = _generationStream

    protected fun setReady(ready: Boolean) {
        _ready.value = ready
        Logger.debug("[MODEL ${this.javaClass.simpleName}] AI repository ready: $ready")
    }

    protected fun isReady(): Boolean {
        return _ready.value
    }

    suspend fun generate(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null
    ): String? {
        if (!isReady()) {
            Logger.warn("AI repository is NOT ready yet.")

            return null
        }

        return withContext(dispatcher) {
            StatsUtils.measureSuspend("[AI ${this@BaseAIRepository.javaClass.simpleName}]") {
                _generationStream.emit(
                    GenerationStream(
                        status = Status.RUNNING
                    )
                )

                try {
                    generateContent(prompt, fileUri, mimeType)
                } catch (e: Exception) {
                    Logger.error("[AI ${this@BaseAIRepository.javaClass.simpleName}] fail to generate: ${e.message}")
                    _generationStream.emit(
                        GenerationStream(
                            status = Status.ERROR,
                            errorMessage = e.message
                        )
                    )
                    null
                }
            }
        }
    }

    suspend fun generateAsync(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null
    ) {
        Logger.debug("prompt: $prompt")
        if (!isReady()) {
            Logger.warn("AI repository is NOT ready yet.")

            return
        }

        withContext(dispatcher) {
            val tag = "[AI ${this@BaseAIRepository.javaClass.simpleName}]"

//            StatsUtils.startMeasurement(tag)
            _generationStream.emit(
                GenerationStream(
                status = Status.RUNNING
            )
            )

//            _generationStream.map {
//                when (it.status) {
//                    Status.DONE, Status.ERROR -> {
//                        StatsUtils.endMeasurement(tag)
//                    }
//                    else -> {}
//                }
//            }

            try {
                generateContentStream(prompt, fileUri, mimeType)
            } catch (e: Exception) {
                Logger.error("[AI ${this@BaseAIRepository.javaClass.simpleName}] fail to generate: ${e.message}")
                _generationStream.emit(
                    GenerationStream(
                    status = Status.ERROR,
                    errorMessage = e.message
                )
                )
            }
        }
    }

    protected fun updateGenerationStream(
        text: String? = null,
        status: Status,
        errorMessage: String? = null
    ) {
        _generationStream.tryEmit(
            GenerationStream(
            text = text,
            status = status,
            errorMessage = errorMessage
        )
        )
    }

    fun checkAndPrepare() {
        if (isReady()) {
            return
        }

        prepare()
    }

    abstract fun prepare()

    protected abstract suspend fun generateContent(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null
    ): String?

    protected abstract suspend fun generateContentStream(
        prompt: String,
        fileUri: String? = null,
        mimeType: String? = null
    )

    open fun close() {
        Logger.debug("closing ai repository: ${this.javaClass.simpleName}")
        setReady(false)
    }

}