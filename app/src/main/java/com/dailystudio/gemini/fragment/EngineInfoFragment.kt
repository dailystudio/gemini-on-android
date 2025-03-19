package com.dailystudio.gemini.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dailystudio.gemini.R
import com.dailystudio.gemini.core.R as coreR
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.model.AIEngine
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.devbricksx.fragment.DevBricksFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EngineInfoFragment:DevBricksFragment() {

    private var infoView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppSettingsPrefs.instance.prefsChanges.collectLatest { pref ->
                    if (pref.prefKey == AppSettingsPrefs.PREF_ENGINE) {
                        val engine = AppSettingsPrefs.instance.engine

                        Logger.debug("engine changed: new = $engine")
                        infoView?.text = getString(
                            coreR.string.engine_info,
                            AppSettingsPrefs.instance.engine)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_engine_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        infoView = view.findViewById(R.id.info)
        updateAppInfo()
    }

    private fun updateAppInfo() {
        val engine = AppSettingsPrefs.instance.engine
        val model = AppSettingsPrefs.instance.model

        val nameOfEngine = when (engine) {
            AIEngine.GEMINI.toString() -> getString(coreR.string.label_engine_gemini)
            AIEngine.VERTEX.toString() -> getString(coreR.string.label_engine_vertex)
            AIEngine.GEMINI_NANO.toString() -> getString(coreR.string.label_engine_gemini_nano)
            AIEngine.MEDIA_PIPE.toString() -> getString(coreR.string.label_media_pipe)
            else -> null
        }

        val nameOfModel = when (engine) {
            AIEngine.GEMINI.toString(),
            AIEngine.VERTEX.toString(),
            AIEngine.MEDIA_PIPE.toString() -> {
                when (model) {
                    "gemini-2.0-flash" -> getString(coreR.string.label_model_gemini_2_0_flash)
                    "gemini-2.0-flash-lite" -> getString(coreR.string.label_model_gemini_2_0_flash_lite)
                    "gemini-1.5-pro" -> getString(coreR.string.label_model_gemini_2_0_flash)
                    "gemini-1.5-flash" -> getString(coreR.string.label_model_gemini_2_0_flash)
                    "gemma-2-2b" -> getString(coreR.string.label_model_gemma_2)
                    "gemma-3-1b" -> getString(coreR.string.label_model_gemma_3)
                    "gemma-3-27b-it" -> getString(coreR.string.label_model_gemma_3_27b
                    )
                    else -> null
                }
            }
            else -> null
        }

        infoView?.text = buildString {
            nameOfEngine?.let {
                append(getString(coreR.string.engine_info, it))
            }
            nameOfModel?.let {
                append(" (")
                append(it)
                append(")")
            }
        }
    }


}