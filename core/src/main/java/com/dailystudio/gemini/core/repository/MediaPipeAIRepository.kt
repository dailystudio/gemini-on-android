package com.dailystudio.gemini.core.repository

import android.content.Context
import com.dailystudio.gemini.core.utils.StatsUtils
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.AppSettings
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.Constants.LT_MODEL_GEMMA
import com.dailystudio.gemini.core.utils.ContentUtils
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class MediaPipeAIRepository(
    context: Context,
    dispatcher: CoroutineDispatcher,
): BaseAIRepository(context, dispatcher) {

    companion object {
        // NB: Make sure the filename is *unique* per model you use!
        // Weight caching is currently based on filename alone.
        private const val MODEL_PATH_GEMMA2 = "/data/local/tmp/llm/model.bin"
        private const val MODEL_PATH_GEMMA3 = "/data/local/tmp/llm/gemma3-1B-it-int4.task"
    }

    private val modelExists: Boolean
        get() = File(modelPath).exists()

    private val modelPath: String
        get() {
            return when (AppSettingsPrefs.instance.model) {
                "gemma-3-1b" -> MODEL_PATH_GEMMA3
                "gemma-2-2b" -> MODEL_PATH_GEMMA2
                else -> MODEL_PATH_GEMMA2
            }
        }

    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null

    override fun prepare() {
        Logger.debug(LT_MODEL_GEMMA, "model = ${AppSettingsPrefs.instance.model}")
        Logger.debug(LT_MODEL_GEMMA, "temperature = ${AppSettingsPrefs.instance.temperature}")
        Logger.debug(LT_MODEL_GEMMA, "topK = ${AppSettingsPrefs.instance.topK}")
        Logger.debug(LT_MODEL_GEMMA, "topP = ${AppSettingsPrefs.instance.topP}")
        Logger.debug(LT_MODEL_GEMMA, "maxTokens = ${AppSettingsPrefs.instance.maxTokens}")
        Logger.debug(LT_MODEL_GEMMA, "model path = $modelPath")

        val ready = if (!modelExists) {
            Logger.error("model not found at path: $modelPath")

            false
        } else {
            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath).apply {
                    when (AppSettingsPrefs.instance.model) {
                        "gemma-3-1b" -> {
                            setPreferredBackend(LlmInference.Backend.CPU)
                            setMaxTokens(AppSettingsPrefs.instance.maxTokens)
                        }
                        else -> setMaxTokens(1024)
                    }
                }
                .setMaxTopK(AppSettings.MAX_TOP_K)
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(AppSettingsPrefs.instance.topK)
                .setTopP(AppSettingsPrefs.instance.topP)
                .setTemperature(AppSettingsPrefs.instance.temperature)
                .build()

            llmSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)

            true
        }

        setReady(ready)
    }

    override suspend fun generateContent(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ): String? {
        buildContent(
            prompt = prompt,
            fileUri = fileUri,
            mimeType = mimeType
        )
        return llmSession?.generateResponse()
    }

    override suspend fun generateContentStream(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ) {
        Logger.debug(LT_MODEL_GEMMA, "streaming for prompt: $prompt")
        buildContent(
            prompt = prompt,
            fileUri = fileUri,
            mimeType = mimeType
        )
        llmSession?.generateResponseAsync() { partialResult, done ->
            updateGenerationStream(
                text = if (done) "" else partialResult,
                status = if (done) Status.DONE else Status.RUNNING
            )
        }
    }

    private suspend fun buildContent(prompt: String,
                                     fileUri: String?,
                                     mimeType: String?) {
        val extractedContent = if (fileUri != null && !mimeType.isNullOrBlank()) {
            if (mimeType.contains("pdf")) {
                StatsUtils.measure("PDF") {
                    ContentUtils.extractTextFromPdf(fileUri)
                }
            } else if (mimeType.contains("image/")) {
                StatsUtils.measureSuspend ("IMAGE") {
                    ContentUtils.extractTextFromImage(fileUri)
                }
            } else {
                ""
            }
        } else {
            ""
        } ?: ""

        val composedPrompt = buildString {
            append(extractedContent)
            append("\n $prompt")
        }

        llmSession?.addQueryChunk(composedPrompt)
    }

    override fun close() {
        super.close()

        llmSession?.close()
        llmInference?.close()
    }

}