package com.dailystudio.gemini.core.repository

import android.content.Context
import com.dailystudio.gemini.core.utils.StatsUtils
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.utils.ContentUtils
import com.google.ai.edge.aicore.Content
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.content
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.onCompletion

class GeminiNanoRepository(
    context: Context,
    dispatcher: CoroutineDispatcher
): BaseAIRepository(context, dispatcher) {

    private var model: GenerativeModel? = null

    override fun prepare() {
        Logger.debug("[MODEL Gemini Nano]: model = ${AppSettingsPrefs.instance.model}")
        Logger.debug("[MODEL Gemini Nano]: temperature = ${AppSettingsPrefs.instance.temperature}")
        Logger.debug("[MODEL Gemini Nano]: topK = ${AppSettingsPrefs.instance.topK}")

        model = GenerativeModel(
            generationConfig {
                this.context = this@GeminiNanoRepository.context
                temperature = AppSettingsPrefs.instance.temperature
                topK = AppSettingsPrefs.instance.topK
                maxOutputTokens = 8192
            }
        )

        setReady(true)
    }

    override suspend fun generateContent(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ): String? {
        return model?.generateContent(
            buildContent(prompt, fileUri, mimeType)
        )?.text
    }


    override suspend fun generateContentStream(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ) {
        var status = Status.RUNNING
        var errorMessage: String? = null

        model?.generateContentStream(
            buildContent(prompt, fileUri, mimeType)
        )?.onCompletion {
            status = if (it == null) {
                Status.DONE
            } else {
                Logger.error("generate stream failed: ${it.message}")

                errorMessage = it.message
                Status.ERROR
            }

            updateGenerationStream(
                "",
                status,
                errorMessage
            )
        }?.collect { response ->
            updateGenerationStream(
                response.text,
                status,
                errorMessage
            )
        }
    }

    private suspend fun buildContent(prompt: String,
                                     fileUri: String?,
                                     mimeType: String?): Content {
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

        return content {
            val composedPrompt = "$extractedContent\n $prompt"
//                Logger.debug("[AI] composed prompt: $composedPrompt")

            text(composedPrompt)
        }
    }

    override fun close() {
        super.close()

        model?.close()
    }
}