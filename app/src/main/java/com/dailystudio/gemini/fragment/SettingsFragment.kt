package com.dailystudio.gemini.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import com.dailystudio.devbricksx.development.LT
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.gemini.R
import com.dailystudio.gemini.core.AppSettings
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.R as coreR
import com.dailystudio.gemini.core.model.AIEngine
import com.dailystudio.devbricksx.fragment.DevBricksFragment
import com.dailystudio.devbricksx.settings.AbsSetting
import com.dailystudio.devbricksx.settings.AbsSettingsFragment
import com.dailystudio.devbricksx.settings.RadioSetting
import com.dailystudio.devbricksx.settings.RadioSettingItem
import com.dailystudio.devbricksx.settings.SeekBarSetting
import com.dailystudio.devbricksx.settings.SimpleRadioSettingItem
import com.dailystudio.devbricksx.settings.SwitchSetting
import com.dailystudio.devbricksx.settings.SwitchSettingLayoutHolder
import com.dailystudio.devbricksx.utils.ResourcesCompatUtils
import com.dailystudio.devbricksx.utils.changeTitle
import com.dailystudio.devbricksx.utils.registerActionBar
import kotlin.math.roundToInt


class SettingsFragmentExt(): DevBricksFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_ext, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.registerActionBar(view, R.id.topAppBar)
        changeTitle(coreR.string.title_settings)
    }
}

data class ModelRadioSettingItem(private val context: Context,
                                 private val itemId: String,
                                 private val descResId: Int) : RadioSettingItem {

    override fun getId(): String {
        return itemId
    }

    override fun getLabel(): CharSequence {
        return context.getString(descResId)
    }

    fun getDescription(): CharSequence {
        return context.getString(descResId)
    }

}

class SettingsFragment: AbsSettingsFragment() {

    override fun createSettings(context: Context): Array<AbsSetting> {
        val engineItems = arrayOf(
            SimpleRadioSettingItem(context, AIEngine.GEMINI.toString(), coreR.string.label_engine_gemini),
            SimpleRadioSettingItem(context, AIEngine.VERTEX.toString(), coreR.string.label_engine_vertex),
            SimpleRadioSettingItem(context, AIEngine.GEMINI_NANO.toString(), coreR.string.label_engine_gemini_nano),
            SimpleRadioSettingItem(context, AIEngine.MEDIA_PIPE.toString(), coreR.string.label_media_pipe)
        )

        val currEngine = AppSettingsPrefs.instance.engine

        val geminiModels = arrayOf(
            ModelRadioSettingItem(context,
                "gemini-2.0-flash", coreR.string.label_model_gemini_2_0_flash),
            ModelRadioSettingItem(context,
                "gemini-2.0-flash-lite", coreR.string.label_model_gemini_2_0_flash_lite),
            ModelRadioSettingItem(context,
                "gemini-1.5-pro", coreR.string.label_model_gemini_1_5_pro),
            ModelRadioSettingItem(context,
                "gemini-1.5-flash", coreR.string.label_model_gemini_1_5_flash),
            ModelRadioSettingItem(context,
                "gemma-3-27b-it", coreR.string.label_model_gemma_3_27b)
        )

        val vertexModels = arrayOf(
            ModelRadioSettingItem(context,
                "gemini-2.0-flash", coreR.string.label_model_gemini_2_0_flash),
            ModelRadioSettingItem(context,
                "gemini-2.0-flash-lite", coreR.string.label_model_gemini_2_0_flash_lite),
            ModelRadioSettingItem(context,
                "gemini-1.5-pro", coreR.string.label_model_gemini_1_5_pro),
            ModelRadioSettingItem(context,
                "gemini-1.5-flash", coreR.string.label_model_gemini_1_5_flash),
        )

        val mediaPipeModels = arrayOf(
            ModelRadioSettingItem(context,
                "gemma-2-2b", coreR.string.label_model_gemma_2),
            ModelRadioSettingItem(context,
                "gemma-3-1b", coreR.string.label_model_gemma_3),
        )

        var geminiModelSettings: AbsSetting? = null
        var vertexModelSettings: AbsSetting? = null
        var mediaPipeModelSettings: AbsSetting? = null

        val engineSetting = object: RadioSetting<SimpleRadioSettingItem>(
            context,
            AppSettingsPrefs.PREF_ENGINE,
            coreR.drawable.ic_ai_engine,
            coreR.string.settings_ai_engine,
            engineItems) {
            override val selectedId: String?
                get() = AppSettingsPrefs.instance.engine

            override fun setSelected(selectedId: String?) {
                selectedId?.let { engine ->
                    AppSettingsPrefs.instance.engine = engine
                    geminiModelSettings?.enabled = geminiModelEnabled(engine)
                    vertexModelSettings?.enabled = vertexModelEnabled(engine)
                    mediaPipeModelSettings?.enabled = mediaPipeModelEnabled(engine)

                    val model = AppSettingsPrefs.instance.model
                    when (engine) {
                        AIEngine.GEMINI.toString() -> {
                            AppSettingsPrefs.instance.model =
                                checkOrReturnValidModel(model, geminiModels)
                            geminiModelSettings?.postInvalidate()
                        }

                        AIEngine.VERTEX.toString() -> {
                            AppSettingsPrefs.instance.model =
                                checkOrReturnValidModel(model, vertexModels)
                            vertexModelSettings?.postInvalidate()
                        }

                        AIEngine.MEDIA_PIPE.toString() -> {
                            AppSettingsPrefs.instance.model =
                                checkOrReturnValidModel(model, mediaPipeModels)
                            mediaPipeModelSettings?.postInvalidate()
                        }

                        else -> {}
                    }
                }
            }
        }


        geminiModelSettings = object: RadioSetting<ModelRadioSettingItem>(
            context,
            AppSettingsPrefs.PREF_MODEL,
            coreR.drawable.ic_model,
            coreR.string.settings_gemini_model,
            geminiModels,
            geminiModelEnabled(currEngine),
        ) {
            override val selectedId: String?
                get() = AppSettingsPrefs.instance.model

            override fun setSelected(selectedId: String?) {
                selectedId?.let {
                    AppSettingsPrefs.instance.model = it
                }
            }
        }

        vertexModelSettings = object: RadioSetting<ModelRadioSettingItem>(
            context,
            AppSettingsPrefs.PREF_MODEL,
            coreR.drawable.ic_model,
            coreR.string.settings_vertex_model,
            vertexModels,
            vertexModelEnabled(currEngine),
        ) {
            override val selectedId: String?
                get() = AppSettingsPrefs.instance.model

            override fun setSelected(selectedId: String?) {
                selectedId?.let {
                    AppSettingsPrefs.instance.model = it
                }
            }
        }

        mediaPipeModelSettings = object: RadioSetting<ModelRadioSettingItem>(
            context,
            AppSettingsPrefs.PREF_MODEL,
            coreR.drawable.ic_model,
            coreR.string.settings_media_pipe_model,
            mediaPipeModels,
            mediaPipeModelEnabled(currEngine),
        ) {
            override val selectedId: String?
                get() = AppSettingsPrefs.instance.model

            override fun setSelected(selectedId: String?) {
                selectedId?.let {
                    AppSettingsPrefs.instance.model = it
                }
            }
        }

        val asyncGenerationSetting = object: SwitchSetting(context,
            AppSettingsPrefs.PREF_ASYNC_GENERATION,
            coreR.drawable.ic_async,
            coreR.string.settings_async,
            -1,
            holder = object : SwitchSettingLayoutHolder() {

                override fun bindSetting(settingView: View, setting: AbsSetting) {
                    super.bindSetting(settingView, setting)

                    val context = requireContext()

                    if (setting !is SwitchSetting) {
                        return
                    }

                    val switch: SwitchCompat? = settingView.findViewById(
                        com.dailystudio.devbricksx.R.id.setting_switch)
                    switch?.trackTintList = ColorStateList.valueOf(
                        ResourcesCompatUtils.getColor(context,
                            coreR.color.primaryLightColor
                        )
                    )
                    switch?.background = ResourcesCompatUtils.getDrawable(
                        context, coreR.drawable.ripple_background)

                }
            }) {

            override fun isOn(): Boolean {
                return AppSettingsPrefs.instance.asyncGeneration
            }

            override fun setOn(on: Boolean) {
                AppSettingsPrefs.instance.asyncGeneration = on
            }
        }

        val temperatureSetting = object: SeekBarSetting(context,
            AppSettingsPrefs.PREF_TEMPERATURE,
            coreR.drawable.ic_temperature,
            coreR.string.settings_temperature) {

            override fun getProgress(context: Context): Float {
                return AppSettingsPrefs.instance.temperature
            }

            override fun setProgress(context: Context, progress: Float) {
                AppSettingsPrefs.instance.temperature = progress
            }

            override fun getMinValue(context: Context): Float {
                return AppSettings.MIN_TEMPERATURE
            }

            override fun getMaxValue(context: Context): Float {
                return AppSettings.MAX_TEMPERATURE
            }

            override fun getStep(context: Context): Float {
                return AppSettings.TEMPERATURE_STEP
            }
        }

        val topKSetting = object: SeekBarSetting(context,
            AppSettingsPrefs.PREF_TOP_K,
            coreR.drawable.ic_top_k,
            coreR.string.settings_topK) {

            override fun getProgress(context: Context): Float {
                return AppSettingsPrefs.instance.topK.toFloat()
            }

            override fun setProgress(context: Context, progress: Float) {
                AppSettingsPrefs.instance.topK = progress.roundToInt()
            }

            override fun getMinValue(context: Context): Float {
                return AppSettings.MIN_TOP_K.toFloat()
            }

            override fun getMaxValue(context: Context): Float {
                return AppSettings.MAX_TOP_K.toFloat()
            }

            override fun getStep(context: Context): Float {
                return AppSettings.TOP_K_STEP.toFloat()
            }
        }

        val topPSetting = object: SeekBarSetting(context,
            AppSettingsPrefs.PREF_TOP_P,
            coreR.drawable.ic_top_p,
            coreR.string.settings_topP) {

            override fun getProgress(context: Context): Float {
                return AppSettingsPrefs.instance.topP
            }

            override fun setProgress(context: Context, progress: Float) {
                AppSettingsPrefs.instance.topP = progress
            }

            override fun getMinValue(context: Context): Float {
                return AppSettings.MIN_TOP_P
            }

            override fun getMaxValue(context: Context): Float {
                return AppSettings.MAX_TOP_P
            }

            override fun getStep(context: Context): Float {
                return AppSettings.TOP_P_STEP
            }
        }

        val maxTokensSetting = object: SeekBarSetting(context,
            AppSettingsPrefs.PREF_MAX_TOKENS,
            coreR.drawable.ic_tokens,
            coreR.string.settings_max_tokens) {

            override fun getProgress(context: Context): Float {
                return AppSettingsPrefs.instance.maxTokens.toFloat()
            }

            override fun setProgress(context: Context, progress: Float) {
                AppSettingsPrefs.instance.maxTokens = progress.roundToInt()
            }

            override fun getMinValue(context: Context): Float {
                return AppSettings.MIN_MAX_TOKENS.toFloat()
            }

            override fun getMaxValue(context: Context): Float {
                return AppSettings.MAX_MAX_TOKENS.toFloat()
            }

            override fun getStep(context: Context): Float {
                return AppSettings.MAX_TOKENS_STEP.toFloat()
            }
        }


        val arrayOfSettings: MutableList<AbsSetting> = mutableListOf(
            engineSetting,
            geminiModelSettings,
            vertexModelSettings,
            mediaPipeModelSettings,
        )

        if (AppSettingsPrefs.instance.debugEnabled) {
            arrayOfSettings.add(asyncGenerationSetting)
            arrayOfSettings.add(temperatureSetting)
            arrayOfSettings.add(topKSetting)
            arrayOfSettings.add(topPSetting)
            arrayOfSettings.add(maxTokensSetting)
        }

        return arrayOfSettings.toTypedArray()
    }

    private fun geminiModelEnabled(engine: String): Boolean {
        return (engine == AIEngine.GEMINI.toString())
    }

    private fun vertexModelEnabled(engine: String): Boolean {
        return (engine == AIEngine.VERTEX.toString())
    }

    private fun mediaPipeModelEnabled(engine: String): Boolean {
        return (engine == AIEngine.MEDIA_PIPE.toString())
    }

    private fun checkOrReturnValidModel(model: String, modelSettings: Array<ModelRadioSettingItem>): String {
        val validModels = modelSettings.map {
            it.getId()
        }

        if (validModels.isEmpty()) {
            return ""
        }

        Logger.debug(LT("[CHECK]"), "$model, valid = ${validModels.joinToString()}")
        return if (model in validModels) {
            model
        } else {
            validModels[0]
        }.also {
            Logger.debug(LT("[CHECK]"), "return $it")

        }
    }

}