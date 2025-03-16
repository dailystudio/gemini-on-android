package com.dailystudio.gemini.core.repository

import android.content.Context
import com.dailystudio.gemini.core.utils.StatsUtils
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.utils.ContentUtils
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class GemmaAIRepository(
    context: Context,
    dispatcher: CoroutineDispatcher,
): BaseAIRepository(context, dispatcher) {

    companion object {
        // NB: Make sure the filename is *unique* per model you use!
        // Weight caching is currently based on filename alone.
//        private const val MODEL_PATH = "/data/local/tmp/llm/model.bin"
        private const val MODEL_PATH = "/data/local/tmp/llm/model.task"
    }

    private val modelExists: Boolean
        get() = File(MODEL_PATH).exists()

    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null

    override fun prepare() {
        Logger.debug("[MODEL Gemma]: model = ${AppSettingsPrefs.instance.model}")
        Logger.debug("[MODEL Gemma]: temperature = ${AppSettingsPrefs.instance.temperature}")
        Logger.debug("[MODEL Gemma]: topK = ${AppSettingsPrefs.instance.topK}")

        val ready = if (!modelExists) {
            Logger.error("GEMMA2 model not found at path: $MODEL_PATH")

            false
        } else {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .setMaxTopK(32)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)

            val sessOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(AppSettingsPrefs.instance.topK)
                .setTemperature(AppSettingsPrefs.instance.temperature)
                .build()

            llmSession = LlmInferenceSession.createFromOptions(llmInference, sessOptions)

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
        Logger.debug("[MODEL]: streaming for prompt: $prompt")
        buildContent(
            prompt = prompt,
            fileUri = fileUri,
            mimeType = mimeType
        )
        llmSession?.generateResponseAsync() { partialResult, done ->
            Logger.debug("new partial result: $done")

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
        Logger.debug("[AI] composed prompt: $composedPrompt")

        llmSession?.addQueryChunk(composedPrompt)
    }

    override fun close() {
        super.close()

        /** TODO:
         *    This line will cause a crash.
         *    But I believe this line will cause memory leak.
         */

        llmSession?.close()
        llmInference?.close()
    }


}