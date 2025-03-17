package com.dailystudio.gemini.core

import com.dailystudio.devbricksx.annotations.data.BooleanField
import com.dailystudio.devbricksx.annotations.data.DataStoreCompanion
import com.dailystudio.devbricksx.annotations.data.FloatField
import com.dailystudio.devbricksx.annotations.data.IntegerField
import com.dailystudio.devbricksx.annotations.data.StringField
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.core.model.AIEngine

@DataStoreCompanion
class AppSettings(
    @BooleanField(false)
    val debugEnabled: Boolean = false,
    @StringField("GEMINI")
    val engine: String = "GEMINI",
    @StringField("gemini-1.5-flash-001")
    val model: String = "gemini-1.5-flash-001",
    @BooleanField(true)
    val asyncGeneration: Boolean = true,
    @FloatField(DEFAULT_TEMPERATURE)
    val temperature: Float = DEFAULT_TEMPERATURE,
    @IntegerField(DEFAULT_TOP_K)
    val topK: Int = DEFAULT_TOP_K,
    @FloatField(DEFAULT_TOP_P)
    val topP: Float = DEFAULT_TOP_P,
    @IntegerField(DEFAULT_MAX_TOKENS)
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    companion object {

        const val DEFAULT_MAX_TOKENS = 1024
        const val MAX_MAX_TOKENS = 8192
        const val MIN_MAX_TOKENS = 512
        const val MAX_TOKENS_STEP = 512

        const val DEFAULT_TEMPERATURE = 0.15f
        const val MAX_TEMPERATURE = 2f
        const val MIN_TEMPERATURE = 0f
        const val TEMPERATURE_STEP = 0.05f

        const val DEFAULT_TOP_K = 32
        const val MAX_TOP_K = 50
        const val MIN_TOP_K = 5
        const val TOP_K_STEP = 1

        const val DEFAULT_TOP_P = 0.95f
        const val MAX_TOP_P = 1f
        const val MIN_TOP_P = 0f
        const val TOP_P_STEP = 0.05f

    }
}


fun AppSettingsPrefs.getAIEngine(): AIEngine {
    return try {
        AIEngine.valueOf(engine)
    } catch (e: Exception) {
        Logger.error("failed to parse AI engine from [$engine]: $e")

        AIEngine.GEMINI
    }
}