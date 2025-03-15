package com.dailystudio.gemini.core.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class DotsLoader(
    private val coroutineScope: CoroutineScope,
    private val numOfDots: Int = 3
) {
    private var loadingJob: Job? = null
    private var loadingEnd: Boolean = false

    fun isRunning(): Boolean {
        return loadingJob?.isActive == true
    }

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