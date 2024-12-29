package com.dailystudio.gemini.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dailystudio.gemini.R
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.devbricksx.fragment.AbsAboutFragment
import com.dailystudio.devbricksx.R as dsR
import com.dailystudio.gemini.core.R as coreR
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AboutFragment: AbsAboutFragment() {

    companion object {
        const val DEBUG_ENABLE_CLICKS = 5
        const val CROSS_FACE_ANIM_DURATION = 1000L
    }


    private var thumbView: ImageView? = null
    private var enableDebugClicks: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppSettingsPrefs.instance.prefsChanges.collectLatest {
                    if (it.prefKey == AppSettingsPrefs.PREF_DEBUG_ENABLED) {
                        syncThumb()
                    }
                }
            }
        }
    }

    override fun setupViewsOnDialog(dialog: Dialog?) {
        super.setupViewsOnDialog(dialog)
        syncThumb()
    }

    private fun syncThumb() {
        val debugEnabled = AppSettingsPrefs.instance.debugEnabled

        thumbView?.setImageResource(if (debugEnabled) {
            coreR.drawable.illustration_about_debug
        } else {
            coreR.drawable.illustration_about
        })
    }

    override fun setupCustomizedView(view: View?) {
        super.setupCustomizedView(view)

        thumbView = view?.findViewById(dsR.id.about_app_thumb)
        thumbView?.setOnClickListener {
            clickAndToggleDebug()
        }
    }

    private fun clickAndToggleDebug() {
        enableDebugClicks++
        if (enableDebugClicks >= DEBUG_ENABLE_CLICKS) {
            enableDebugClicks = 0
            AppSettingsPrefs.instance.debugEnabled = !AppSettingsPrefs.instance.debugEnabled
        }
    }

    private fun crossfade(fromView: View?,
                          toView: View?,
                          duration: Long,
                          startCallback: (() -> Unit)? = null,
                          endingCallback: (() -> Unit)? = null) {
        toView?.apply {
            alpha = 0f
            visibility = View.VISIBLE

            startCallback?.invoke()

            animate()
                .alpha(1f)
                .setDuration(duration)
                .setListener(null)
        }

        fromView?.animate()
            ?.alpha(0f)
            ?.setDuration(duration)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fromView.visibility = View.GONE
                    endingCallback?.invoke()
                }
            })
    }

    override val appThumbResource: Int
        get() = coreR.drawable.illustration_about

    override val appDescription: CharSequence
        get() = getString(coreR.string.app_desc)

    override val appIconResource: Int
        get() = coreR.mipmap.ic_launcher

    override val appName: CharSequence
        get() = getString(coreR.string.app_name)

    override val fragmentLayoutResource: Int
        get() = R.layout.fragment_about_customized
}