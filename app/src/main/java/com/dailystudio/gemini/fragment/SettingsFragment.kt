package com.dailystudio.gemini.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import com.dailystudio.gemini.R
import com.dailystudio.gemini.core.AppSettings
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.R as coreR
import com.dailystudio.gemini.core.model.AIEngine
import com.dailystudio.gemini.utils.changeTitle
import com.dailystudio.gemini.utils.registerActionBar
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
        return itemId
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
            SimpleRadioSettingItem(context, AIEngine.GEMMA.toString(), coreR.string.label_engine_gemma)
        )

        var modelSetting: AbsSetting? = null
        val engineSetting = object: RadioSetting<SimpleRadioSettingItem>(
            context,
            AppSettingsPrefs.PREF_ENGINE,
            coreR.drawable.ic_ai_engine,
            coreR.string.settings_ai_engine,
            engineItems) {
            override val selectedId: String?
                get() = AppSettingsPrefs.instance.engine

            override fun setSelected(selectedId: String?) {
                selectedId?.let {
                    AppSettingsPrefs.instance.engine = it
                    modelSetting?.enabled = modelEnabled()
                }
            }
        }

        val modelItems = arrayOf(
            ModelRadioSettingItem(context,
                "gemini-2.0-flash-exp", coreR.string.label_model_gemini_2_0_exp),
            ModelRadioSettingItem(context,
                "gemini-1.5-flash-002", coreR.string.label_model_gemini_1_5_flash_002),
            ModelRadioSettingItem(context,
                "gemini-1.5-flash-001", coreR.string.label_model_gemini_1_5_flash_001),
            ModelRadioSettingItem(context,
                "gemini-1.5-pro-002", coreR.string.label_model_gemini_1_5_pro_002),
            ModelRadioSettingItem(context,
                "gemini-1.5-pro-001", coreR.string.label_model_gemini_1_5_pro_001),
        )

        modelSetting = object: RadioSetting<ModelRadioSettingItem>(
            context,
            AppSettingsPrefs.PREF_MODEL,
            coreR.drawable.ic_model,
            coreR.string.settings_model,
            modelItems,
            modelEnabled(),
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

        val arrayOfSettings: MutableList<AbsSetting> = mutableListOf(
            engineSetting,
            modelSetting,
        )

        if (AppSettingsPrefs.instance.debugEnabled) {
            arrayOfSettings.add(asyncGenerationSetting)
            arrayOfSettings.add(temperatureSetting)
            arrayOfSettings.add(topKSetting)
        }

        return arrayOfSettings.toTypedArray()
    }

    private fun modelEnabled(): Boolean {
        val engine = AppSettingsPrefs.instance.engine

        return (engine == AIEngine.GEMINI.toString() || engine == AIEngine.VERTEX.toString())
    }
}