package com.dailystudio.gemini.utils

import android.animation.ObjectAnimator
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object UiHelper {

    fun scrollToTextBottom(scrollView: ScrollView?, textView: TextView?) {
        val tv = textView?: return
        val sv = scrollView?: return

        sv.post {
            val targetScrollY = tv.bottom - sv.height
            if (targetScrollY > 0) {
                ObjectAnimator.ofInt(sv, "scrollY", targetScrollY).apply {
                    duration = 200
                    start()
                }
            }
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