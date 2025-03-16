package com.dailystudio.gemini.utils

import android.animation.ObjectAnimator
import android.widget.ScrollView
import android.widget.TextView
import com.dailystudio.devbricksx.development.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object UiHelper {

    private var scrollRunnable: Runnable? = null

    fun scrollToTextBottom(scrollView: ScrollView?, textView: TextView?, immediately: Boolean = false) {
        val tv = textView?: return
        val sv = scrollView?: return

        scrollRunnable?.let { sv.removeCallbacks(it) }

        if (immediately) {
            val targetScrollY = tv.bottom - sv.height
            Logger.debug("[STB]: tv.bottom(${tv.bottom}) - sv.height(${sv.height} = $targetScrollY")

            if (targetScrollY > 0) {
                sv.scrollY = targetScrollY
            }
        } else {
            sv.postDelayed({
                val targetScrollY = tv.bottom - sv.height
                Logger.debug("[STB]: tv.bottom(${tv.bottom}) - sv.height(${sv.height} = $targetScrollY")

                if (targetScrollY > 0) {
                    ObjectAnimator.ofInt(sv, "scrollY", targetScrollY).apply {
                        duration = 200
                        start()
                    }
                }
            }, 200)
        }
    }

}

class TimeStats(
    private val coroutineScope: CoroutineScope,
) {
    private var loadingJob: Job? = null
    private var loadingEnd: Boolean = false

    fun startStats(callback: (timeElapse: Long) -> Unit) {
        loadingEnd = false

        loadingJob = coroutineScope.launch {
            val start = System.currentTimeMillis()

            var now = start
            while (!loadingEnd) {
                now = System.currentTimeMillis()

                callback(now - start)
                delay(100)
            }
        }
    }

    fun stopStats() {
        loadingEnd = true

        loadingJob?.cancel()
        loadingJob = null
    }
}

class DotsLoader(
    private val coroutineScope: CoroutineScope,
    private val numOfDots: Int = 3
) {
    private var loadingJob: Job? = null
    private var loadingEnd: Boolean = false

    fun startLoadingDots(callback: (text: String) -> Unit) {
        loadingEnd = false

        loadingJob = coroutineScope.launch {
            var dotState = 0
            while (!loadingEnd) {
                val text = ".".repeat(dotState + 1)

                callback(text)

                dotState = (dotState + 1) % numOfDots
                delay(500) // 控制动画更新速度
            }
        }
    }

    fun stopLoadingDots() {
        loadingEnd = true

        loadingJob?.cancel()
        loadingJob = null
    }
}