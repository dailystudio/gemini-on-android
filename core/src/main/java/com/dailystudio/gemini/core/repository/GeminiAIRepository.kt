package com.dailystudio.gemini.core.repository

import android.content.Context
import android.net.Uri
import com.dailystudio.gemini.core.BuildConfig
import com.dailystudio.gemini.core.utils.ContentUtils
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.onCompletion
import java.io.InputStream

class GeminiAIRepository(
    context: Context,
    dispatcher: CoroutineDispatcher
): BaseAIRepository(context, dispatcher) {

    private lateinit var model: GenerativeModel

    override fun prepare() {
        Logger.debug("[MODEL Gemini]: model = ${AppSettingsPrefs.instance.model}")
        Logger.debug("[MODEL Gemini]: temperature = ${AppSettingsPrefs.instance.temperature}")
        Logger.debug("[MODEL Gemini]: topK = ${AppSettingsPrefs.instance.topK}")

        model = GenerativeModel(
            modelName = AppSettingsPrefs.instance.model,
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = AppSettingsPrefs.instance.temperature
                topK = AppSettingsPrefs.instance.topK
                topP = 1f
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
        return model.generateContent(
            buildContent(prompt, fileUri, mimeType)
        ).text
    }

    override suspend fun generateContentStream(
        prompt: String,
        fileUri: String?,
        mimeType: String?
    ) {
        Logger.debug("prompt: $prompt")

        var status = Status.RUNNING
        var errorMessage: String? = null

        model.generateContentStream(
            buildContent(prompt, fileUri, mimeType)
        ).onCompletion {
            status = if (it == null) {
                Status.DONE
            } else {
                Logger.error("generate stream failed: ${it.message}")

                errorMessage = it.message
                Status.ERROR
            }
            Logger.debug("[AI GEMINI]: status = $status, text = ")

            updateGenerationStream(
                "",
                status,
                errorMessage
            )
        }.collect { response ->
            Logger.debug("[AI GEMINI]: status = $status, text = ${response.text}")
            updateGenerationStream(
                response.text,
                status,
                errorMessage
            )
        }

    }

    private fun buildContent(prompt: String,
                             fileUri: String?,
                             mimeType: String?): Content {
        return content {
            if (fileUri != null && !mimeType.isNullOrBlank()) {
                if (mimeType.contains("image")) {
                    val bitmap = ContentUtils.uriToBitmap(
                        context, Uri.parse(fileUri))
                    bitmap?.let {
                        image(it)
                    }
                } else {
                    var stream: InputStream? = null
                    try {
                        stream =
                            context.contentResolver.openInputStream(
                                Uri.parse(fileUri)
                            )

                        stream?.let {
                            blob(mimeType, stream.readBytes())
                        }

                    } catch (e: Exception) {
                        Logger.error("generate failed: ${e.message}")

                    } finally {
                        stream?.close()
                    }
                }
            }
            text(prompt)
        }
    }

}