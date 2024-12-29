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
            AIEngine.GEMMA.toString() -> getString(coreR.string.label_engine_gemma)
            else -> null
        }

        infoView?.text = nameOfEngine?.let {
            getString(
                coreR.string.engine_info,
                nameOfEngine)
        } ?: ""
    }


}