package com.dailystudio.gemini.core.repository

import android.content.Context
import com.dailystudio.gemini.core.utils.StatsUtils
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.utils.ContentUtils
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class GemmaAIRepository(
    context: Context,
    dispatcher: CoroutineDispatcher,
): BaseAIRepository(context, dispatcher) {

    companion object {
        // NB: Make sure the filename is *unique* per model you use!
        // Weight caching is currently based on filename alone.
        private const val MODEL_PATH = "/data/local/tmp/llm/model.bin"
    }

    private val modelExists: Boolean
        get() = File(MODEL_PATH).exists()

    private var llmInference: LlmInference? = null

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
//                .setTemperature(AppSettingsPrefs.instance.temperature)
//                .setMaxTopK(AppSettingsPrefs.instance.topK)
                .setMaxTokens(9999)
                .setResultListener { partialResult, done ->
                    Logger.debug("new partial result: $done")

                    updateGenerationStream(
                        text = if (done) "" else partialResult,
                        status = if (done) Status.DONE else Status.RUNNING
                    )
                }
                .build()

            llmInference = LlmInference.createFromOptions(context, options)

            true
        }

        setReady(ready)
    }

    override suspend fun generateContent(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ): String? {
        return llmInference?.generateResponse(buildContent(
            prompt = prompt,
            fileUri = fileUri,
            mimeType = mimeType
        ))
    }

    override suspend fun generateContentStream(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ) {
        llmInference?.generateResponseAsync(buildContent(
            prompt = prompt,
            fileUri = fileUri,
            mimeType = mimeType
        ))
    }

    private suspend fun buildContent(prompt: String,
                                     fileUri: String?,
                                     mimeType: String?): String {
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

        return composedPrompt
    }

    override fun close() {
        super.close()

        /** TODO:
         *    This line will cause a crash.
         *    But I believe this line will cause memory leak.
         */
        llmInference?.close()
    }


}